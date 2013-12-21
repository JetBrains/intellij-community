package com.intellij.vcs.log.ui.tables;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesTreeBrowser;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.VcsShortCommitDetails;
import com.intellij.vcs.log.data.*;
import com.intellij.vcs.log.graph.elements.Node;
import com.intellij.vcs.log.graph.render.GraphCommitCell;
import com.intellij.vcs.log.graph.render.PositionUtil;
import com.intellij.vcs.log.printmodel.GraphPrintCell;
import com.intellij.vcs.log.ui.VcsLogUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
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
  @NotNull private final NodeAroundProvider myNodeAroundProvider;

  public GraphTableModel(@NotNull VcsLogDataHolder dataHolder, @NotNull VcsLogUI ui) {
    myDataHolder = dataHolder;
    myUi = ui;
    myDataPack = dataHolder.getDataPack();
    myNodeAroundProvider = new NodeAroundProvider(myDataPack, dataHolder);
  }

  @Override
  public int getRowCount() {
    return myDataPack.getGraphModel().getGraph().getNodeRows().size();
  }

  @Nullable
  @Override
  protected VcsShortCommitDetails getShortDetails(int rowIndex) {
    Node commitNode = myDataPack.getGraphModel().getGraph().getCommitNodeInRow(rowIndex);
    return commitNode == null ? null : myDataHolder.getMiniDetailsGetter().getCommitData(commitNode, myNodeAroundProvider);
  }

  @Nullable
  @Override
  public VcsFullCommitDetails getFullCommitDetails(int row) {
    Node node = myDataPack.getGraphModel().getGraph().getCommitNodeInRow(row);
    return node == null ? null : myDataHolder.getCommitDetailsGetter().getCommitData(node, myNodeAroundProvider);
  }

  @Override
  public void requestToLoadMore() {
    myDataHolder.showFullLog(EmptyRunnable.INSTANCE);
  }

  @Nullable
  @Override
  public List<Change> getSelectedChanges(int[] selectedRows) {
    List<Change> changes = new ArrayList<Change>();
    for (Node node : nodes(selectedRows)) {
      VcsFullCommitDetails commitData = myDataHolder.getCommitDetailsGetter().getCommitData(node, myNodeAroundProvider);
      if (commitData instanceof LoadingDetails) {
        return null;
      }
      changes.addAll(commitData.getChanges());
    }
    return CommittedChangesTreeBrowser.zipChanges(changes);
  }

  @NotNull
  private List<Node> nodes(int[] selectedRows) {
    List<Node> result = new ArrayList<Node>();
    Arrays.sort(selectedRows);
    for (int rowIndex : selectedRows) {
      Node node = PositionUtil.getNode(getGraphPrintCellForRow(rowIndex));
      if (node != null) {
        result.add(node);
      }
    }
    return result;
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
  protected VirtualFile getRoot(int rowIndex) {
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
    Node node = getCommit(row);
    return node == null ? null : myDataHolder.getHash(node.getCommitIndex());
  }

  @Nullable
  @Override
  public Node getCommit(int row) {
    return myDataPack.getGraphModel().getGraph().getCommitNodeInRow(row);
  }

  @NotNull
  @Override
  public AroundProvider<Node> getAroundProvider() {
    return myNodeAroundProvider;
  }

}
