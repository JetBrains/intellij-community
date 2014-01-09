package com.intellij.vcs.log.ui.tables;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.VcsShortCommitDetails;
import com.intellij.vcs.log.data.DataPack;
import com.intellij.vcs.log.data.LoadingDetails;
import com.intellij.vcs.log.data.VcsLogDataHolder;
import com.intellij.vcs.log.graph.elements.Node;
import com.intellij.vcs.log.graph.render.GraphCommitCell;
import com.intellij.vcs.log.printmodel.GraphPrintCell;
import com.intellij.vcs.log.ui.VcsLogUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Kirill Likhodedov
 */
public class GraphTableModel extends AbstractVcsLogTableModel<GraphCommitCell, Node> {

  private static final Logger LOG = Logger.getInstance(GraphTableModel.class);

  @NotNull private final DataPack myDataPack;
  @NotNull private final VcsLogDataHolder myDataHolder;
  @NotNull private final VcsLogUI myUi;

  public GraphTableModel(@NotNull VcsLogDataHolder dataHolder, @NotNull VcsLogUI ui) {
    myDataHolder = dataHolder;
    myUi = ui;
    myDataPack = dataHolder.getDataPack();
  }

  @Override
  public int getRowCount() {
    return myDataPack.getGraphModel().getGraph().getNodeRows().size();
  }

  @Nullable
  @Override
  protected VcsShortCommitDetails getShortDetails(int rowIndex) {
    return myDataHolder.getMiniDetailsGetter().getCommitData(rowIndex, this);
  }

  @Nullable
  @Override
  public VcsFullCommitDetails getFullCommitDetails(int row) {
    return myDataHolder.getCommitDetailsGetter().getCommitData(row, this);
  }

  @Override
  public void requestToLoadMore() {
    myDataHolder.showFullLog(EmptyRunnable.INSTANCE);
  }

  @Nullable
  @Override
  public List<Change> getSelectedChanges(@NotNull List<Integer> selectedRows) {
    List<Change> changes = new ArrayList<Change>();
    for (int row : selectedRows) {
      VcsFullCommitDetails commitData = myDataHolder.getCommitDetailsGetter().getCommitData(row, this);
      if (commitData == null || commitData instanceof LoadingDetails) {
        return null;
      }
      changes.addAll(commitData.getChanges());
    }
    return changes;
  }

  @Nullable
  private GraphPrintCell getGraphPrintCellForRow(int row) {
    Object commitValue = getValueAt(row, AbstractVcsLogTableModel.COMMIT_COLUMN);
    if (commitValue instanceof GraphCommitCell) {
      GraphCommitCell commitCell = (GraphCommitCell)commitValue;
      return commitCell.getPrintCell();
    }
    return null;
  }

  @NotNull
  @Override
  public VirtualFile getRoot(int rowIndex) {
    Node commitNode = myDataPack.getGraphModel().getGraph().getCommitNodeInRow(rowIndex);
    return commitNode != null ? commitNode.getBranch().getRepositoryRoot() : FAKE_ROOT;
  }

  @NotNull
  @Override
  protected GraphCommitCell getCommitColumnCell(int rowIndex, @Nullable VcsShortCommitDetails details) {
    GraphPrintCell graphPrintCell = myDataPack.getPrintCellModel().getGraphPrintCell(rowIndex);
    String message = "";
    List<VcsRef> refs = Collections.emptyList();
    Hash hash = null;
    if (details != null) {
      hash = details.getHash();
      message = details.getSubject();
      refs = (List<VcsRef>)myDataPack.getRefsModel().refsToCommit(details.getHash());
    }
    return new GraphCommitCell(graphPrintCell, message, refs);
  }

  @NotNull
  @Override
  protected Class<GraphCommitCell> getCommitColumnClass() {
    return GraphCommitCell.class;
  }

  @Nullable
  @Override
  public Hash getHashAtRow(int row) {
    Node node = myDataPack.getGraphModel().getGraph().getCommitNodeInRow(row);
    return node == null ? null : myDataHolder.getHash(node.getCommitIndex());
  }

}
