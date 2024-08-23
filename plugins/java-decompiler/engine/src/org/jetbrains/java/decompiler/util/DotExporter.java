// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.util;

import org.jetbrains.java.decompiler.code.cfg.BasicBlock;
import org.jetbrains.java.decompiler.code.cfg.ControlFlowGraph;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.modules.decompiler.StatEdge;
import org.jetbrains.java.decompiler.modules.decompiler.sforms.DirectGraph;
import org.jetbrains.java.decompiler.modules.decompiler.sforms.DirectNode;
import org.jetbrains.java.decompiler.modules.decompiler.stats.Statement;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarVersionEdge;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarVersionNode;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarVersionsGraph;
import org.jetbrains.java.decompiler.struct.StructMethod;
import org.jetbrains.java.decompiler.util.FastSparseSetFactory.FastSparseSet;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.Charset;
import java.util.*;
import java.util.Map.Entry;

@SuppressWarnings("CallToPrintStackTrace")
public final class DotExporter {

  private static String toDotFormat(Statement stat) {

    StringBuilder buffer = new StringBuilder();

    buffer.append("digraph G {\r\n");

    for (Statement st : stat.getStats()) {

      String sourceid = st.id + (st.getSuccessorEdges(StatEdge.EdgeType.EXCEPTION).isEmpty() ? "" : "000000");

      buffer.append(sourceid).append(" [shape=box,label=\"").append(sourceid).append("\"];\r\n");

      for (StatEdge edge : st.getSuccessorEdges(StatEdge.EdgeType.DIRECT_ALL)) {
        String destid =
          edge.getDestination().id + (edge.getDestination().getSuccessorEdges(StatEdge.EdgeType.EXCEPTION).isEmpty() ? "" : "000000");

        buffer.append(sourceid).append("->").append(destid).append(";\r\n");

        if (!stat.getStats().contains(edge.getDestination())) {
          buffer.append(destid).append(" [label=\"").append(destid).append("\"];\r\n");
        }
      }

      for (StatEdge edge : st.getSuccessorEdges(StatEdge.EdgeType.EXCEPTION)) {
        String destid =
          edge.getDestination().id + (edge.getDestination().getSuccessorEdges(StatEdge.EdgeType.EXCEPTION).isEmpty() ? "" : "000000");

        buffer.append(sourceid).append(" -> ").append(destid).append(" [style=dotted];\r\n");

        if (!stat.getStats().contains(edge.getDestination())) {
          buffer.append(destid).append(" [label=\"").append(destid).append("\"];\r\n");
        }
      }
    }

    buffer.append("}");

    return buffer.toString();
  }


  private static String toDotFormat(ControlFlowGraph graph, boolean showMultipleEdges) {

    StringBuilder buffer = new StringBuilder();

    buffer.append("digraph G {\r\n");

    List<BasicBlock> blocks = graph.getBlocks();
    for (int i = 0; i < blocks.size(); i++) {
      BasicBlock block = blocks.get(i);

      buffer.append(block.id).append(" [shape=box,label=\"").append(block.id).append("\"];\r\n");


      List<BasicBlock> suc = block.getSuccessors();
      if (!showMultipleEdges) {
        HashSet<BasicBlock> set = new HashSet<>(suc);
        suc = Collections.list(Collections.enumeration(set));
      }
      for (int j = 0; j < suc.size(); j++) {
        buffer.append(block.id).append("->").append(suc.get(j).id).append(";\r\n");
      }


      suc = block.getSuccessorExceptions();
      if (!showMultipleEdges) {
        HashSet<BasicBlock> set = new HashSet<>(suc);
        suc = Collections.list(Collections.enumeration(set));
      }
      for (int j = 0; j < suc.size(); j++) {
        buffer.append(block.id).append(" -> ").append(suc.get(j).id).append(" [style=dotted];\r\n");
      }
    }

    buffer.append("}");

    return buffer.toString();
  }

