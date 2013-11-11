package com.intellij.vcs.log.graphmodel.fragment;

import com.intellij.util.Function;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.graph.GraphTestUtils;
import com.intellij.vcs.log.graph.elements.Node;
import com.intellij.vcs.log.graph.mutable.MutableGraph;
import com.intellij.vcs.log.graphmodel.FragmentManager;
import com.intellij.vcs.log.graphmodel.GraphFragment;
import com.intellij.vcs.log.graphmodel.GraphModel;
import com.intellij.vcs.log.graphmodel.impl.GraphModelImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static com.intellij.vcs.log.graph.GraphStrUtils.toStr;
import static com.intellij.vcs.log.graph.GraphTestUtils.getCommitNode;
import static junit.framework.Assert.assertEquals;

/**
 * @author erokhins
 */
public class GraphModelTest {

    private GraphModel simpleGraph;
    private GraphModel middleGraph;
    private GraphModel hardGraph;


    @NotNull
    public GraphModel buildGraphModel(@NotNull String inputGraph) {
        MutableGraph graph = GraphTestUtils.getNewMutableGraph(inputGraph);
        return new GraphModelImpl(graph, Collections.<VcsRef>emptyList());
    }


    /**
     * a0
     *  |
     * a1
     *  |
     * a2
     *  |
     *  | a3
     *  |  |
     *  | a4
     *  |  |
     *  | a5
     *  | /
     *  a6
     *  |
     *  a7
     *  |
     *  a8
     *  |
     *  a9
     */


    @Before
    public void initMiddleGraph() {
        middleGraph = buildGraphModel(
                "a0|-a1\n" +
                        "a1|-a2\n" +
                        "a2|-a6\n" +
                        "a3|-a4\n" +
                        "a4|-a5\n" +
                        "a5|-a6\n" +
                        "a6|-a7\n" +
                        "a7|-a8\n" +
                        "a8|-a9\n" +
                        "a9|-"
        );
        assertEquals("init graph",
                toStr(middleGraph.getGraph()),

                "a0|-|-a0:a1:USUAL:a0|-COMMIT_NODE|-a0|-0\n" +
                "a1|-a0:a1:USUAL:a0|-a1:a2:USUAL:a0|-COMMIT_NODE|-a0|-1\n" +
                "a2|-a1:a2:USUAL:a0|-a2:a6:USUAL:a0|-COMMIT_NODE|-a0|-2\n" +
                "a3|-|-a3:a4:USUAL:a3|-COMMIT_NODE|-a3|-3\n" +
                "a4|-a3:a4:USUAL:a3|-a4:a5:USUAL:a3|-COMMIT_NODE|-a3|-4\n" +
                "a5|-a4:a5:USUAL:a3|-a5:a6:USUAL:a3|-COMMIT_NODE|-a3|-5\n" +
                "a6|-a2:a6:USUAL:a0 a5:a6:USUAL:a3|-a6:a7:USUAL:a0|-COMMIT_NODE|-a0|-6\n" +
                "a7|-a6:a7:USUAL:a0|-a7:a8:USUAL:a0|-COMMIT_NODE|-a0|-7\n" +
                "a8|-a7:a8:USUAL:a0|-a8:a9:USUAL:a0|-COMMIT_NODE|-a0|-8\n" +
                "a9|-a8:a9:USUAL:a0|-|-COMMIT_NODE|-a0|-9"
        );
    }






    /**
     * a0
     *  |
     * a1
     *  |
     * a2
     *  |\
     *  | | a3
     *  | |  |
     *  | | a4
     *  | |  |
     *  | | a5
     *  | \ /
     *  | a6
     *  |  |
     *  | a7
     *  |  |
     *  | a8
     *  | /
     *  *  a9
     *  | /
     *  *  a10
     *  |   |
     * a11  |
     *  |   |
     * a12  |
     *  |   |
     *  |  a13
     *  |   |
     *  |  a14
     *   \ /
     *   a15
     *    |
     *   a16
     *    |
     *   a17
     */


