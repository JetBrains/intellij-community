package com.intellij.vcs.log.ui.frame;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.data.VcsLogDataHolder;
import com.intellij.vcs.log.graph.elements.Edge;
import com.intellij.vcs.log.graph.elements.GraphElement;
import com.intellij.vcs.log.graph.elements.Node;
import com.intellij.vcs.log.graph.render.*;
import com.intellij.vcs.log.printmodel.GraphPrintCell;
import com.intellij.vcs.log.printmodel.SpecialPrintElement;
import com.intellij.vcs.log.ui.VcsLogUI;
import com.intellij.vcs.log.ui.render.AbstractPaddingCellRender;
import com.intellij.vcs.log.ui.render.CommitCellRender;
import com.intellij.vcs.log.ui.render.GraphCommitCellRender;
import com.intellij.vcs.log.ui.tables.AbstractVcsLogTableModel;
import com.intellij.vcs.log.ui.tables.GraphTableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

import static com.intellij.vcs.log.graph.render.PrintParameters.HEIGHT_CELL;

/**
 * @author erokhins
 */
public class VcsLogGraphTable extends JBTable {

  private static final Logger LOG = Logger.getInstance(VcsLogGraphTable.class);
  private static final int ROOT_INDICATOR_WIDTH = 5;

  @NotNull private final VcsLogUI myUI;
  @NotNull private final GraphCellPainter myGraphPainter = new SimpleGraphCellPainter();

