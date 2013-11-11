package com.intellij.vcs.log.graph.mutable;

import com.intellij.vcs.log.GraphCommit;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.graph.GraphTestUtils;
import com.intellij.vcs.log.parser.SimpleCommitListParser;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static com.intellij.vcs.log.graph.GraphStrUtils.toStr;
import static junit.framework.Assert.assertEquals;

/**
 * @author erokhins
 */
public class GraphBuilderTest {


  public void runTest(String input, String out) {
    List<GraphCommit> vcsCommitParentses = SimpleCommitListParser.parseCommitList(input);
    MutableGraph graph = GraphTestUtils.buildGraph(vcsCommitParentses, Collections.<VcsRef>emptyList());
    assertEquals(out, toStr(graph));
  }


  @Test
  public void simple1() {
    runTest("12|-", "12|-|-|-COMMIT_NODE|-12|-0");
  }

  @Test
  public void simple2() {
    runTest("12|-af\n" + "af|-",

            "12|-|-12:af:USUAL:12|-COMMIT_NODE|-12|-0\n" + "af|-12:af:USUAL:12|-|-COMMIT_NODE|-12|-1");
  }

  @Test
  public void simple3() {
    runTest("a0|-a1\n" +
            "a1|-a2\n" +
            "a2|-",

            "a0|-|-a0:a1:USUAL:a0|-COMMIT_NODE|-a0|-0\n" +
            "a1|-a0:a1:USUAL:a0|-a1:a2:USUAL:a0|-COMMIT_NODE|-a0|-1\n" +
            "a2|-a1:a2:USUAL:a0|-|-COMMIT_NODE|-a0|-2");
  }

  @Test
  public void moreParents() {
    runTest("a0|-a1 a2 a3\n" +
            "a1|-\n" +
            "a2|-\n" +
            "a3|-",

            "a0|-|-a0:a1:USUAL:a0#a1 a0:a2:USUAL:a0#a2 a0:a3:USUAL:a0#a3|-COMMIT_NODE|-a0|-0\n" +
            "a1|-a0:a1:USUAL:a0#a1|-|-COMMIT_NODE|-a0#a1|-1\n" +
            "a2|-a0:a2:USUAL:a0#a2|-|-COMMIT_NODE|-a0#a2|-2\n" +
            "a3|-a0:a3:USUAL:a0#a3|-|-COMMIT_NODE|-a0#a3|-3");
  }

  @Test
  public void edgeNodes() {
    runTest("a0|-a1 a3\n" +
            "a1|-a3 a2\n" +
            "a2|-\n" +
            "a3|-",

            "a0|-|-a0:a1:USUAL:a0#a1 a0:a3:USUAL:a0#a3|-COMMIT_NODE|-a0|-0\n" +
            "a1|-a0:a1:USUAL:a0#a1|-a1:a2:USUAL:a1#a2 a1:a3:USUAL:a1#a3|-COMMIT_NODE|-a0#a1|-1\n" +
            "a2|-a1:a2:USUAL:a1#a2|-|-COMMIT_NODE|-a1#a2|-2\n" +
            "   a3|-a0:a3:USUAL:a0#a3 a1:a3:USUAL:a1#a3|-a3:a3:USUAL:a0#a3|-EDGE_NODE|-a0#a3|-2\n" +
            "a3|-a3:a3:USUAL:a0#a3|-|-COMMIT_NODE|-a0#a3|-3");
  }

  @Test
  public void nodeEdge2() {
    runTest("a0|-a1 a3\n" +
            "a1|-a3\n" +
            "a2|-\n" +
            "a3|-",

            "a0|-|-a0:a1:USUAL:a0#a1 a0:a3:USUAL:a0#a3|-COMMIT_NODE|-a0|-0\n" +
            "a1|-a0:a1:USUAL:a0#a1|-a1:a3:USUAL:a0#a1|-COMMIT_NODE|-a0#a1|-1\n" +
            "a2|-|-|-COMMIT_NODE|-a2|-2\n" +
            "   a3|-a0:a3:USUAL:a0#a3 a1:a3:USUAL:a0#a1|-a3:a3:USUAL:a0#a3|-EDGE_NODE|-a0#a3|-2\n" +
            "a3|-a3:a3:USUAL:a0#a3|-|-COMMIT_NODE|-a0#a3|-3");
  }

  @Test
  public void twoChildren() {
    runTest("a0|-a2\n" +
            "a1|-a2\n" +
            "a2|-",

            "a0|-|-a0:a2:USUAL:a0|-COMMIT_NODE|-a0|-0\n" +
            "a1|-|-a1:a2:USUAL:a1|-COMMIT_NODE|-a1|-1\n" +
            "a2|-a0:a2:USUAL:a0 a1:a2:USUAL:a1|-|-COMMIT_NODE|-a0|-2");
  }

  @Test
  public void twoChildren_hard() {
    runTest("a0|-a1 a2\n" +
            "a1|-a2\n" +
            "a2|-",

            "a0|-|-a0:a1:USUAL:a0#a1 a0:a2:USUAL:a0#a2|-COMMIT_NODE|-a0|-0\n" +
            "a1|-a0:a1:USUAL:a0#a1|-a1:a2:USUAL:a0#a1|-COMMIT_NODE|-a0#a1|-1\n" +
            "a2|-a0:a2:USUAL:a0#a2 a1:a2:USUAL:a0#a1|-|-COMMIT_NODE|-a0#a2|-2");
  }

  @Test
  public void simpleNotFullGraph() {
    runTest("a0|-a1",

            "a0|-|-a0:a1:USUAL:a0|-COMMIT_NODE|-a0|-0\n" + "a1|-a0:a1:USUAL:a0|-|-END_COMMIT_NODE|-a0|-1");
  }

  @Test
  public void notFullGraph_EdgeNode() {
    runTest("a0|-a2\n" + "a1|-a2",

            "a0|-|-a0:a2:USUAL:a0|-COMMIT_NODE|-a0|-0\n" +
            "a1|-|-a1:a2:USUAL:a1|-COMMIT_NODE|-a1|-1\n" +
            "a2|-a0:a2:USUAL:a0 a1:a2:USUAL:a1|-|-END_COMMIT_NODE|-a0|-2");
  }


  @Test
  public void notFullGraph_EdgeNode_hard() {
    runTest("a0|-a3\n" + "a1|-a2 a3",

            "a0|-|-a0:a3:USUAL:a0|-COMMIT_NODE|-a0|-0\n" +
            "a1|-|-a1:a2:USUAL:a1#a2 a1:a3:USUAL:a1#a3|-COMMIT_NODE|-a1|-1\n" +
            "a2|-a1:a2:USUAL:a1#a2|-|-END_COMMIT_NODE|-a1#a2|-2\n" +
            "   a3|-a0:a3:USUAL:a0 a1:a3:USUAL:a1#a3|-|-END_COMMIT_NODE|-a0|-2");
  }
}
