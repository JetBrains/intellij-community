package org.hanuna.gitalk.graphmodel.impl;

import org.hanuna.gitalk.common.Function;
import org.hanuna.gitalk.common.OneElementList;
import org.hanuna.gitalk.graph.elements.Edge;
import org.hanuna.gitalk.graph.elements.Node;
import org.hanuna.gitalk.graph.mutable.GraphDecorator;
import org.hanuna.gitalk.graph.mutable.elements.MutableNode;
import org.hanuna.gitalk.graphmodel.FragmentManager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author erokhins
 */

public class GraphDecoratorImpl implements GraphDecorator {
    private final FragmentManager.GraphPreDecorator preDecorator;
    private final Function<Node, Boolean> branchVisibleNodes;

    public GraphDecoratorImpl(FragmentManager.GraphPreDecorator preDecorator, Function<Node, Boolean> branchVisibleNodes) {
        this.preDecorator = preDecorator;
        this.branchVisibleNodes = branchVisibleNodes;
    }

    @Override
    public boolean isVisibleNode(@NotNull Node node) {
        return preDecorator.isVisibleNode(node) && branchVisibleNodes.get(node);
    }

    @NotNull
    @Override
    public List<Edge> getDownEdges(@NotNull Node node, @NotNull List<Edge> innerDownEdges) {
        Edge edge = preDecorator.getHideFragmentDownEdge(node);
        if (edge != null) {
            return OneElementList.buildList(edge);
        } else {
            return Collections.unmodifiableList(innerDownEdges);
        }
    }

    @NotNull
    @Override
    public List<Edge> getUpEdges(@NotNull Node node, @NotNull List<Edge> innerUpEdges) {
        Edge hideFragmentUpEdge = preDecorator.getHideFragmentUpEdge(node);
        if (hideFragmentUpEdge != null) {
            return OneElementList.buildList(hideFragmentUpEdge);
        }

        List<Edge> edges = new ArrayList<Edge>();
        for (Edge edge : innerUpEdges) {
            if (isVisibleNode(edge.getUpNode())) {
                edges.add(edge);
            }
        }
        return edges;
    }
}