    @Before
    public void initHardGraph() {
        hardGraph = buildGraphModel(
                "a0|-a1\n" +
                "a1|-a2\n" +
                "a2|-a11 a6\n" +
                "a3|-a4\n" +
                "a4|-a5\n" +
                "a5|-a6\n" +
                "a6|-a7\n" +
                "a7|-a8\n" +
                "a8|-a11\n" +
                "a9|-a11\n" +
                "a10|-a13\n" +
                "a11|-a12\n" +
                "a12|-a15\n" +
                "a13|-a14\n" +
                "a14|-a15\n" +
                "a15|-a16\n" +
                "a16|-a17\n" +
                "a17|-"
        );
        assertEquals("init graph",

                "a0|-|-a0:a1:USUAL:a0|-COMMIT_NODE|-a0|-0\n" +
                "a1|-a0:a1:USUAL:a0|-a1:a2:USUAL:a0|-COMMIT_NODE|-a0|-1\n" +
                "a2|-a1:a2:USUAL:a0|-a2:a11:USUAL:a2#a11 a2:a6:USUAL:a2#a6|-COMMIT_NODE|-a0|-2\n" +
                "a3|-|-a3:a4:USUAL:a3|-COMMIT_NODE|-a3|-3\n" +
                "a4|-a3:a4:USUAL:a3|-a4:a5:USUAL:a3|-COMMIT_NODE|-a3|-4\n" +
                "a5|-a4:a5:USUAL:a3|-a5:a6:USUAL:a3|-COMMIT_NODE|-a3|-5\n" +
                "a6|-a2:a6:USUAL:a2#a6 a5:a6:USUAL:a3|-a6:a7:USUAL:a2#a6|-COMMIT_NODE|-a2#a6|-6\n" +
                "a7|-a6:a7:USUAL:a2#a6|-a7:a8:USUAL:a2#a6|-COMMIT_NODE|-a2#a6|-7\n" +
                "a8|-a7:a8:USUAL:a2#a6|-a8:a11:USUAL:a2#a6|-COMMIT_NODE|-a2#a6|-8\n" +
                "a11|-a2:a11:USUAL:a2#a11 a8:a11:USUAL:a2#a6|-a11:a11:USUAL:a2#a11|-EDGE_NODE|-a2#a11|-9\n" +
                "   a9|-|-a9:a11:USUAL:a9|-COMMIT_NODE|-a9|-9\n" +
                "a10|-|-a10:a13:USUAL:a10|-COMMIT_NODE|-a10|-10\n" +
                "   a11|-a11:a11:USUAL:a2#a11 a9:a11:USUAL:a9|-a11:a11:USUAL:a2#a11|-EDGE_NODE|-a2#a11|-10\n" +
                "a11|-a11:a11:USUAL:a2#a11|-a11:a12:USUAL:a2#a11|-COMMIT_NODE|-a2#a11|-11\n" +
                "a12|-a11:a12:USUAL:a2#a11|-a12:a15:USUAL:a2#a11|-COMMIT_NODE|-a2#a11|-12\n" +
                "a13|-a10:a13:USUAL:a10|-a13:a14:USUAL:a10|-COMMIT_NODE|-a10|-13\n" +
                "a14|-a13:a14:USUAL:a10|-a14:a15:USUAL:a10|-COMMIT_NODE|-a10|-14\n" +
                "a15|-a12:a15:USUAL:a2#a11 a14:a15:USUAL:a10|-a15:a16:USUAL:a2#a11|-COMMIT_NODE|-a2#a11|-15\n" +
                "a16|-a15:a16:USUAL:a2#a11|-a16:a17:USUAL:a2#a11|-COMMIT_NODE|-a2#a11|-16\n" +
                "a17|-a16:a17:USUAL:a2#a11|-|-COMMIT_NODE|-a2#a11|-17",

                toStr(hardGraph.getGraph())
        );
    }


    private void setVisibleBranches(@NotNull GraphModel graphModel, @NotNull final  Set<String> startedNodes) {
        graphModel.setVisibleBranchesNodes(new Function<Node, Boolean>() {
            @NotNull
            @Override
            public Boolean fun(@NotNull Node key) {
                return startedNodes.contains(Integer.toHexString(key.getCommitIndex()));
            }
        });
    }


    private void runTestGraphBranchesVisibility(@NotNull GraphModel graphModel,
                                               @NotNull Set<String> startedNodes, @NotNull String outStr) {
        setVisibleBranches(graphModel, startedNodes);
        assertEquals(outStr, toStr(graphModel.getGraph()));
    }

