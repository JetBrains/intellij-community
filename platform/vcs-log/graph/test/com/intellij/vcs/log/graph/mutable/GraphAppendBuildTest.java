package com.intellij.vcs.log.graph.mutable;

import com.intellij.vcs.log.GraphCommit;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.graph.GraphTestUtils;
import com.intellij.vcs.log.graph.elements.Branch;
import com.intellij.vcs.log.graph.mutable.elements.MutableNode;
import com.intellij.vcs.log.graph.mutable.elements.MutableNodeRow;
import com.intellij.vcs.log.parser.SimpleCommitListParser;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.intellij.vcs.log.graph.GraphStrUtils.toStr;
import static junit.framework.Assert.assertEquals;

/**
 * @author erokhins
 */
public class GraphAppendBuildTest {
  public void runTest(String firstPart, String firstPartStr, String secondPart, String secondPartStr) {
    List<GraphCommit> vcsCommitParentses = SimpleCommitListParser.parseCommitList(firstPart);
    final MutableGraph graph = GraphTestUtils.buildGraph(vcsCommitParentses, Collections.<VcsRef>emptyList());
    assertEquals(firstPartStr, toStr(graph));

    vcsCommitParentses = SimpleCommitListParser.parseCommitList(secondPart);
    new GraphAppendBuilder(graph, makeRefs(firstPart)) {
      @NotNull
      @Override
      protected GraphBuilder createGraphBuilder(List<GraphCommit> commitParentses, MutableNodeRow nextRow,
                                                Map<Integer, MutableNode> underdoneNodes, int startIndex,
                                                Map<Integer, Integer> commitLogIndexes) {
        return new GraphBuilder(commitParentses.size() + startIndex - 1, commitLogIndexes, graph, underdoneNodes, nextRow,
                                Collections.<VcsRef>emptyList()) {
          @NotNull
          @Override
          protected Branch createBranch(int commitHash, @NotNull Collection<VcsRef> refs) {
            return GraphTestUtils.createBranchWithFakeRoot(commitHash, refs);
          }
        };

      }
    }.appendToGraph(vcsCommitParentses);
    assertEquals(secondPartStr, toStr(graph));
  }

  private static Collection<VcsRef> makeRefs(String log) {
    return null;
  }

  @Test
  public void simpleEnd() {
    runTest("a0|-",

            "a0|-|-|-COMMIT_NODE|-a0|-0",


            "a1|-",

            "a0|-|-|-COMMIT_NODE|-a0|-0\n" + "a1|-|-|-COMMIT_NODE|-a1|-1");
  }


  @Test
  public void oneEndNode() {
    runTest("a0|-a2",

            "a0|-|-a0:a2:USUAL:a0|-COMMIT_NODE|-a0|-0\n" + "a2|-a0:a2:USUAL:a0|-|-END_COMMIT_NODE|-a0|-1",


            "a1|-a2\n" + "a2|-",

            "a0|-|-a0:a2:USUAL:a0|-COMMIT_NODE|-a0|-0\n" +
            "a1|-|-a1:a2:USUAL:a1|-COMMIT_NODE|-a1|-1\n" +
            "a2|-a0:a2:USUAL:a0 a1:a2:USUAL:a1|-|-COMMIT_NODE|-a0|-2");
  }


  @Test
  public void oneEndAfterNotAddNode() {
    runTest("a0|-a2",

            "a0|-|-a0:a2:USUAL:a0|-COMMIT_NODE|-a0|-0\n" + "a2|-a0:a2:USUAL:a0|-|-END_COMMIT_NODE|-a0|-1",


            "a1|-a2", "a0|-|-a0:a2:USUAL:a0|-COMMIT_NODE|-a0|-0\n" +
                      "a1|-|-a1:a2:USUAL:a1|-COMMIT_NODE|-a1|-1\n" +
                      "a2|-a0:a2:USUAL:a0 a1:a2:USUAL:a1|-|-END_COMMIT_NODE|-a0|-2");
  }


  @Test
  public void oneEndImmediatelyAddNode() {
    runTest("a0|-a1",

            "a0|-|-a0:a1:USUAL:a0|-COMMIT_NODE|-a0|-0\n" + "a1|-a0:a1:USUAL:a0|-|-END_COMMIT_NODE|-a0|-1",


            "a1|-",

            "a0|-|-a0:a1:USUAL:a0|-COMMIT_NODE|-a0|-0\n" + "a1|-a0:a1:USUAL:a0|-|-COMMIT_NODE|-a0|-1"

    );
  }

  @Test
  public void oneEndImmediatelyAddNode2() {
    runTest("a0|-a1",

            "a0|-|-a0:a1:USUAL:a0|-COMMIT_NODE|-a0|-0\n" + "a1|-a0:a1:USUAL:a0|-|-END_COMMIT_NODE|-a0|-1",


            "a1|-a2\n" + "a2|-",

            "a0|-|-a0:a1:USUAL:a0|-COMMIT_NODE|-a0|-0\n" +
            "a1|-a0:a1:USUAL:a0|-a1:a2:USUAL:a0|-COMMIT_NODE|-a0|-1\n" +
            "a2|-a1:a2:USUAL:a0|-|-COMMIT_NODE|-a0|-2"

    );
  }

  @Test
  public void edgeNodeInEndImmediately() {
    runTest("a0|-a2\n" + "a1|-a2",

            "a0|-|-a0:a2:USUAL:a0|-COMMIT_NODE|-a0|-0\n" +
            "a1|-|-a1:a2:USUAL:a1|-COMMIT_NODE|-a1|-1\n" +
            "a2|-a0:a2:USUAL:a0 a1:a2:USUAL:a1|-|-END_COMMIT_NODE|-a0|-2",


            "a2|-",

            "a0|-|-a0:a2:USUAL:a0|-COMMIT_NODE|-a0|-0\n" +
            "a1|-|-a1:a2:USUAL:a1|-COMMIT_NODE|-a1|-1\n" +
            "a2|-a0:a2:USUAL:a0 a1:a2:USUAL:a1|-|-COMMIT_NODE|-a0|-2");
  }

  @Test
  public void edgeNodeInEnd() {
    runTest("a0|-a3\n" + "a1|-a3",

            "a0|-|-a0:a3:USUAL:a0|-COMMIT_NODE|-a0|-0\n" +
            "a1|-|-a1:a3:USUAL:a1|-COMMIT_NODE|-a1|-1\n" +
            "a3|-a0:a3:USUAL:a0 a1:a3:USUAL:a1|-|-END_COMMIT_NODE|-a0|-2",


            "a2|-a3",

            "a0|-|-a0:a3:USUAL:a0|-COMMIT_NODE|-a0|-0\n" +
            "a1|-|-a1:a3:USUAL:a1|-COMMIT_NODE|-a1|-1\n" +
            "a2|-|-a2:a3:USUAL:a2|-COMMIT_NODE|-a2|-2\n" +
            "   a3|-a0:a3:USUAL:a0 a1:a3:USUAL:a1|-a3:a3:USUAL:a0|-EDGE_NODE|-a0|-2\n" +
            "a3|-a2:a3:USUAL:a2 a3:a3:USUAL:a0|-|-END_COMMIT_NODE|-a0|-3");
  }
}
