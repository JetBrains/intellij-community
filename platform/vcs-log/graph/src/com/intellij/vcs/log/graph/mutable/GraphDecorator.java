package com.intellij.vcs.log.graph.mutable;

import com.intellij.vcs.log.graph.elements.Edge;
import com.intellij.vcs.log.graph.elements.Node;
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