  public VcsLogGraphTable(@NotNull VcsLogUI UI, final VcsLogDataHolder logDataHolder) {
    super();
    myUI = UI;

    setTableHeader(null);
    setDefaultRenderer(VirtualFile.class, new RootCellRenderer(myUI));
    setDefaultRenderer(GraphCommitCell.class, new GraphCommitCellRender(myGraphPainter, logDataHolder, myUI.getColorManager()));
    setDefaultRenderer(CommitCell.class, new CommitCellRender(myUI.getColorManager()));
    setDefaultRenderer(String.class, new StringCellRenderer());

    setRowHeight(HEIGHT_CELL);
    setShowHorizontalLines(false);
    setIntercellSpacing(new Dimension(0, 0));

    getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        int selectedRow = getSelectedRow();
        if (selectedRow >= 0) {
          myUI.click(selectedRow);
        }
      }
    });

    MouseAdapter mouseAdapter = new MyMouseAdapter();
    addMouseMotionListener(mouseAdapter);
    addMouseListener(mouseAdapter);
  }

  public void setPreferredColumnWidths() {
    TableColumn rootColumn = getColumnModel().getColumn(AbstractVcsLogTableModel.ROOT_COLUMN);
    int rootWidth = myUI.getColorManager().isMultipleRoots() ? ROOT_INDICATOR_WIDTH : 0;
    // NB: all further instructions and their order are important, otherwise the minimum size which is less than 15 won't be applied
    rootColumn.setMinWidth(rootWidth);
    rootColumn.setMaxWidth(rootWidth);
    rootColumn.setPreferredWidth(rootWidth);

    getColumnModel().getColumn(AbstractVcsLogTableModel.COMMIT_COLUMN).setPreferredWidth(700);
    getColumnModel().getColumn(AbstractVcsLogTableModel.AUTHOR_COLUMN).setMinWidth(90);
    getColumnModel().getColumn(AbstractVcsLogTableModel.DATE_COLUMN).setMinWidth(90);
  }

  public void jumpToRow(int rowIndex) {
    scrollRectToVisible(getCellRect(rowIndex, 0, false));
    setRowSelectionInterval(rowIndex, rowIndex);
    scrollRectToVisible(getCellRect(rowIndex, 0, false));
  }

  @Nullable
  public GraphPrintCell getGraphPrintCellForRow(TableModel model, int rowIndex) {
    if (rowIndex >= model.getRowCount()) {
      return null;
    }
    Object commitValue = model.getValueAt(rowIndex, AbstractVcsLogTableModel.COMMIT_COLUMN);
    if (commitValue instanceof GraphCommitCell) {
      GraphCommitCell commitCell = (GraphCommitCell)commitValue;
      return commitCell.getPrintCell();
    }
    return null;
  }

  @Nullable
  public List<Change> getSelectedChanges() {
    TableModel model = getModel();
    if (!(model instanceof AbstractVcsLogTableModel)) {
      LOG.error("Unexpected table model passed to the VcsLogGraphTable: " + model);
      return null;
    }
    return ((GraphTableModel)model).getSelectedChanges(getSelectedRows());
  }

  private class MyMouseAdapter extends MouseAdapter {
    private final Cursor DEFAULT_CURSOR = new Cursor(Cursor.DEFAULT_CURSOR);
    private final Cursor HAND_CURSOR = new Cursor(Cursor.HAND_CURSOR);

    @Nullable
    private GraphPrintCell getGraphPrintCell(MouseEvent e) {
      int rowIndex = PositionUtil.getRowIndex(e);
      return getGraphPrintCellForRow(getModel(), rowIndex);
    }

    @Nullable
    private GraphElement overCell(MouseEvent e) {
      int y = PositionUtil.getYInsideRow(e);
      int x = e.getX();
      GraphPrintCell row = getGraphPrintCell(e);
      return row != null ? myGraphPainter.mouseOver(row, x, y) : null;
    }

    @Nullable
    private Node arrowToNode(MouseEvent e) {
      int y = PositionUtil.getYInsideRow(e);
      int x = e.getX();
      GraphPrintCell row = getGraphPrintCell(e);
      if (row == null) {
        return null;
      }
      SpecialPrintElement printElement = myGraphPainter.mouseOverArrow(row, x, y);
      if (printElement == null) {
        return null;
      }
      Edge edge = printElement.getGraphElement().getEdge();
      if (edge == null) {
        return null;
      }
      return printElement.getType() == SpecialPrintElement.Type.DOWN_ARROW ? edge.getDownNode() : edge.getUpNode();
    }

    @Override
    public void mouseClicked(MouseEvent e) {
      if (e.getClickCount() == 1) {
        Node jumpToNode = arrowToNode(e);
        if (jumpToNode != null) {
          jumpToRow(jumpToNode.getRowIndex());
        }
        GraphElement graphElement = overCell(e);
        myUI.click(graphElement);
        if (graphElement == null) {
          myUI.click(PositionUtil.getRowIndex(e));
        }
      }
    }

    @Override
    public void mouseMoved(MouseEvent e) {
      Node jumpToNode = arrowToNode(e);
      if (jumpToNode != null) {
        setCursor(HAND_CURSOR);
      }
      else {
        setCursor(DEFAULT_CURSOR);
      }
      myUI.over(overCell(e));
    }

    @Override
    public void mouseEntered(MouseEvent e) {
      // Do nothing
    }

    @Override
    public void mouseExited(MouseEvent e) {
      // Do nothing
    }
  }

  private static class RootCellRenderer extends JPanel implements TableCellRenderer {

    private static final Logger LOG = Logger.getInstance(RootCellRenderer.class);

    @NotNull private final VcsLogUI myUi;

    @NotNull private Color myColor = UIUtil.getTableBackground();

    RootCellRenderer(@NotNull VcsLogUI ui) {
      myUi = ui;
    }

    @Override
    protected void paintComponent(Graphics g) {
      g.setColor(myColor);
      g.fillRect(0, 0, ROOT_INDICATOR_WIDTH - 1, HEIGHT_CELL);
      UIUtil.drawLine((Graphics2D)g, ROOT_INDICATOR_WIDTH - 1, 0, ROOT_INDICATOR_WIDTH - 1, HEIGHT_CELL, null,
                      myUi.getColorManager().getRootIndicatorBorder());
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      if (value instanceof VirtualFile) {
        myColor = myUi.getColorManager().getRootColor((VirtualFile)value);
      }
      else {
        LOG.error("Incorrect value " + value + " specified in row #" + row + ", column #");
        myColor = UIUtil.getTableBackground(isSelected);
      }
      return this;
    }
  }

  private class StringCellRenderer extends DefaultTableCellRenderer {
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      Component rendererComponent = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      Object commit = getValueAt(row, AbstractVcsLogTableModel.COMMIT_COLUMN);
      if (commit instanceof GraphCommitCell) {
        if (AbstractPaddingCellRender.isMarked(commit) && !isSelected) {
          rendererComponent.setBackground(AbstractPaddingCellRender.MARKED_BACKGROUND);
        }
        else {
          setBackground(isSelected ? table.getSelectionBackground() : JBColor.WHITE);
        }
      }
      return rendererComponent;
    }

  }
}
