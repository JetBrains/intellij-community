package org.hanuna.gitalk.graphmodel.fragment;

import com.intellij.util.Function;
import org.hanuna.gitalk.common.compressedlist.UpdateRequest;
import org.hanuna.gitalk.graph.GraphTestUtils;
import org.hanuna.gitalk.graph.elements.Node;
import org.hanuna.gitalk.graph.mutable.MutableGraph;
import org.hanuna.gitalk.graphmodel.FragmentManager;
import org.hanuna.gitalk.graphmodel.GraphFragment;
import org.hanuna.gitalk.graphmodel.impl.GraphDecoratorImpl;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static org.hanuna.gitalk.graph.GraphStrUtils.toStr;
import static org.hanuna.gitalk.graph.GraphTestUtils.getCommitNode;
import static org.hanuna.gitalk.graphmodel.fragment.GraphModelUtils.toStr;

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
