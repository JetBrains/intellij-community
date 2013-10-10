package com.intellij.vcs.log.ui.tables;

import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesTreeBrowser;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.VcsShortCommitDetails;
import com.intellij.vcs.log.data.DataPack;
import com.intellij.vcs.log.data.LoadingDetails;
import com.intellij.vcs.log.data.VcsLogDataHolder;
import com.intellij.vcs.log.graph.elements.Node;
import com.intellij.vcs.log.graph.render.GraphCommitCell;
import com.intellij.vcs.log.graph.render.PositionUtil;
import com.intellij.vcs.log.printmodel.GraphPrintCell;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.table.AbstractTableModel;
import java.util.*;

/**
 * @author erokhins
 */
public class GraphTableModel extends AbstractTableModel {

  public static final int ROOT_COLUMN = 0;
  public static final int COMMIT_COLUMN = 1;
  public static final int AUTHOR_COLUMN = 2;
  public static final int DATE_COLUMN = 3;
  private static final int COLUMN_COUNT = DATE_COLUMN + 1;

  private static final String[] COLUMN_NAMES = {"Root", "Subject", "Author", "Date"};

  @NotNull private final DataPack myDataPack;
  @NotNull private final VcsLogDataHolder myDataHolder;

  public GraphTableModel(@NotNull VcsLogDataHolder dataHolder) {
    myDataHolder = dataHolder;
    myDataPack = dataHolder.getDataPack();
  }

  @Override
  public int getRowCount() {
    return myDataPack.getGraphModel().getGraph().getNodeRows().size();
  }

  @Override
  public int getColumnCount() {
    return COLUMN_COUNT;
  }

  @Override
  public Object getValueAt(int rowIndex, int columnIndex) {
    Node commitNode = myDataPack.getGraphModel().getGraph().getCommitNodeInRow(rowIndex);
    VcsShortCommitDetails data;
    if (commitNode == null) {
      data = null;
    }
    else {
      data = myDataHolder.getMiniDetailsGetter().getCommitData(commitNode);
    }
    switch (columnIndex) {
      case ROOT_COLUMN:
        if (commitNode != null) {
          return commitNode.getBranch().getRepositoryRoot();
        }
        else {
          return null;
        }
      case COMMIT_COLUMN:
        GraphPrintCell graphPrintCell = myDataPack.getPrintCellModel().getGraphPrintCell(rowIndex);
        String message = "";
        Collection<VcsRef> refs = Collections.emptyList();
        if (data != null) {
          message = data.getSubject();
          refs = myDataPack.getRefsModel().refsToCommit(data.getHash());
        }
        return new GraphCommitCell(graphPrintCell, message, refs);
      case AUTHOR_COLUMN:
        if (data == null) {
          return "";
        }
        else {
          return data.getAuthorName();
        }
      case DATE_COLUMN:
        if (data == null) {
          return "";
        }
        else {
          return DateFormatUtil.formatDateTime(data.getAuthorTime());
        }
      default:
        throw new IllegalArgumentException("columnIndex is " + columnIndex + " > " + (COLUMN_COUNT - 1));
    }
  }

  @Override
  public Class<?> getColumnClass(int column) {
    switch (column) {
      case ROOT_COLUMN:
        return VirtualFile.class;
      case COMMIT_COLUMN:
        return GraphCommitCell.class;
      case AUTHOR_COLUMN:
        return String.class;
      case DATE_COLUMN:
        return String.class;
      default:
        throw new IllegalArgumentException("columnIndex is " + column + " > " + (COLUMN_COUNT - 1));
    }
  }

  @Override
  public String getColumnName(int column) {
    return COLUMN_NAMES[column];
  }

  @Nullable
  public List<Change> getSelectedChanges(int[] selectedRows) {
    List<Change> changes = new ArrayList<Change>();
    for (Node node : nodes(selectedRows)) {
      VcsFullCommitDetails commitData = myDataHolder.getCommitDetailsGetter().getCommitData(node);
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

}
