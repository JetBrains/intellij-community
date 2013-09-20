package com.intellij.vcs.log.graphmodel.fragment;

import com.intellij.util.Function;
import com.intellij.vcs.log.compressedlist.UpdateRequest;
import com.intellij.vcs.log.graph.GraphTestUtils;
import com.intellij.vcs.log.graph.elements.Node;
import com.intellij.vcs.log.graph.mutable.MutableGraph;
import com.intellij.vcs.log.graphmodel.FragmentManager;
import com.intellij.vcs.log.graphmodel.GraphFragment;
import com.intellij.vcs.log.graphmodel.impl.GraphDecoratorImpl;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static com.intellij.vcs.log.graph.GraphStrUtils.toStr;
import static com.intellij.vcs.log.graph.GraphTestUtils.getCommitNode;
import static com.intellij.vcs.log.graphmodel.fragment.GraphModelUtils.toStr;

/**
 * @author erokhins
 */
public class FragmentManagerTest {

    public void runTest(String inputGraph, int hideNodeRowIndex, String fragmentStr, int showNodeRowIndex, String hideGraphStr) {
        final MutableGraph graph = GraphTestUtils.getNewMutableGraph(inputGraph);
        FragmentManager fragmentManager = new FragmentManagerImpl(graph, new FragmentManagerImpl.CallBackFunction() {
            @Override
            public UpdateRequest runIntermediateUpdate(@NotNull Node upNode, @NotNull Node downNode) {
                graph.updateVisibleRows();
                return UpdateRequest.ID_UpdateRequest;
            }

            @Override
            public void fullUpdate() {
                graph.updateVisibleRows();
            }
        });
        graph.setGraphDecorator(new GraphDecoratorImpl(fragmentManager.getGraphPreDecorator(), new Function<Node, Boolean>() {
            @NotNull
            @Override
            public Boolean fun(@NotNull Node key) {
                return true;
            }
        }));

        Node node = getCommitNode(graph, hideNodeRowIndex);
        GraphFragment fragment = fragmentManager.relateFragment(node);
        assertEquals(fragmentStr, toStr(fragment));

        if (fragment == null) {
            return;
        }

        String saveGraphStr = toStr(graph);

        fragmentManager.changeVisibility(fragment);
        assertEquals(hideGraphStr, toStr(graph));

        fragment = fragmentManager.relateFragment(getCommitNode(graph, showNodeRowIndex));
        assertNotNull(fragment);

        fragmentManager.changeVisibility(fragment);
        assertEquals(saveGraphStr, toStr(graph));
    }

    @Test
    public void simple1() {
        runTest("a0|-a1\n" +
                "a1|-a2\n" +
                "a2|-",
                1,

                "a0:0|-a1:1|-a2:2",

                0,

                "a0|-|-a0:a2:HIDE_FRAGMENT:a0|-COMMIT_NODE|-a0|-0\n" +
                "a2|-a0:a2:HIDE_FRAGMENT:a0|-|-COMMIT_NODE|-a0|-1"
        );
    }

    @Test
    public void simple_null_fragment() {
        runTest("a0|-a1\n" +
                "a1|-",
                1,

                "null",

                0,

                ""
        );
    }

    @Test
    public void simple_with_notFullGraph() {
        runTest("a0|-a1\n" +
                "a1|-a2",
                1,

                "null",

                0,

                ""
        );
    }

    @Test
    public void difficultGraph1() {
        runTest("a0|-a1 a4\n" +
                "a1|-a2 a3\n" +
                "a2|-a3\n" +
                "a3|-a4\n" +
                "a4|-",
                2,

                "a1:1|-a2:2|-a3:3",

                1,

                "a0|-|-a0:a1:USUAL:a0#a1 a0:a4:USUAL:a0#a4|-COMMIT_NODE|-a0|-0\n" +
                "a1|-a0:a1:USUAL:a0#a1|-a1:a3:HIDE_FRAGMENT:a0#a1|-COMMIT_NODE|-a0#a1|-1\n" +
                "a3|-a1:a3:HIDE_FRAGMENT:a0#a1|-a3:a4:USUAL:a1#a3|-COMMIT_NODE|-a1#a3|-2\n" +
                "a4|-a0:a4:USUAL:a0#a4 a3:a4:USUAL:a1#a3|-|-COMMIT_NODE|-a0#a4|-3"
        );
    }

    @Test
    public void difficultGraph2() {
        runTest("a0|-a1 a4\n" +
                "a1|-a2 a3\n" +
                "a2|-a3\n" +
                "a3|-a4\n" +
                "a4|-",
                4,

                "a0:0|-a1:1 a2:2 a3:3|-a4:4",

                1,

                "a0|-|-a0:a4:HIDE_FRAGMENT:a0|-COMMIT_NODE|-a0|-0\n" +
                "a4|-a0:a4:HIDE_FRAGMENT:a0|-|-COMMIT_NODE|-a0#a4|-1"
        );
    }

}
