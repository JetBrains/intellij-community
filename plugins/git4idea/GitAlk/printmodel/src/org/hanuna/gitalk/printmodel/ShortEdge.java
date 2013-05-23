package org.hanuna.gitalk.printmodel;

import org.hanuna.gitalk.graph.elements.Edge;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public class ShortEdge {
    private final Edge edge;
    private final int upPosition;
    private final int downPosition;
    private final boolean selected;

    public ShortEdge(@NotNull Edge edge, int upPosition, int downPosition, boolean selected) {
        this.edge = edge;
        this.upPosition = upPosition;
        this.downPosition = downPosition;
        this.selected = selected;
    }

    @NotNull
    public Edge getEdge() {
        return edge;
    }

    public boolean isUsual() {
        return edge.getType() == Edge.EdgeType.USUAL;
    }

    public int getUpPosition() {
        return upPosition;
    }

    public boolean isSelected() {
        return selected;
    }

    public int getDownPosition() {
        return downPosition;
    }
}
