package org.hanuna.gitalk.graph.mutable;

import org.hanuna.gitalk.graph.elements.Edge;
import org.hanuna.gitalk.graph.elements.Node;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author erokhins
 */
public interface GraphDecorator {

  public boolean isVisibleNode(@NotNull Node node);

  @NotNull
  public List<Edge> getDownEdges(@NotNull Node node, @NotNull List<Edge> innerDownEdges);

  @NotNull
  public List<Edge> getUpEdges(@NotNull Node node, @NotNull List<Edge> innerUpEdges);
}
