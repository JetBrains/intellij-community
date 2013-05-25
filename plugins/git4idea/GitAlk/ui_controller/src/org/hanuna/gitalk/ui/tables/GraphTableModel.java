package org.hanuna.gitalk.ui.tables;

import org.hanuna.gitalk.commit.Hash;
import org.hanuna.gitalk.data.DataPack;
import org.hanuna.gitalk.graph.elements.Node;
import org.hanuna.gitalk.log.commit.CommitData;
import org.hanuna.gitalk.log.commit.parents.FakeCommitParents;
import org.hanuna.gitalk.printmodel.GraphPrintCell;
import org.hanuna.gitalk.refs.Ref;
import org.hanuna.gitalk.swing_ui.render.PositionUtil;
import org.hanuna.gitalk.ui.impl.DateConverter;
import org.jetbrains.annotations.NotNull;

import javax.swing.table.AbstractTableModel;
import java.util.*;

/**
 * @author erokhins
 */
public class GraphTableModel extends AbstractTableModel {
  private final String[] columnNames = {"Subject", "Author", "Date"};
  private final DataPack dataPack;

  private final Map<Hash, String> reworded = new HashMap<Hash, String>();
  private final Set<Hash> fixedUp = new HashSet<Hash>();

  public GraphTableModel(@NotNull DataPack dataPack) {
    this.dataPack = dataPack;
  }

  @Override
  public int getRowCount() {
    return dataPack.getGraphModel().getGraph().getNodeRows().size();
  }

  @Override
  public int getColumnCount() {
    return 3;
  }

  @Override
  public Object getValueAt(int rowIndex, int columnIndex) {
    Node commitNode = dataPack.getGraphModel().getGraph().getCommitNodeInRow(rowIndex);
    CommitData data;
    if (commitNode == null) {
      data = null;
    }
    else {
      data = dataPack.getCommitDataGetter().getCommitData(commitNode);
    }
    switch (columnIndex) {
      case 0:
        GraphPrintCell graphPrintCell = dataPack.getPrintCellModel().getGraphPrintCell(rowIndex);
        GraphCommitCell.Kind cellKind = getCellKind(PositionUtil.getNode(graphPrintCell));
        String message = "";
        List<Ref> refs = Collections.emptyList();
        if (data != null) {
          if (cellKind == GraphCommitCell.Kind.REWORD) {
            message = reworded.get(commitNode.getCommitHash());
          }
          else {
            message = data.getMessage();
            refs = dataPack.getRefsModel().refsToCommit(data.getCommitHash());
          }
        }
        else {
          if (rowIndex == getRowCount() - 1) {
            message = "load more commits";
          }
        }
        return new GraphCommitCell(graphPrintCell, cellKind, message, refs);
      case 1:
        if (data == null) {
          return "";
        }
        else {
          return data.getAuthor();
        }
      case 2:
        if (data == null) {
          return "";
        }
        else {
          return DateConverter.getStringOfDate(data.getTimeStamp());
        }
      default:
        throw new IllegalArgumentException("columnIndex > 2");
    }
  }

  private GraphCommitCell.Kind getCellKind(Node node) {
    Hash hash = node.getCommitHash();
    if (fixedUp.contains(hash)) return GraphCommitCell.Kind.FIXUP;
    if (reworded.containsKey(hash)) return GraphCommitCell.Kind.REWORD;
    if (FakeCommitParents.isFake(hash)) return GraphCommitCell.Kind.PICK;
    return GraphCommitCell.Kind.NORMAL;
  }

  @Override
  public Class<?> getColumnClass(int column) {
    switch (column) {
      case 0:
        return GraphCommitCell.class;
      case 1:
        return String.class;
      case 2:
        return String.class;
      default:
        throw new IllegalArgumentException("column > 2");
    }
  }

  @Override
  public String getColumnName(int column) {
    return columnNames[column];
  }

  @Override
  public boolean isCellEditable(int rowIndex, int columnIndex) {
    return columnIndex == 0;
  }

  public void clearReworded() {
    reworded.clear();
    //fireTableDataChanged();
  }

  public void addReworded(Hash hash, String newMessage) {
    reworded.put(hash, newMessage);
    //fireTableDataChanged();
  }

  public void addReworded(Map<Hash, String> map) {
    reworded.putAll(map);
    //fireTableDataChanged();
  }

  public void addFixedUp(Collection<Hash> collection) {
    fixedUp.addAll(collection);
  }
}
