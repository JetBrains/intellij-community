package com.intellij.vcs.log.graph.render;

import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.printmodel.GraphPrintCell;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;


/**
 * @author erokhins
 */
public class GraphCommitCell extends CommitCell {

  private final GraphPrintCell row;

  public GraphCommitCell(@NotNull GraphPrintCell row, @NotNull String text,
                         @NotNull Collection<VcsRef> refsToThisCommit) {
    super(text, refsToThisCommit);
    this.row = row;
  }

  public GraphPrintCell getPrintCell() {
    return row;
  }
}
