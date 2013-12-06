package com.intellij.vcs.log.graphmodel.fragment;

import com.intellij.util.Function;
import com.intellij.vcs.log.graph.Graph;
import com.intellij.vcs.log.graph.elements.Node;
import com.intellij.vcs.log.graphmodel.GraphFragment;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.vcs.log.graph.GraphTestUtils.getCommitNode;
import static com.intellij.vcs.log.graph.GraphTestUtils.parseIntegers;

/**
 * @author erokhins
 */
public class GraphModelUtils {
    public static String toShortStr(@NotNull Node node) {
        return Integer.toHexString(node.getCommitIndex()) + ":" + node.getRowIndex();
    }


    public static String toStr(@Nullable GraphFragment fragment) {
        if (fragment == null) {
            return "null";
        }
        StringBuilder s = new StringBuilder();
        s.append(toShortStr(fragment.getUpNode())).append("|-");

        List<String> intermediateNodeStr = new ArrayList<String>();
        for (Node intermediateNode : fragment.getIntermediateNodes()) {
            intermediateNodeStr.add(toShortStr(intermediateNode));
        }
        Collections.sort(intermediateNodeStr);
        for (int i = 0; i < intermediateNodeStr.size(); i++) {
            if (i > 0) {
                s.append(" ");
            }
            s.append(intermediateNodeStr.get(i));
        }
        s.append("|-").append(toShortStr(fragment.getDownNode()));
        return s.toString();
    }

    public static Function<Node, Boolean> parseUnhiddenNodes(Graph graph, String unhiddenNodeRows) {
        Set<Integer> unhiddenNodesRowIndex = parseIntegers(unhiddenNodeRows);
        final Set<Node> unhiddenNodes = new HashSet<Node>();
        for (Integer i : unhiddenNodesRowIndex) {
            unhiddenNodes.add(getCommitNode(graph, i));
        }
        return new Function<Node, Boolean>() {
            @NotNull
            @Override
            public Boolean fun(@NotNull Node key) {
                return unhiddenNodes.contains(key);
            }
        };
    }
}