  private static String toDotFormat(VarVersionsGraph graph) {

    StringBuilder buffer = new StringBuilder();

    buffer.append("digraph G {\r\n");

    List<VarVersionNode> blocks = graph.nodes;
    for (int i = 0; i < blocks.size(); i++) {
      VarVersionNode block = blocks.get(i);

      buffer.append((block.var * 1000 + block.version)).append(" [shape=box,label=\"").append(block.var).append("_").append(block.version)
        .append("\"];\r\n");

      for (VarVersionEdge edge : block.successors) {
        VarVersionNode dest = edge.dest;
        buffer.append((block.var * 1000 + block.version)).append("->").append(dest.var * 1000 + dest.version)
          .append(edge.type == VarVersionEdge.EDGE_PHANTOM ? " [style=dotted]" : "").append(";\r\n");
      }
    }

    buffer.append("}");

    return buffer.toString();
  }

  private static String toDotFormat(DirectGraph graph, Map<String, SFormsFastMapDirect> vars) {

    StringBuilder buffer = new StringBuilder();

    buffer.append("digraph G {\r\n");

    List<DirectNode> blocks = graph.nodes;
    for (int i = 0; i < blocks.size(); i++) {
      DirectNode block = blocks.get(i);

      StringBuilder label = new StringBuilder(block.id);
      if (vars != null && vars.containsKey(block.id)) {
        SFormsFastMapDirect map = vars.get(block.id);

        List<Entry<Integer, FastSparseSet<Integer>>> lst = map.entryList();
        if (lst != null) {
          for (Entry<Integer, FastSparseSet<Integer>> entry : lst) {
            label.append("\\n").append(entry.getKey());
            Set<Integer> set = entry.getValue().toPlainSet();
            label.append("=").append(set);
          }
        }
      }

      buffer.append(directBlockIdToDot(block.id)).append(" [shape=box,label=\"").append(label).append("\"];\r\n");

      for (DirectNode dest : block.successors) {
        buffer.append(directBlockIdToDot(block.id)).append("->").append(directBlockIdToDot(dest.id)).append(";\r\n");
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
    String folder =  DecompilerContext.getProperty(IFernflowerPreferences.DOTS_FOLDER).toString();
    File root = new File(folder + mt.getClassQualifiedName());
    if (!root.isDirectory()) {
      root.mkdirs();
    }
    return new File(root,
                    mt.getName().replace('<', '.').replace('>', '_') +
                    mt.getDescriptor().replace('/', '.') +
                    '_' + suffix + ".dot");
  }

  public static void toDotFile(DirectGraph dgraph, StructMethod mt, String suffix) {
    toDotFile(dgraph, mt, suffix, null);
  }

  public static void toDotFile(DirectGraph dgraph, StructMethod mt, String suffix, Map<String, SFormsFastMapDirect> vars) {
    if (DecompilerContext.getProperty(IFernflowerPreferences.DOTS_FOLDER) == null) {
      return;
    }
    try {
      BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(getFile(mt, suffix)));
      out.write(toDotFormat(dgraph, vars).getBytes(Charset.defaultCharset()));
      out.close();
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  @SuppressWarnings("unused")
  public static void toDotFile(Statement stat, StructMethod mt, String suffix) {
    if (DecompilerContext.getProperty(IFernflowerPreferences.DOTS_FOLDER) == null) {
      return;
    }
    try {
      BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(getFile(mt, suffix)));
      out.write(toDotFormat(stat).getBytes(Charset.defaultCharset()));
      out.close();
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  @SuppressWarnings("unused")
  public static void toDotFile(VarVersionsGraph graph, StructMethod mt, String suffix) {
    if (DecompilerContext.getProperty(IFernflowerPreferences.DOTS_FOLDER) == null) {
      return;
    }
    try {
      BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(getFile(mt, suffix)));
      out.write(toDotFormat(graph).getBytes(Charset.defaultCharset()));
      out.close();
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  @SuppressWarnings("unused")
  public static void toDotFile(ControlFlowGraph graph, StructMethod mt, String suffix, boolean showMultipleEdges) {
    if (DecompilerContext.getProperty(IFernflowerPreferences.DOTS_FOLDER) == null) {
      return;
    }
    try {
      BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(getFile(mt, suffix)));
      out.write(toDotFormat(graph, showMultipleEdges).getBytes(Charset.defaultCharset()));
      out.close();
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }
}