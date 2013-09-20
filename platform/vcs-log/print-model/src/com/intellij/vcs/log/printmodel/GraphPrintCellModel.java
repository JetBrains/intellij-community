package com.intellij.vcs.log.printmodel;

import com.intellij.vcs.log.compressedlist.UpdateRequest;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public interface GraphPrintCellModel {

  boolean HIDE_LONG_EDGES_DEFAULT = true;

  @NotNull
  public GraphPrintCell getGraphPrintCell(final int rowIndex);

  @NotNull
  public SelectController getSelectController();

  @NotNull
  public CommitSelectController getCommitSelectController();

  public void recalculate(@NotNull UpdateRequest updateRequest);

  public void setLongEdgeVisibility(boolean visibility);

  boolean areLongEdgesHidden();
}
