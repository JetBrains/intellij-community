package com.intellij.vcs.log.printmodel.layout;

import com.intellij.vcs.log.graph.elements.GraphElement;
import com.intellij.vcs.log.graph.elements.NodeRow;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author erokhins
 */
public interface LayoutRow {
  @NotNull
  public List<GraphElement> getOrderedGraphElements();

  public NodeRow getGraphNodeRow();
}