    @Test
    public void middle1() {
        Set<String> startNodes = new HashSet<String>();
        startNodes.add("a0");

        runTestGraphBranchesVisibility(
                middleGraph,

                startNodes,

                "a0|-|-a0:a1:USUAL:a0|-COMMIT_NODE|-a0|-0\n" +
                        "a1|-a0:a1:USUAL:a0|-a1:a2:USUAL:a0|-COMMIT_NODE|-a0|-1\n" +
                        "a2|-a1:a2:USUAL:a0|-a2:a6:USUAL:a0|-COMMIT_NODE|-a0|-2\n" +
                        "a6|-a2:a6:USUAL:a0|-a6:a7:USUAL:a0|-COMMIT_NODE|-a0|-3\n" +
                        "a7|-a6:a7:USUAL:a0|-a7:a8:USUAL:a0|-COMMIT_NODE|-a0|-4\n" +
                        "a8|-a7:a8:USUAL:a0|-a8:a9:USUAL:a0|-COMMIT_NODE|-a0|-5\n" +
                        "a9|-a8:a9:USUAL:a0|-|-COMMIT_NODE|-a0|-6");

    }

    @Test
    public void middle2() {
        Set<String> startNodes = new HashSet<String>();
        startNodes.add("a1");

        runTestGraphBranchesVisibility(
                middleGraph,

                startNodes,

                "a1|-|-a1:a2:USUAL:a0|-COMMIT_NODE|-a0|-0\n" +
                "a2|-a1:a2:USUAL:a0|-a2:a6:USUAL:a0|-COMMIT_NODE|-a0|-1\n" +
                "a6|-a2:a6:USUAL:a0|-a6:a7:USUAL:a0|-COMMIT_NODE|-a0|-2\n" +
                "a7|-a6:a7:USUAL:a0|-a7:a8:USUAL:a0|-COMMIT_NODE|-a0|-3\n" +
                "a8|-a7:a8:USUAL:a0|-a8:a9:USUAL:a0|-COMMIT_NODE|-a0|-4\n" +
                "a9|-a8:a9:USUAL:a0|-|-COMMIT_NODE|-a0|-5"
        );

    }

    @Test
    public void middleTwoBranch() {
        Set<String> startNodes = new HashSet<String>();
        startNodes.add("a1");
        startNodes.add("a5");

        runTestGraphBranchesVisibility(
                middleGraph,

                startNodes,
               "a1|-|-a1:a2:USUAL:a0|-COMMIT_NODE|-a0|-0\n" +
               "a2|-a1:a2:USUAL:a0|-a2:a6:USUAL:a0|-COMMIT_NODE|-a0|-1\n" +
               "a5|-|-a5:a6:USUAL:a3|-COMMIT_NODE|-a3|-2\n" +
               "a6|-a2:a6:USUAL:a0 a5:a6:USUAL:a3|-a6:a7:USUAL:a0|-COMMIT_NODE|-a0|-3\n" +
               "a7|-a6:a7:USUAL:a0|-a7:a8:USUAL:a0|-COMMIT_NODE|-a0|-4\n" +
               "a8|-a7:a8:USUAL:a0|-a8:a9:USUAL:a0|-COMMIT_NODE|-a0|-5\n" +
               "a9|-a8:a9:USUAL:a0|-|-COMMIT_NODE|-a0|-6"
        );

    }



    //////////////Hard



    @Test
    public void hard1() {
        Set<String> startNodes = new HashSet<String>();
        startNodes.add("a0");

        runTestGraphBranchesVisibility(
                hardGraph,

                startNodes,

                "a0|-|-a0:a1:USUAL:a0|-COMMIT_NODE|-a0|-0\n" +
                "a1|-a0:a1:USUAL:a0|-a1:a2:USUAL:a0|-COMMIT_NODE|-a0|-1\n" +
                "a2|-a1:a2:USUAL:a0|-a2:a11:USUAL:a2#a11 a2:a6:USUAL:a2#a6|-COMMIT_NODE|-a0|-2\n" +
                "a6|-a2:a6:USUAL:a2#a6|-a6:a7:USUAL:a2#a6|-COMMIT_NODE|-a2#a6|-3\n" +
                "a7|-a6:a7:USUAL:a2#a6|-a7:a8:USUAL:a2#a6|-COMMIT_NODE|-a2#a6|-4\n" +
                "a8|-a7:a8:USUAL:a2#a6|-a8:a11:USUAL:a2#a6|-COMMIT_NODE|-a2#a6|-5\n" +
                "a11|-a2:a11:USUAL:a2#a11 a8:a11:USUAL:a2#a6|-a11:a11:USUAL:a2#a11|-EDGE_NODE|-a2#a11|-6\n" +
                "a11|-a11:a11:USUAL:a2#a11|-a11:a11:USUAL:a2#a11|-EDGE_NODE|-a2#a11|-7\n" +
                "a11|-a11:a11:USUAL:a2#a11|-a11:a12:USUAL:a2#a11|-COMMIT_NODE|-a2#a11|-8\n" +
                "a12|-a11:a12:USUAL:a2#a11|-a12:a15:USUAL:a2#a11|-COMMIT_NODE|-a2#a11|-9\n" +
                "a15|-a12:a15:USUAL:a2#a11|-a15:a16:USUAL:a2#a11|-COMMIT_NODE|-a2#a11|-10\n" +
                "a16|-a15:a16:USUAL:a2#a11|-a16:a17:USUAL:a2#a11|-COMMIT_NODE|-a2#a11|-11\n" +
                "a17|-a16:a17:USUAL:a2#a11|-|-COMMIT_NODE|-a2#a11|-12"
        );

    }

