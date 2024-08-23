package org.jetbrains.java.decompiler.util;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.jetbrains.java.decompiler.code.cfg.BasicBlock;
import org.jetbrains.java.decompiler.code.cfg.ControlFlowGraph;
import org.jetbrains.java.decompiler.modules.decompiler.StatEdge;
import org.jetbrains.java.decompiler.modules.decompiler.sforms.DirectGraph;
import org.jetbrains.java.decompiler.modules.decompiler.sforms.DirectNode;
import org.jetbrains.java.decompiler.modules.decompiler.stats.Statement;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarVersionEdge;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarVersionNode;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarVersionsGraph;
import org.jetbrains.java.decompiler.struct.StructMethod;
import org.jetbrains.java.decompiler.util.FastSparseSetFactory.FastSparseSet;

public class DotExporter {
  private static final String DOTS_FOLDER = System.getProperty("DOT_EXPORT_DIR", null);
  private static final boolean DUMP_DOTS = DOTS_FOLDER != null;
  // http://graphs.grevian.org/graph is a nice visualizer for the outputed dots.

  private static String toDotFormat(Statement stat) {

    StringBuffer buffer = new StringBuffer();

    buffer.append("digraph G {\r\n");

    for(Statement st : stat.getStats()) {

      String sourceid = st.id + (st.getSuccessorEdges(StatEdge.EdgeType.EXCEPTION).isEmpty()?"":"000000");

      buffer.append(sourceid+" [shape=box,label=\""+sourceid+"\"];\r\n");

      for(StatEdge edge : st.getSuccessorEdges(StatEdge.EdgeType.DIRECT_ALL)) {
        String destid = edge.getDestination().id + (edge.getDestination().getSuccessorEdges(StatEdge.EdgeType.EXCEPTION).isEmpty()?"":"000000");

        buffer.append(sourceid+"->"+destid+";\r\n");

        if(!stat.getStats().contains(edge.getDestination())) {
          buffer.append(destid+" [label=\""+destid+"\"];\r\n");
        }
      }

      for(StatEdge edge : st.getSuccessorEdges(StatEdge.EdgeType.EXCEPTION)) {
        String destid = edge.getDestination().id + (edge.getDestination().getSuccessorEdges(StatEdge.EdgeType.EXCEPTION).isEmpty()?"":"000000");

        buffer.append(sourceid+" -> "+destid+" [style=dotted];\r\n");

        if(!stat.getStats().contains(edge.getDestination())) {
          buffer.append(destid+" [label=\""+destid+"\"];\r\n");
        }
      }
    }

    buffer.append("}");

    return buffer.toString();
  }


  private static String toDotFormat(ControlFlowGraph graph, boolean showMultipleEdges) {

    StringBuffer buffer = new StringBuffer();

    buffer.append("digraph G {\r\n");

    List<BasicBlock> blocks = graph.getBlocks();
    for(int i=0;i<blocks.size();i++) {
      BasicBlock block = blocks.get(i);

      buffer.append(block.id+" [shape=box,label=\""+block.id+"\"];\r\n");


      List<BasicBlock> suc = block.getSuccessors();
      if(!showMultipleEdges) {
        HashSet<BasicBlock> set = new HashSet<>();
        set.addAll(suc);
        suc = Collections.list(Collections.enumeration(set));
      }
      for(int j=0;j<suc.size();j++) {
        buffer.append(block.id+"->"+suc.get(j).id+";\r\n");
      }


      suc = block.getSuccessorExceptions();
      if(!showMultipleEdges) {
        HashSet<BasicBlock> set = new HashSet<>();
        set.addAll(suc);
        suc = Collections.list(Collections.enumeration(set));
      }
      for(int j=0;j<suc.size();j++) {
        buffer.append(block.id+" -> "+suc.get(j).id+" [style=dotted];\r\n");
      }
    }

    buffer.append("}");

    return buffer.toString();
  }

  private static String toDotFormat(VarVersionsGraph graph) {

    StringBuffer buffer = new StringBuffer();

    buffer.append("digraph G {\r\n");

    List<VarVersionNode> blocks = graph.nodes;
    for(int i=0;i<blocks.size();i++) {
      VarVersionNode block = blocks.get(i);

      buffer.append((block.var*1000+block.version)+" [shape=box,label=\""+block.var+"_"+block.version+"\"];\r\n");

      for(VarVersionEdge edge: block.successors) {
        VarVersionNode dest = edge.dest;
        buffer.append((block.var*1000+block.version)+"->"+(dest.var*1000+dest.version)+(edge.type==VarVersionEdge.EDGE_PHANTOM?" [style=dotted]":"")+";\r\n");
      }
    }

    buffer.append("}");

    return buffer.toString();
  }

  private static String toDotFormat(DirectGraph graph, Map<String, SFormsFastMapDirect> vars) {

    StringBuffer buffer = new StringBuffer();

    buffer.append("digraph G {\r\n");

    List<DirectNode> blocks = graph.nodes;
    for(int i=0;i<blocks.size();i++) {
      DirectNode block = blocks.get(i);

      StringBuilder label = new StringBuilder(block.id);
      if (vars != null && vars.containsKey(block.id)) {
        SFormsFastMapDirect map = vars.get(block.id);

        List<Entry<Integer, FastSparseSet<Integer>>> lst = map.entryList();
        if (lst != null) {
          for (Entry<Integer, FastSparseSet<Integer>> entry : lst) {
             label.append("\\n").append(entry.getKey());
            Set<Integer> set = entry.getValue().toPlainSet();
            label.append("=").append(set.toString());
          }
        }
      }

      buffer.append(directBlockIdToDot(block.id)+" [shape=box,label=\""+label+"\"];\r\n");

      for(DirectNode dest: block.successors) {
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

  private static File getFile(StructMethod mt, String suffix) {
    File root = new File(DOTS_FOLDER + mt.getClassQualifiedName());
    if (!root.isDirectory())
      root.mkdirs();
    return new File(root,
      mt.getName().replace('<', '.').replace('>', '_') +
      mt.getDescriptor().replace('/', '.') +
      '_' + suffix + ".dot");
  }

  public static void toDotFile(DirectGraph dgraph, StructMethod mt, String suffix) {
    toDotFile(dgraph, mt, suffix, null);
  }
  public static void toDotFile(DirectGraph dgraph, StructMethod mt, String suffix, Map<String, SFormsFastMapDirect> vars) {
    if (!DUMP_DOTS)
      return;
    try{
      BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(getFile(mt, suffix)));
      out.write(toDotFormat(dgraph, vars).getBytes());
      out.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public static void toDotFile(Statement stat, StructMethod mt, String suffix) {
    if (!DUMP_DOTS)
      return;
    try{
      BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(getFile(mt, suffix)));
      out.write(toDotFormat(stat).getBytes());
      out.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public static void toDotFile(VarVersionsGraph graph, StructMethod mt, String suffix) {
    if (!DUMP_DOTS)
      return;
    try{
      BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(getFile(mt, suffix)));
      out.write(toDotFormat(graph).getBytes());
      out.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public static void toDotFile(ControlFlowGraph graph, StructMethod mt, String suffix, boolean showMultipleEdges) {
    if (!DUMP_DOTS)
      return;
    try{
      BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(getFile(mt, suffix)));
      out.write(toDotFormat(graph, showMultipleEdges).getBytes());
      out.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}