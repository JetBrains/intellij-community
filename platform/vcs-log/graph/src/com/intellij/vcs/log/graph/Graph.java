package com.intellij.vcs.log.graph;

import com.intellij.vcs.log.graph.elements.Node;
import com.intellij.vcs.log.graph.elements.NodeRow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author erokhins
 */
public interface Graph {

  @NotNull
  List<NodeRow> getNodeRows();

  @Nullable
  Node getCommitNodeInRow(int rowIndex);
}