    @Test
    public void hardSeveralBranches() {
        Set<String> startNodes = new HashSet<String>();
        startNodes.add("a3");
        startNodes.add("a10");

        runTestGraphBranchesVisibility(
                hardGraph,

                startNodes,

                "a3|-|-a3:a4:USUAL:a3|-COMMIT_NODE|-a3|-0\n" +
                "a4|-a3:a4:USUAL:a3|-a4:a5:USUAL:a3|-COMMIT_NODE|-a3|-1\n" +
                "a5|-a4:a5:USUAL:a3|-a5:a6:USUAL:a3|-COMMIT_NODE|-a3|-2\n" +
                "a6|-a5:a6:USUAL:a3|-a6:a7:USUAL:a2#a6|-COMMIT_NODE|-a2#a6|-3\n" +
                "a7|-a6:a7:USUAL:a2#a6|-a7:a8:USUAL:a2#a6|-COMMIT_NODE|-a2#a6|-4\n" +
                "a8|-a7:a8:USUAL:a2#a6|-a8:a11:USUAL:a2#a6|-COMMIT_NODE|-a2#a6|-5\n" +
                "a11|-a8:a11:USUAL:a2#a6|-a11:a11:USUAL:a2#a11|-EDGE_NODE|-a2#a11|-6\n" +
                "a10|-|-a10:a13:USUAL:a10|-COMMIT_NODE|-a10|-7\n" +
                "   a11|-a11:a11:USUAL:a2#a11|-a11:a11:USUAL:a2#a11|-EDGE_NODE|-a2#a11|-7\n" +
                "a11|-a11:a11:USUAL:a2#a11|-a11:a12:USUAL:a2#a11|-COMMIT_NODE|-a2#a11|-8\n" +
                "a12|-a11:a12:USUAL:a2#a11|-a12:a15:USUAL:a2#a11|-COMMIT_NODE|-a2#a11|-9\n" +
                "a13|-a10:a13:USUAL:a10|-a13:a14:USUAL:a10|-COMMIT_NODE|-a10|-10\n" +
                "a14|-a13:a14:USUAL:a10|-a14:a15:USUAL:a10|-COMMIT_NODE|-a10|-11\n" +
                "a15|-a12:a15:USUAL:a2#a11 a14:a15:USUAL:a10|-a15:a16:USUAL:a2#a11|-COMMIT_NODE|-a2#a11|-12\n" +
                "a16|-a15:a16:USUAL:a2#a11|-a16:a17:USUAL:a2#a11|-COMMIT_NODE|-a2#a11|-13\n" +
                "a17|-a16:a17:USUAL:a2#a11|-|-COMMIT_NODE|-a2#a11|-14"
        );

    }

    @Test
    public void hardMiddleSelect() {
        Set<String> startNodes = new HashSet<String>();
        startNodes.add("a9");

        runTestGraphBranchesVisibility(
                hardGraph,

                startNodes,

                "a9|-|-a9:a11:USUAL:a9|-COMMIT_NODE|-a9|-0\n" +
                "a11|-a9:a11:USUAL:a9|-a11:a11:USUAL:a2#a11|-EDGE_NODE|-a2#a11|-1\n" +
                "a11|-a11:a11:USUAL:a2#a11|-a11:a12:USUAL:a2#a11|-COMMIT_NODE|-a2#a11|-2\n" +
                "a12|-a11:a12:USUAL:a2#a11|-a12:a15:USUAL:a2#a11|-COMMIT_NODE|-a2#a11|-3\n" +
                "a15|-a12:a15:USUAL:a2#a11|-a15:a16:USUAL:a2#a11|-COMMIT_NODE|-a2#a11|-4\n" +
                "a16|-a15:a16:USUAL:a2#a11|-a16:a17:USUAL:a2#a11|-COMMIT_NODE|-a2#a11|-5\n" +
                "a17|-a16:a17:USUAL:a2#a11|-|-COMMIT_NODE|-a2#a11|-6"
        );

    }



