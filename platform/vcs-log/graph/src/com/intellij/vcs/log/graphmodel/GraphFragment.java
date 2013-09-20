package com.intellij.vcs.log.graphmodel;

import com.intellij.vcs.log.graph.elements.Node;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author erokhins
 */
public interface GraphFragment {

  @NotNull
  public Node getUpNode();

  @NotNull
  public Node getDownNode();

  @NotNull
  public Collection<Node> getIntermediateNodes();
}
