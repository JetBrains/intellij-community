package com.intellij.vcs.log.ui.frame;

import com.intellij.ide.CopyProvider;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.issueLinks.TableLinkMouseListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.table.JBTable;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.data.VcsLogDataHolder;
import com.intellij.vcs.log.graph.elements.Edge;
import com.intellij.vcs.log.graph.elements.GraphElement;
import com.intellij.vcs.log.graph.elements.Node;
import com.intellij.vcs.log.graph.render.*;
import com.intellij.vcs.log.printmodel.GraphPrintCell;
import com.intellij.vcs.log.printmodel.SpecialPrintElement;
import com.intellij.vcs.log.ui.VcsLogUI;
import com.intellij.vcs.log.ui.render.CommitCellRender;
import com.intellij.vcs.log.ui.render.GraphCommitCellRender;
import com.intellij.vcs.log.ui.tables.AbstractVcsLogTableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

import static com.intellij.vcs.log.graph.render.PrintParameters.HEIGHT_CELL;

/**
 * @author erokhins
 */
public class VcsLogGraphTable extends JBTable implements TypeSafeDataProvider, CopyProvider {

  private static final Logger LOG = Logger.getInstance(VcsLogGraphTable.class);
  private static final int ROOT_INDICATOR_WIDTH = 5;

  @NotNull private final VcsLogUI myUI;
  @NotNull private final GraphCellPainter myGraphPainter;

  private  volatile boolean myRepaintFreezed;

  public VcsLogGraphTable(@NotNull VcsLogUI UI, final VcsLogDataHolder logDataHolder) {
    super();
    myUI = UI;

    myGraphPainter = new SimpleGraphCellPainter(logDataHolder.isMultiRoot());

    setDefaultRenderer(VirtualFile.class, new RootCellRenderer(myUI));
    setDefaultRenderer(GraphCommitCell.class, new GraphCommitCellRender(myGraphPainter, logDataHolder, myUI.getColorManager()));
    setDefaultRenderer(CommitCell.class, new CommitCellRender(myUI.getColorManager(), logDataHolder.getProject()));
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

    PopupHandler.installPopupHandler(this, VcsLogUI.POPUP_ACTION_GROUP, VcsLogUI.VCS_LOG_TABLE_PLACE);

    getColumnModel().addColumnModelListener(new TableColumnModelListener() {
      @Override
      public void columnAdded(TableColumnModelEvent e) {
        if (e.getToIndex() == AbstractVcsLogTableModel.ROOT_COLUMN) {
          myGraphPainter.setRootColumn(getColumnModel().getColumn(AbstractVcsLogTableModel.ROOT_COLUMN));
        }
      }

      @Override
      public void columnRemoved(TableColumnModelEvent e) {
      }

      @Override
      public void columnMoved(TableColumnModelEvent e) {
      }

      @Override
      public void columnMarginChanged(ChangeEvent e) {
      }

      @Override
      public void columnSelectionChanged(ListSelectionEvent e) {
      }
    });
  }

  @Override
  public String getToolTipText(@NotNull MouseEvent event) {
    int row = rowAtPoint(event.getPoint());
    int column = columnAtPoint(event.getPoint());
    if (column < 0 || row < 0) {
      return null;
    }
    if (column == AbstractVcsLogTableModel.ROOT_COLUMN) {
      Object at = getValueAt(row, column);
      if (at instanceof VirtualFile) {
        return ((VirtualFile)at).getPresentableUrl();
      }
    }
    return null;
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

  @Override
  protected void paintComponent(Graphics g) {
    if (myRepaintFreezed) {
      return;
    }
    super.paintComponent(g);
  }

  /**
   * Freeze repaint to avoid repainting during changing the Graph.
   */
  public void executeWithoutRepaint(@NotNull Runnable action) {
    myRepaintFreezed = true;
    try {
      action.run();
    }
    finally {
      myRepaintFreezed = false;
    }
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
    return ((AbstractVcsLogTableModel)model).getSelectedChanges(getSelectedRows());
  }

  @Override
  public void calcData(DataKey key, DataSink sink) {
    if (PlatformDataKeys.COPY_PROVIDER == key) {
      sink.put(key, this);
    }
  }

  @Override
  public void performCopy(@NotNull DataContext dataContext) {
    List<String> hashes = ContainerUtil.newArrayList();
    for (int row : getSelectedRows()) {
      Hash hash = ((AbstractVcsLogTableModel)getModel()).getHashAtRow(row);
      if (hash != null) {
        hashes.add(hash.asString());
      }
    }
    if (!hashes.isEmpty()) {
      CopyPasteManager.getInstance().setContents(new StringSelection(StringUtil.join(hashes, "\n")));
    }
  }

  @Override
  public boolean isCopyEnabled(@NotNull DataContext dataContext) {
    return getSelectedRowCount() > 0;
  }

  @Override
  public boolean isCopyVisible(@NotNull DataContext dataContext) {
    return true;
  }

  private class MyMouseAdapter extends MouseAdapter {
    private final Cursor DEFAULT_CURSOR = new Cursor(Cursor.DEFAULT_CURSOR);
    private final Cursor HAND_CURSOR = new Cursor(Cursor.HAND_CURSOR);
    private final TableLinkMouseListener myTableListener;

    MyMouseAdapter() {
      myTableListener = new TableLinkMouseListener();
    }

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
      myTableListener.onClick(e, e.getClickCount());
    }

    @Override
    public void mouseMoved(MouseEvent e) {
      Node jumpToNode = arrowToNode(e);
      if (jumpToNode != null || isAboveLink(e)) {
        setCursor(HAND_CURSOR);
      }
      else {
        setCursor(DEFAULT_CURSOR);
      }
      myUI.over(overCell(e));
    }

    private boolean isAboveLink(MouseEvent e) {
      return myTableListener.getTagAt(e) != null;
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
        setBackground(isSelected ? table.getSelectionBackground() : JBColor.WHITE);
      }
      return rendererComponent;
    }

  }
}