    ///------------------hideLongEdges

    @Nullable
    private GraphFragment getRelateFragment(@NotNull GraphModel graphModel, int rowIndex) {
        return graphModel.getFragmentManager().relateFragment(getCommitNode(graphModel.getGraph(), rowIndex));
    }

    @Test
    public void middleSimpleHide() {
        Set<String> startNodes = new HashSet<String>();

        FragmentManager fragmentManager = middleGraph.getFragmentManager();
        fragmentManager.showAll();

        startNodes.add("a0");
        setVisibleBranches(middleGraph, startNodes);
        fragmentManager.changeVisibility(getRelateFragment(middleGraph, 0));

        assertEquals("hide long branch",

                "a0|-|-a0:a9:HIDE_FRAGMENT:a0|-COMMIT_NODE|-a0|-0\n" +
                "a9|-a0:a9:HIDE_FRAGMENT:a0|-|-COMMIT_NODE|-a0|-1",

                toStr(middleGraph.getGraph()));

        startNodes.add("a3");
        runTestGraphBranchesVisibility(
                middleGraph,
                startNodes,
                "a0|-|-a0:a1:USUAL:a0|-COMMIT_NODE|-a0|-0\n" +
                "a1|-a0:a1:USUAL:a0|-a1:a2:USUAL:a0|-COMMIT_NODE|-a0|-1\n" +
                "a2|-a1:a2:USUAL:a0|-a2:a6:USUAL:a0|-COMMIT_NODE|-a0|-2\n" +
                "a3|-|-a3:a4:USUAL:a3|-COMMIT_NODE|-a3|-3\n" +
                "a4|-a3:a4:USUAL:a3|-a4:a5:USUAL:a3|-COMMIT_NODE|-a3|-4\n" +
                "a5|-a4:a5:USUAL:a3|-a5:a6:USUAL:a3|-COMMIT_NODE|-a3|-5\n" +
                "a6|-a2:a6:USUAL:a0 a5:a6:USUAL:a3|-a6:a7:USUAL:a0|-COMMIT_NODE|-a0|-6\n" +
                "a7|-a6:a7:USUAL:a0|-a7:a8:USUAL:a0|-COMMIT_NODE|-a0|-7\n" +
                "a8|-a7:a8:USUAL:a0|-a8:a9:USUAL:a0|-COMMIT_NODE|-a0|-8\n" +
                "a9|-a8:a9:USUAL:a0|-|-COMMIT_NODE|-a0|-9"
        );

        fragmentManager.showAll();
    }



