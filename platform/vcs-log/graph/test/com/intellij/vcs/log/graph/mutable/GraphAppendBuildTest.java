package com.intellij.vcs.log.graph.mutable;

import com.intellij.vcs.log.VcsCommit;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.parser.SimpleCommitListParser;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static junit.framework.Assert.assertEquals;
import static com.intellij.vcs.log.graph.GraphStrUtils.toStr;

/**
 * @author erokhins
 */
public class GraphAppendBuildTest {
  public void runTest(String firstPart, String firstPartStr, String secondPart, String secondPartStr) {
    List<VcsCommit> vcsCommitParentses = SimpleCommitListParser.parseCommitList(firstPart);
    MutableGraph graph = GraphBuilder.build(vcsCommitParentses, Collections.<VcsRef>emptyList());
    assertEquals(firstPartStr, toStr(graph));

    vcsCommitParentses = SimpleCommitListParser.parseCommitList(secondPart);
    GraphBuilder.addCommitsToGraph(graph, vcsCommitParentses, Collections.<VcsRef>emptyList());
    assertEquals(secondPartStr, toStr(graph));
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
