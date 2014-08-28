package test.util;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;


import de.fernflower.code.cfg.BasicBlock;
import de.fernflower.code.cfg.ControlFlowGraph;
import de.fernflower.modules.decompiler.StatEdge;
import de.fernflower.modules.decompiler.sforms.DirectGraph;
import de.fernflower.modules.decompiler.sforms.DirectNode;
import de.fernflower.modules.decompiler.stats.Statement;
import de.fernflower.modules.decompiler.vars.VarVersionEdge;
import de.fernflower.modules.decompiler.vars.VarVersionNode;
import de.fernflower.modules.decompiler.vars.VarVersionsGraph;

public class DotExporter {

	
	public static String toDotFormat(Statement stat) {
		
		StringBuffer buffer = new StringBuffer();
		
		buffer.append("digraph G {\r\n");
		
		for(Statement st : stat.getStats()) {
			
			String sourceid = st.id + (st.getSuccessorEdges(StatEdge.TYPE_EXCEPTION).isEmpty()?"":"000000");
			
			buffer.append(sourceid+" [shape=box,label=\""+sourceid+"\"];\r\n");
			
			for(StatEdge edge : st.getSuccessorEdges(Statement.STATEDGE_DIRECT_ALL)) {
				String destid = edge.getDestination().id + (edge.getDestination().getSuccessorEdges(StatEdge.TYPE_EXCEPTION).isEmpty()?"":"000000");

				buffer.append(sourceid+"->"+destid+";\r\n");
				
				if(!stat.getStats().contains(edge.getDestination())) {
					buffer.append(destid+" [label=\""+destid+"\"];\r\n");
				}
			}

			for(StatEdge edge : st.getSuccessorEdges(StatEdge.TYPE_EXCEPTION)) {
				String destid = edge.getDestination().id + (edge.getDestination().getSuccessorEdges(StatEdge.TYPE_EXCEPTION).isEmpty()?"":"000000");

				buffer.append(sourceid+" -> "+destid+" [style=dotted];\r\n");

				if(!stat.getStats().contains(edge.getDestination())) {
					buffer.append(destid+" [label=\""+destid+"\"];\r\n");
				}
			}
		}
		
		buffer.append("}");
		
		return buffer.toString(); 
	}
	
	
	public static String toDotFormat(ControlFlowGraph graph, boolean showMultipleEdges) {
		
		StringBuffer buffer = new StringBuffer();
		
		buffer.append("digraph G {\r\n");
		
		List<BasicBlock> blocks = graph.getBlocks(); 
		for(int i=0;i<blocks.size();i++) {
			BasicBlock block = (BasicBlock)blocks.get(i);
			
			buffer.append(block.id+" [shape=box,label=\""+block.id+"\"];\r\n");
			
			
			List<BasicBlock> suc = block.getSuccs();
			if(!showMultipleEdges) {
				HashSet<BasicBlock> set = new HashSet<BasicBlock>();
				set.addAll(suc);
				suc = Collections.list(Collections.enumeration(set));
			}
			for(int j=0;j<suc.size();j++) {
				buffer.append(block.id+"->"+((BasicBlock)suc.get(j)).id+";\r\n");
			}

			
			suc = block.getSuccExceptions();
			if(!showMultipleEdges) {
				HashSet<BasicBlock> set = new HashSet<BasicBlock>();
				set.addAll(suc);
				suc = Collections.list(Collections.enumeration(set));
			}
			for(int j=0;j<suc.size();j++) {
				buffer.append(block.id+" -> "+((BasicBlock)suc.get(j)).id+" [style=dotted];\r\n");
			}
		}
		
		buffer.append("}");
		
		return buffer.toString(); 
	}

	public static String toDotFormat(VarVersionsGraph graph) {
		
		StringBuffer buffer = new StringBuffer();
		
		buffer.append("digraph G {\r\n");
		
		List<VarVersionNode> blocks = graph.nodes; 
		for(int i=0;i<blocks.size();i++) {
			VarVersionNode block = blocks.get(i);
			
			buffer.append((block.var*1000+block.version)+" [shape=box,label=\""+block.var+"_"+block.version+"\"];\r\n");
			
			for(VarVersionEdge edge: block.succs) {
				VarVersionNode dest = edge.dest;
				buffer.append((block.var*1000+block.version)+"->"+(dest.var*1000+dest.version)+(edge.type==VarVersionEdge.EDGE_PHANTOM?" [style=dotted]":"")+";\r\n");
			}
		}
		
		buffer.append("}");
		
		return buffer.toString(); 
	}

	public static String toDotFormat(DirectGraph graph) {
		
		StringBuffer buffer = new StringBuffer();
		
		buffer.append("digraph G {\r\n");
		
		List<DirectNode> blocks = graph.nodes; 
		for(int i=0;i<blocks.size();i++) {
			DirectNode block = blocks.get(i);
			
			buffer.append(directBlockIdToDot(block.id)+" [shape=box,label=\""+directBlockIdToDot(block.id)+"\"];\r\n");
			
			for(DirectNode dest: block.succs) {
				buffer.append(directBlockIdToDot(block.id)+"->"+directBlockIdToDot(dest.id)+";\r\n");
			}
		}
		
		buffer.append("}");
		
		return buffer.toString(); 
	}
	
	private static String directBlockIdToDot(String id) {
		id = id.replaceAll("_try", "999");
		id = id.replaceAll("_tail", "888");

		id = id.replaceAll("_init", "111");
		id = id.replaceAll("_cond", "222");
		id = id.replaceAll("_inc", "333");
		return id;
	}

	public static void toDotFile(ControlFlowGraph graph, File file, boolean showMultipleEdges) throws FileNotFoundException, IOException {
		BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(file)); 
		out.write(toDotFormat(graph, showMultipleEdges).getBytes());
		out.close(); 
	}

	public static void toDotFile(VarVersionsGraph graph, File file) throws FileNotFoundException, IOException {
		
		BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(file)); 
		out.write(toDotFormat(graph).getBytes());
		out.close(); 
	}

	public static void toDotFile(DirectGraph graph, File file) throws FileNotFoundException, IOException {
		
		BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(file)); 
		out.write(toDotFormat(graph).getBytes());
		out.close(); 
	}

	public static void toDotFile(Statement stat, File file) throws FileNotFoundException, IOException {
		
		BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(file)); 
		out.write(toDotFormat(stat).getBytes());
		out.close(); 
	}
	
}