    @Test
    public void middleHardHide() {
        Set<String> startNodes = new HashSet<String>();

        FragmentManager fragmentManager = middleGraph.getFragmentManager();
        fragmentManager.showAll();

        startNodes.add("a0");
        setVisibleBranches(middleGraph, startNodes);
        fragmentManager.changeVisibility(getRelateFragment(middleGraph, 0));

        assertEquals("hide long branch",

                "a0|-|-a0:a9:HIDE_FRAGMENT:a0|-COMMIT_NODE|-a0|-0\n" +
                "a9|-a0:a9:HIDE_FRAGMENT:a0|-|-COMMIT_NODE|-a0|-1",

                toStr(middleGraph.getGraph()));

        startNodes.clear();
        startNodes.add("a3");


        runTestGraphBranchesVisibility(
                middleGraph,
                startNodes,
                "a3|-|-a3:a4:USUAL:a3|-COMMIT_NODE|-a3|-0\n" +
                "a4|-a3:a4:USUAL:a3|-a4:a5:USUAL:a3|-COMMIT_NODE|-a3|-1\n" +
                "a5|-a4:a5:USUAL:a3|-a5:a6:USUAL:a3|-COMMIT_NODE|-a3|-2\n" +
                "a6|-a5:a6:USUAL:a3|-a6:a7:USUAL:a0|-COMMIT_NODE|-a0|-3\n" +
                "a7|-a6:a7:USUAL:a0|-a7:a8:USUAL:a0|-COMMIT_NODE|-a0|-4\n" +
                "a8|-a7:a8:USUAL:a0|-a8:a9:USUAL:a0|-COMMIT_NODE|-a0|-5\n" +
                "a9|-a8:a9:USUAL:a0|-|-COMMIT_NODE|-a0|-6"
        );

        fragmentManager.changeVisibility(getRelateFragment(middleGraph, 0));

        startNodes.add("a0");
        runTestGraphBranchesVisibility(
                middleGraph,
                startNodes,
                "a0|-|-a0:a1:USUAL:a0|-COMMIT_NODE|-a0|-0\n" +
                "a1|-a0:a1:USUAL:a0|-a1:a2:USUAL:a0|-COMMIT_NODE|-a0|-1\n" +
                "a2|-a1:a2:USUAL:a0|-a2:a6:USUAL:a0|-COMMIT_NODE|-a0|-2\n" +
                "a3|-|-a3:a4:USUAL:a3|-COMMIT_NODE|-a3|-3\n" +
                "a4|-a3:a4:USUAL:a3|-a4:a5:USUAL:a3|-COMMIT_NODE|-a3|-4\n" +
                "a5|-a4:a5:USUAL:a3|-a5:a6:USUAL:a3|-COMMIT_NODE|-a3|-5\n" +
                "a6|-a2:a6:USUAL:a0 a5:a6:USUAL:a3|-a6:a7:USUAL:a0|-COMMIT_NODE|-a0|-6\n" +
                "a7|-a6:a7:USUAL:a0|-a7:a8:USUAL:a0|-COMMIT_NODE|-a0|-7\n" +
                "a8|-a7:a8:USUAL:a0|-a8:a9:USUAL:a0|-COMMIT_NODE|-a0|-8\n" +
                "a9|-a8:a9:USUAL:a0|-|-COMMIT_NODE|-a0|-9"
        );


        fragmentManager.showAll();
    }

