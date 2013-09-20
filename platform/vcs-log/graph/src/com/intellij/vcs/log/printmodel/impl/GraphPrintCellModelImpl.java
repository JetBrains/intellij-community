package com.intellij.vcs.log.printmodel.impl;

import com.intellij.vcs.log.compressedlist.UpdateRequest;
import com.intellij.vcs.log.graph.Graph;
import com.intellij.vcs.log.printmodel.*;
import com.intellij.vcs.log.printmodel.layout.LayoutModel;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author erokhins
 */
public class GraphPrintCellModelImpl implements GraphPrintCellModel {
  private final LayoutModel layoutModel;
  private final SelectController selectController;
  private boolean hideLongEdges = HIDE_LONG_EDGES_DEFAULT;
  private final CommitSelectController commitSelectController;

  public GraphPrintCellModelImpl(Graph graph) {
    this.layoutModel = new LayoutModel(graph);
    this.selectController = new SelectController();
    this.commitSelectController = new CommitSelectController();
  }

  private List<ShortEdge> getUpEdges(int rowIndex) {
    PrePrintCellModel prevPreModel = new PrePrintCellModel(hideLongEdges, layoutModel, rowIndex - 1, selectController,
                                                           commitSelectController);
    return prevPreModel.downShortEdges();
  }

  public void recalculate(@NotNull UpdateRequest updateRequest) {
    layoutModel.recalculate(updateRequest);
  }

  @Override
  public void setLongEdgeVisibility(boolean visibility) {
    hideLongEdges = !visibility;
  }

  @Override
  public boolean areLongEdgesHidden() {
    return hideLongEdges;
  }

  @NotNull
  public SelectController getSelectController() {
    return selectController;
  }

  @NotNull
  public CommitSelectController getCommitSelectController() {
    return commitSelectController;
  }

  @NotNull
  public GraphPrintCell getGraphPrintCell(final int rowIndex) {
    final PrePrintCellModel prePrintCellModel = new PrePrintCellModel(hideLongEdges, layoutModel, rowIndex, selectController,
                                                                      commitSelectController);

    return new GraphPrintCell() {
      @Override
      public int countCell() {
        return prePrintCellModel.getCountCells();
      }

      @NotNull
      @Override
      public List<ShortEdge> getUpEdges() {
        return GraphPrintCellModelImpl.this.getUpEdges(rowIndex);
      }

      @NotNull
      @Override
      public List<ShortEdge> getDownEdges() {
        return prePrintCellModel.downShortEdges();
      }

      @NotNull
      @Override
      public List<SpecialPrintElement> getSpecialPrintElements() {
        return prePrintCellModel.getSpecialPrintElements();
      }
    };
  }
}
