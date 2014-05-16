package jsafran.parsing.unsup.hillclimb;

import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import utils.ml.edmonds.Edmonds;

import jsafran.Dep;
import jsafran.DetGraph;
import jsafran.GraphIO;
import jsafran.JSafran;
import jsafran.parsing.unsup.rules.PatternRules;
import jsafran.parsing.unsup.rules.RulesEditor.Combiner;

/**
 * - Precompute all deps that can be generated by the rules: this is the dep graph
 * - Use rules priors to compute dep scores
 * - Add a small random on the dep scores to differentiate multiple best trees
 * - compute the MST on the dep graph = random sampling of one of the best trees
 * - g.conf contains the best score so far
 * 
 * IMPORTANT: we assume here that the rules do not depend on each other, because MST must be given a fixed array of possible links
 * To overcome this limitation, we want to put two steps in cascade
 * 
 * @author xtof
 *
 */
public class MSTSearch implements UttSearch {

	// when set to true, the scorer is not used, and the output is always a random sample amongst the best MST trees
	// this is our strong baseline
	static boolean randomSearch = true;
	public static JSafran treesframe = null;

	PatternRules rules = new PatternRules();
	Random rand = new Random();
	
	@Override
	public float search(DetGraph g, Scorer sc) {
		// TODO: include the scorer
		ArrayList<Dep> bestdeps = new ArrayList<Dep>();
		bestdeps.addAll(g.deps);
		g.clearDeps();
		int[] idx = rules.getApplicable(g);
		if (idx==null||idx.length==0) {
			g.clearDeps();
			g.deps.addAll(bestdeps);
		} else {
			final int nw=g.getNbMots();
			float[][] arcs = new float[nw][nw];
			byte[][] deptyp = new byte[nw][nw];
			
			{
				// arcs ne contient que des 0 a la creation
				// mais on veut autoriser tous les arcs possibles, afin d'eviter le problemes des sous-arbres disjoints
				for (int i=0;i<arcs.length;i++)
					for (int j=0;j<arcs.length;j++)
						arcs[i][j]=0.0001f;
			}
					
			
			for (int i=0;i<idx.length;i++) {
				g.clearDeps();
				rules.apply(g, i);
				float prob = rules.getScore(i);
				for (Dep d : g.deps) {
					int gov=d.gov.getIndexInUtt()-1;
					int head=d.head.getIndexInUtt()-1;
					if (prob>arcs[head][gov]) {
						arcs[head][gov]=prob; deptyp[head][gov]=(byte)d.type;
					}
				}
			}
			// add a small random to produce a random sampling amongst all best MST trees:
			for (int i=0;i<nw;i++)
				for (int j=0;j<nw;j++)
					if (arcs[i][j]>0) {
						float epsilon = rand.nextFloat()/100f-0.005f;
						arcs[i][j]+=epsilon;
					}
			float[] res = Edmonds.getMST(arcs);
			if (Edmonds.debug) System.out.println("MST RES "+Arrays.toString(res));
			g.clearDeps();
			if (res==null) {
				g.deps.addAll(bestdeps);
				return g.conf;
			}
			// create the graph
			for (int i=2;i<res.length;) {
				int head=(int)res[i++];
				int gov=(int)res[i++];
				g.ajoutDep(deptyp[head][gov], gov, head);
			}
		}
		return g.conf;
	}

	@Override
	public void init(DetGraph g, Scorer sc) {
		// TODO Auto-generated method stub
		
	}

	public void parse(final List<DetGraph> gs, final List<DetGraph> golds) {
		Thread t = new Thread(new Runnable() {
			@Override
			public void run() {
				Search search = new Search(golds,gs);
				search.uttsearch = new MSTSearch();
				search.scorer=new Combiner();
				search.listeners.add(new Search.listenr() {
					@Override
					public void endOfIter() {
						if (treesframe!=null) {
							treesframe.repaint();
						}
					}
				});
				Search.stopitBeforeNextIter=false;
				Search.stopitNow=false;
				search.search();
			}
		}, "rulesSampler");
		t.start();
	}

	public static void main(String args[]) {
		GraphIO gio = new GraphIO(null);
		java.util.List<DetGraph> gs = gio.loadAllGraphs("xx.xml");
		//		 java.util.List<DetGraph> gs = gio.loadAllGraphs("test2009.xml");
		//		java.util.List<DetGraph> gs = gio.loadAllGraphs("train2011.xml");
		ArrayList<DetGraph> golds = new ArrayList<DetGraph>();
		for (DetGraph g : gs) {
			golds.add(g.clone());
			g.clearDeps();
		}
		golds=null;
		Search.niters=100;
		Search.nstarts=1;
		MSTSearch m = new MSTSearch();
		JSafran j = JSafran.viewGraph(gs,false);
		j.addMouseListener(new MouseListener() {

			@Override
			public void mouseReleased(MouseEvent arg0) {
				// TODO Auto-generated method stub

			}

			@Override
			public void mousePressed(MouseEvent arg0) {
				Search.stopitBeforeNextIter=true;
			}

			@Override
			public void mouseExited(MouseEvent arg0) {
				// TODO Auto-generated method stub

			}

			@Override
			public void mouseEntered(MouseEvent arg0) {
				// TODO Auto-generated method stub

			}

			@Override
			public void mouseClicked(MouseEvent arg0) {
				// TODO Auto-generated method stub

			}
		});
		m.treesframe=j;
		m.parse(gs, golds);
	}
}