    @Test
    public void hardHardHideTest() {
        Set<String> startNodes = new HashSet<String>();
        FragmentManager fragmentManager = hardGraph.getFragmentManager();
        fragmentManager.showAll();

        startNodes.add("a3");
        startNodes.add("a9");
        startNodes.add("a10");
        setVisibleBranches(hardGraph, startNodes);

        fragmentManager.changeVisibility(getRelateFragment(hardGraph, 0));

        assertEquals("first hide",

                "a3|-|-a3:a8:HIDE_FRAGMENT:a3|-COMMIT_NODE|-a3|-0\n" +
                "a8|-a3:a8:HIDE_FRAGMENT:a3|-a8:a11:USUAL:a2#a6|-COMMIT_NODE|-a2#a6|-1\n" +
                "a11|-a8:a11:USUAL:a2#a6|-a11:a11:USUAL:a2#a11|-EDGE_NODE|-a2#a11|-2\n" +
                "   a9|-|-a9:a11:USUAL:a9|-COMMIT_NODE|-a9|-2\n" +
                "a10|-|-a10:a13:USUAL:a10|-COMMIT_NODE|-a10|-3\n" +
                "   a11|-a11:a11:USUAL:a2#a11 a9:a11:USUAL:a9|-a11:a11:USUAL:a2#a11|-EDGE_NODE|-a2#a11|-3\n" +
                "a11|-a11:a11:USUAL:a2#a11|-a11:a12:USUAL:a2#a11|-COMMIT_NODE|-a2#a11|-4\n" +
                "a12|-a11:a12:USUAL:a2#a11|-a12:a15:USUAL:a2#a11|-COMMIT_NODE|-a2#a11|-5\n" +
                "a13|-a10:a13:USUAL:a10|-a13:a14:USUAL:a10|-COMMIT_NODE|-a10|-6\n" +
                "a14|-a13:a14:USUAL:a10|-a14:a15:USUAL:a10|-COMMIT_NODE|-a10|-7\n" +
                "a15|-a12:a15:USUAL:a2#a11 a14:a15:USUAL:a10|-a15:a16:USUAL:a2#a11|-COMMIT_NODE|-a2#a11|-8\n" +
                "a16|-a15:a16:USUAL:a2#a11|-a16:a17:USUAL:a2#a11|-COMMIT_NODE|-a2#a11|-9\n" +
                "a17|-a16:a17:USUAL:a2#a11|-|-COMMIT_NODE|-a2#a11|-10",

                toStr(hardGraph.getGraph())
        );

        startNodes.remove("a9");
        setVisibleBranches(hardGraph, startNodes);

        fragmentManager.changeVisibility(getRelateFragment(hardGraph, 4));
        assertEquals("second hide",

                "a3|-|-a3:a12:HIDE_FRAGMENT:a3|-COMMIT_NODE|-a3|-0\n" +
                "a10|-|-a10:a13:USUAL:a10|-COMMIT_NODE|-a10|-1\n" +
                "a12|-a3:a12:HIDE_FRAGMENT:a3|-a12:a15:USUAL:a2#a11|-COMMIT_NODE|-a2#a11|-2\n" +
                "a13|-a10:a13:USUAL:a10|-a13:a14:USUAL:a10|-COMMIT_NODE|-a10|-3\n" +
                "a14|-a13:a14:USUAL:a10|-a14:a15:USUAL:a10|-COMMIT_NODE|-a10|-4\n" +
                "a15|-a12:a15:USUAL:a2#a11 a14:a15:USUAL:a10|-a15:a16:USUAL:a2#a11|-COMMIT_NODE|-a2#a11|-5\n" +
                "a16|-a15:a16:USUAL:a2#a11|-a16:a17:USUAL:a2#a11|-COMMIT_NODE|-a2#a11|-6\n" +
                "a17|-a16:a17:USUAL:a2#a11|-|-COMMIT_NODE|-a2#a11|-7",

                toStr(hardGraph.getGraph())
        );

        startNodes.remove("a10");
        setVisibleBranches(hardGraph, startNodes);
        fragmentManager.changeVisibility(getRelateFragment(hardGraph, 2));
        assertEquals("last hide",

                "a3|-|-a3:a17:HIDE_FRAGMENT:a3|-COMMIT_NODE|-a3|-0\n" +
                "a17|-a3:a17:HIDE_FRAGMENT:a3|-|-COMMIT_NODE|-a2#a11|-1",

                toStr(hardGraph.getGraph())
        );


        startNodes.clear();
        startNodes.add("a0");
        setVisibleBranches(hardGraph, startNodes);

        assertEquals("show a0",

                "a0|-|-a0:a1:USUAL:a0|-COMMIT_NODE|-a0|-0\n" +
                "a1|-a0:a1:USUAL:a0|-a1:a2:USUAL:a0|-COMMIT_NODE|-a0|-1\n" +
                "a2|-a1:a2:USUAL:a0|-a2:a11:USUAL:a2#a11 a2:a6:USUAL:a2#a6|-COMMIT_NODE|-a0|-2\n" +
                "a6|-a2:a6:USUAL:a2#a6|-a6:a7:USUAL:a2#a6|-COMMIT_NODE|-a2#a6|-3\n" +
                "a7|-a6:a7:USUAL:a2#a6|-a7:a8:USUAL:a2#a6|-COMMIT_NODE|-a2#a6|-4\n" +
                "a8|-a7:a8:USUAL:a2#a6|-a8:a11:USUAL:a2#a6|-COMMIT_NODE|-a2#a6|-5\n" +
                "a11|-a2:a11:USUAL:a2#a11 a8:a11:USUAL:a2#a6|-a11:a11:USUAL:a2#a11|-EDGE_NODE|-a2#a11|-6\n" +
                "a11|-a11:a11:USUAL:a2#a11|-a11:a11:USUAL:a2#a11|-EDGE_NODE|-a2#a11|-7\n" +
                "a11|-a11:a11:USUAL:a2#a11|-a11:a12:USUAL:a2#a11|-COMMIT_NODE|-a2#a11|-8\n" +
                "a12|-a11:a12:USUAL:a2#a11|-a12:a15:USUAL:a2#a11|-COMMIT_NODE|-a2#a11|-9\n" +
                "a15|-a12:a15:USUAL:a2#a11|-a15:a16:USUAL:a2#a11|-COMMIT_NODE|-a2#a11|-10\n" +
                "a16|-a15:a16:USUAL:a2#a11|-a16:a17:USUAL:a2#a11|-COMMIT_NODE|-a2#a11|-11\n" +
                "a17|-a16:a17:USUAL:a2#a11|-|-COMMIT_NODE|-a2#a11|-12",

                toStr(hardGraph.getGraph())
        );




        fragmentManager.showAll();
    }

}
