package com.intellij.vcs.log.graphmodel.fragment;

import junit.framework.Assert;
import com.intellij.vcs.log.graph.GraphTestUtils;
import com.intellij.vcs.log.graph.elements.Node;
import com.intellij.vcs.log.graph.mutable.MutableGraph;
import com.intellij.vcs.log.graphmodel.GraphFragment;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import static com.intellij.vcs.log.graph.GraphTestUtils.getCommitNode;
import static com.intellij.vcs.log.graphmodel.fragment.GraphModelUtils.parseUnhiddenNodes;
import static com.intellij.vcs.log.graphmodel.fragment.GraphModelUtils.toStr;

/**
 * @author erokhins
 */
public class ShortFragmentTest {

    public void runTest(@NotNull String inputGraph, int rowIndex, String fragmentStr, final String unhiddenNodeRows, boolean down) {
        MutableGraph graph = GraphTestUtils.getNewMutableGraph(inputGraph);
        ShortFragmentGenerator shortFragmentGenerator = new ShortFragmentGenerator(graph);
        shortFragmentGenerator.setUnconcealedNodeFunction(parseUnhiddenNodes(graph, unhiddenNodeRows));

        Node commitNode = getCommitNode(graph, rowIndex);
        GraphFragment fragment;
        if (down) {
            fragment = shortFragmentGenerator.getDownShortFragment(commitNode);
        } else {
            fragment = shortFragmentGenerator.getUpShortFragment(commitNode);
        }
        Assert.assertEquals(fragmentStr, toStr(fragment));
    }

    public void runTest(@NotNull String inputGraph, int rowIndex, String fragmentStr, boolean down) {
        runTest(inputGraph, rowIndex, fragmentStr, "", down);
    }



    @Test
    public void simpleDownTest() {
        runTest(
                "a0|-a1\n" +
                        "a1|-a2\n" +
                        "a2|-",
                0,

                "a0:0|-|-a1:1",
                true
        );
    }

    @Test
    public void simpleUpTest() {
        runTest(
                "a0|-a1\n" +
                "a1|-a2\n"  +
                "a2|-",
                1,

                "a0:0|-|-a1:1",
                false
        );
    }

    @Test
    public void simpleNullDown() {
        runTest(
                "a0|-a1\n" +
                        "a1|-a2\n" +
                        "a2|-",
                2,
                "null",
                true
        );
    }

    @Test
    public void simpleNullUp() {
        runTest(
                "a0|-a1\n" +
                "a1|-a2\n"  +
                "a2|-",
                0,
                "null",
                false
        );
    }



    @Test
    public void severalChildrenDown() {
        runTest(
                "a0|-a1 a2\n" +
                        "a1|-a2\n" +
                        "a2|-",
                0,
                "a0:0|-a1:1|-a2:2",
                true
        );
    }


    @Test
    public void severalChildrenUp() {
        runTest(
                "a0|-a1 a2\n" +
                "a1|-a2\n"  +
                "a2|-",
                2,
                "a0:0|-a1:1|-a2:2",
                false
        );
    }



    @Test
    public void badEndNodeDown() {
        runTest(
                "a0|-a1 a3\n" +
                        "a1|-a3\n" +
                        "a2|-a3\n" +
                        "a3|-",
                0,
                "null",
                true
        );
    }

    @Test
    public void badEndNodeUp() {
        runTest(
                "a0|-a2 a3 a1\n" +
                "a1|-\n"  +
                "a2|-a3\n" +
                "a3|-",
                3,
                "null",
                false
        );
    }



    @Test
    public void badIntermediateNodeDown() {
        runTest(
                "a0|-a2 a3\n" +
                        "a1|-a2\n" +
                        "a2|-a3\n" +
                        "a3|-",
                0,
                "null",
                true
        );
    }

    @Test
    public void badIntermediateNodeUp() {
        runTest(
                "a0|-a1 a3\n" +
                "a1|-a2 a3\n"  +
                "a2|-\n" +
                "a3|-",
                3,
                "null",
                false
        );
    }


    @Test
    public void longEndNodeDown() {
        runTest(
                "a0|-a1 a2\n" +
                        "a1|-a2\n" +
                        "a2|-a3\n" +
                        "a3|-",
                0,
                "a0:0|-a1:1|-a2:2",
                true
        );
    }

    @Test
    public void longEndNodeUp() {
        runTest(
                "a0|-a1\n" +
                "a1|-a2 a3\n"  +
                "a2|-a3\n" +
                "a3|-",
                3,
                "a1:1|-a2:2|-a3:3",
                false
        );
    }


    @Test
    public void edgeNodesDown() {
        runTest(
                "a0|-a3 a1\n" +
                        "a1|-a2 a3\n" +
                        "a2|-a3\n" +
                        "a3|-",
                0,
                "a0:0|-a1:1 a2:2 a3:2|-a3:3",
                true
        );
    }

    @Test
    public void edgeNodesUp() {
        runTest(
                "a0|-a3 a1\n" +
                "a1|-a2 a3\n"  +
                "a2|-a3\n" +
                "a3|-",
                3,
                "a0:0|-a1:1 a2:2 a3:2|-a3:3",
                false
        );
    }

    @Test
    public void unhiddenMiddleTestDown() {
        runTest(
                "a0|-a3 a1\n" +
                        "a1|-a2 a3\n" +
                        "a2|-a3\n" +
                        "a3|-",
                0,
                "null",
                "1",
                true
        );
    }


    @Test
    public void unhiddenMiddleTestUp() {
        runTest(
                "a0|-a3 a1\n" +
                "a1|-a2 a3\n" +
                "a2|-a3\n" +
                "a3|-",
                3,
                "null",
                "1",
                false
        );
    }

    @Test
    public void unhiddenEndTestDown() {
        runTest(
                "a0|-a3 a1\n" +
                        "a1|-a2 a3\n" +
                        "a2|-a3\n" +
                        "a3|-",
                0,
                "a0:0|-a1:1 a2:2 a3:2|-a3:3",
                "0 3",
                true
        );
    }


    @Test
    public void unhiddenEndTestUp() {
        runTest(
                "a0|-a3 a1\n" +
                "a1|-a2 a3\n" +
                "a2|-a3\n" +
                "a3|-",
                3,
                "a0:0|-a1:1 a2:2 a3:2|-a3:3",
                "0 3",
                false
        );
    }


    @Test
    public void doubleLineEndTestDown() {
        runTest(
                "a0|-a2 a1\n" +
                "a1|-\n" +
                "a2|-",
                0,
                "a0:0|-a1:1|-a2:2",
                true
        );
    }

    @Test
    public void doubleLineEndTestUp() {
        runTest(
                "a0|-a2\n" +
                "a1|-a2\n" +
                "a2|-",
                2,
                "a0:0|-a1:1|-a2:2",
                false
        );
    }





}
