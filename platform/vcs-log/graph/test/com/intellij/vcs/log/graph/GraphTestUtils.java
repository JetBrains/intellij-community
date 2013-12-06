package com.intellij.vcs.log.graph;

import com.intellij.openapi.vfs.newvfs.impl.StubVirtualFile;
import com.intellij.vcs.log.GraphCommit;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.graph.elements.Branch;
import com.intellij.vcs.log.graph.elements.Node;
import com.intellij.vcs.log.graph.elements.NodeRow;
import com.intellij.vcs.log.graph.mutable.GraphBuilder;
import com.intellij.vcs.log.graph.mutable.MutableGraph;
import com.intellij.vcs.log.parser.SimpleCommitListParser;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.StringReader;
import java.util.*;

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
    List<GraphCommit> vcsCommitParentses;
    try {
      vcsCommitParentses = parser.readAllCommits();
    }
    catch (IOException e) {
      throw new IllegalStateException();
    }
    return buildGraph(vcsCommitParentses, Collections.<VcsRef>emptyList());
  }

  @NotNull
  public static MutableGraph buildGraph(@NotNull List<GraphCommit> commitParentses, @NotNull List<VcsRef> refs) {
    GraphBuilder builder = new GraphBuilder(commitParentses.size() - 1, GraphBuilder.calcCommitLogIndices(commitParentses), refs) {
      @NotNull
      @Override
      protected Branch createBranch(int commitHash, @NotNull Collection<VcsRef> refs) {
        // allow no refs in tests
        return createBranchWithFakeRoot(commitHash, refs);
      }
    };
    return builder.runBuild(commitParentses);
  }

  @NotNull
  public static Branch createBranchWithFakeRoot(int commitHash, @NotNull Collection<VcsRef> refs) {
    return new Branch(commitHash, refs, new StubVirtualFile());
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
