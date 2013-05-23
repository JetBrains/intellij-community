package org.hanuna.gitalk.graphmodel;

import org.hanuna.gitalk.common.Function;
import org.hanuna.gitalk.common.compressedlist.UpdateRequest;
import org.hanuna.gitalk.graph.elements.Edge;
import org.hanuna.gitalk.graph.elements.GraphElement;
import org.hanuna.gitalk.graph.elements.Node;
import org.hanuna.gitalk.graph.mutable.GraphDecorator;
import org.hanuna.gitalk.graphmodel.fragment.GraphFragmentController;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author erokhins
 */
public interface FragmentManager {

    @Nullable
    public GraphFragment relateFragment(@NotNull GraphElement graphElement);

    @NotNull
    public UpdateRequest changeVisibility(@NotNull GraphFragment fragment);

    //true, if node is unconcealedNode
    public void setUnconcealedNodeFunction(@NotNull Function<Node, Boolean> isUnconcealedNode);

    void hideAll();

    void showAll();

    @NotNull
    public GraphPreDecorator getGraphPreDecorator();

    public interface GraphPreDecorator {
        public boolean isVisibleNode(@NotNull Node node);

        @Nullable
        public Edge getHideFragmentUpEdge(@NotNull Node node);

        @Nullable
        public Edge getHideFragmentDownEdge(@NotNull Node node);
    }
}
