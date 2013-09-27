package com.intellij.vcs.log.graph;

import com.intellij.vcs.log.VcsCommit;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.graph.elements.Node;
import com.intellij.vcs.log.graph.elements.NodeRow;
import com.intellij.vcs.log.graph.mutable.GraphBuilder;
import com.intellij.vcs.log.graph.mutable.MutableGraph;
import com.intellij.vcs.log.parser.SimpleCommitListParser;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.StringReader;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author erokhins
 */
public class GraphTestUtils {


  @NotNull
  public static Node getCommitNode(Graph graph, int rowIndex) {
    NodeRow row = graph.getNodeRows().get(rowIndex);
    for (Node node : row.getNodes()) {
      if (node.getType() == Node.NodeType.COMMIT_NODE) {
        return node;
      }
    }
    throw new IllegalArgumentException();
  }

  @NotNull
  public static MutableGraph getNewMutableGraph(@NotNull String inputStr) {
    SimpleCommitListParser parser = new SimpleCommitListParser(new StringReader(inputStr));
    List<VcsCommit> vcsCommitParentses;
    try {
      vcsCommitParentses = parser.readAllCommits();
    }
    catch (IOException e) {
      throw new IllegalStateException();
    }
    return GraphBuilder.build(vcsCommitParentses, Collections.<VcsRef>emptyList());
  }

  // "1 20 3" -> {1,20,3}
  @NotNull
  public static Set<Integer> parseIntegers(@NotNull String str) {
    if (str.length() == 0) {
      return Collections.emptySet();
    }
    String[] strings = str.split(" ");
    Set<Integer> integers = new HashSet<Integer>();
    for (String s : strings) {
      integers.add(Integer.parseInt(s));
    }
    return integers;
  }
}
