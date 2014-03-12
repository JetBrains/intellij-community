package com.intellij.vcs.log.ui.frame;

import com.intellij.ide.CopyProvider;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesTreeBrowser;
import com.intellij.openapi.vcs.changes.issueLinks.TableLinkMouseListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.table.JBTable;
import com.intellij.util.PairFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsLogHighlighter;
import com.intellij.vcs.log.data.DataPack;
import com.intellij.vcs.log.data.VcsLogDataHolder;
import com.intellij.vcs.log.graph.*;
import com.intellij.vcs.log.graph.render.GraphCommitCell;
import com.intellij.vcs.log.graph.render.PositionUtil;
import com.intellij.vcs.log.ui.VcsLogUiImpl;
import com.intellij.vcs.log.ui.render.GraphCommitCellRender;
import com.intellij.vcs.log.ui.tables.AbstractVcsLogTableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static com.intellij.vcs.log.graph.render.PrintParameters.HEIGHT_CELL;

public class VcsLogGraphTable extends JBTable implements TypeSafeDataProvider, CopyProvider {

  private static final int ROOT_INDICATOR_WIDTH = 5;
  private static final int MAX_DEFAULT_AUTHOR_COLUMN_WIDTH = 200;
  private static final int MAX_ROWS_TO_CALC_WIDTH = 1000;
  private static final int MAX_ROWS_TO_CALC_OFFSET = 100;

  @NotNull private final VcsLogUiImpl myUI;
  private final VcsLogDataHolder myLogDataHolder;
  private final GraphCommitCellRender myGraphCommitCellRender;

  private boolean myColumnsSizeInitialized = false;
  private volatile boolean myRepaintFreezed;

  @NotNull private final Collection<VcsLogHighlighter> myHighlighters = ContainerUtil.newArrayList();

  @NotNull private DataPack myDataPack;

  public VcsLogGraphTable(@NotNull VcsLogUiImpl UI, @NotNull final VcsLogDataHolder logDataHolder, @NotNull DataPack initialDataPack) {
    super();
    myUI = UI;
    myLogDataHolder = logDataHolder;
    myDataPack = initialDataPack;
    myGraphCommitCellRender = new GraphCommitCellRender(myUI.getColorManager(), logDataHolder, myDataPack.getGraphFacade(), this);

    setDefaultRenderer(VirtualFile.class, new RootCellRenderer(myUI, myLogDataHolder.isMultiRoot()));
    setDefaultRenderer(GraphCommitCell.class, myGraphCommitCellRender);
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

    PopupHandler.installPopupHandler(this, VcsLogUiImpl.POPUP_ACTION_GROUP, VcsLogUiImpl.VCS_LOG_TABLE_PLACE);
  }

  @Override
  public void setModel(@NotNull TableModel model) {
    super.setModel(model);
    // initialize sizes once, when the real model is set (not from the constructor).
    if (!myColumnsSizeInitialized && !(model instanceof DefaultTableModel)) {
      myColumnsSizeInitialized = true;
      setColumnPreferredSize();
      setAutoCreateColumnsFromModel(false); // otherwise sizes are recalculated after each TableColumn re-initialization
    }
  }

  private void setColumnPreferredSize() {
    for (int i = 0; i < getColumnCount(); i++) {
      TableColumn column = getColumnModel().getColumn(i);
      if (i == AbstractVcsLogTableModel.ROOT_COLUMN) { // thin stripe or nothing
        int rootWidth = myUI.getColorManager().isMultipleRoots() ? ROOT_INDICATOR_WIDTH : 0;
        // NB: all further instructions and their order are important, otherwise the minimum size which is less than 15 won't be applied
        column.setMinWidth(rootWidth);
        column.setMaxWidth(rootWidth);
        column.setPreferredWidth(rootWidth);
      }
      else if (i == AbstractVcsLogTableModel.COMMIT_COLUMN) { // let commit message occupy as much as possible
        column.setPreferredWidth(Short.MAX_VALUE);
      }
      else if (i == AbstractVcsLogTableModel.AUTHOR_COLUMN) { // detect author with the longest name
        // to avoid querying the last row (it would lead to full graph loading)
        int maxRowsToCheck = Math.min(MAX_ROWS_TO_CALC_WIDTH, getRowCount() - MAX_ROWS_TO_CALC_OFFSET);
        if (maxRowsToCheck < 0) { // but if the log is small, check all of them
          maxRowsToCheck = getRowCount();
        }
        int contentWidth = calcMaxContentColumnWidth(i, maxRowsToCheck);
        column.setMinWidth(Math.min(contentWidth, MAX_DEFAULT_AUTHOR_COLUMN_WIDTH));
        column.setWidth(column.getMinWidth());
      }
      else if (i == AbstractVcsLogTableModel.DATE_COLUMN) { // all dates have nearly equal sizes
        Font tableFont = UIManager.getFont("Table.font");
        column.setMinWidth(getFontMetrics(tableFont).stringWidth("mm" + DateFormatUtil.formatDateTime(new Date())));
        column.setWidth(column.getMinWidth());
      }
    }
  }

  private int calcMaxContentColumnWidth(int columnIndex, int maxRowsToCheck) {
    int maxWidth = 0;
    for (int row = 0; row < maxRowsToCheck; row++) {
      TableCellRenderer renderer = getCellRenderer(row, columnIndex);
      Component comp = prepareRenderer(renderer, row, columnIndex);
      maxWidth = Math.max(comp.getPreferredSize().width, maxWidth);
    }
    return maxWidth + UIUtil.DEFAULT_HGAP;
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
  public List<Change> getSelectedChanges() {
    TableModel model = getModel();
    if (!(model instanceof AbstractVcsLogTableModel)) {
      return null;
    }
    List<Change> changes = ((AbstractVcsLogTableModel)model).getSelectedChanges(sortSelectedRows());
    return changes == null ? null : CommittedChangesTreeBrowser.zipChanges(changes);
  }

  @NotNull
  private List<Integer> sortSelectedRows() {
    List<Integer> rows = ContainerUtil.newArrayList();
    for (int row : getSelectedRows()) {
      rows.add(row);
    }
    Collections.sort(rows, Collections.reverseOrder());
    return rows;
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

  public void updateDataPack(@NotNull DataPack dataPack) {
    myDataPack = dataPack;
    myGraphCommitCellRender.updateGraphFacade(dataPack.getGraphFacade());
  }

  public void addHighlighter(@NotNull VcsLogHighlighter highlighter) {
    myHighlighters.add(highlighter);
  }

  public void removeHighlighter(@NotNull VcsLogHighlighter highlighter) {
    myHighlighters.remove(highlighter);
  }

  public void removeAllHighlighters() {
    myHighlighters.clear();
  }

  public void applyHighlighters(@NotNull Component rendererComponent, int row, boolean selected) {
    boolean fgUpdated = false;
    for (VcsLogHighlighter highlighter : myHighlighters) {
      Color color = highlighter.getForeground(myDataPack.getGraphFacade().getCommitAtRow(row), selected);
      if (color != null) {
        rendererComponent.setForeground(color);
        fgUpdated = true;
      }
    }
    if (!fgUpdated) { // reset highlighting if no-one wants to change it
      rendererComponent.setForeground(UIUtil.getTableForeground(selected));
    }
  }

  private class MyMouseAdapter extends MouseAdapter {
    private final TableLinkMouseListener myLinkListener;

    MyMouseAdapter() {
      myLinkListener = new TableLinkMouseListener();
    }

    @Override
    public void mouseClicked(MouseEvent e) {
      if (myLinkListener.onClick(e, e.getClickCount())) {
        return;
      }

      if (e.getClickCount() == 1) {
        performAction(e, new PairFunction<Integer, Point, GraphAction>() {
          @Override
          public GraphAction fun(Integer row, Point point) {
            return new ClickGraphAction(row, point);
          }
        });
      }
    }

    @Override
    public void mouseMoved(MouseEvent e) {
      if (isAboveLink(e)) {
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      }
      else {
        performAction(e, new PairFunction<Integer, Point, GraphAction>() {
          @Override
          public GraphAction fun(Integer row, Point point) {
            return new MouseOverAction(row, point);
          }
        });
      }
    }

    private void performAction(@NotNull MouseEvent e, @NotNull PairFunction<Integer, Point, GraphAction> actionConstructor) {
      int row = PositionUtil.getRowIndex(e.getPoint());
      Point point = calcPoint4Graph(e.getPoint());
      GraphFacade graphFacade = myDataPack.getGraphFacade();
      GraphAnswer answer = graphFacade.performAction(actionConstructor.fun(row, point));
      myUI.handleAnswer(answer);
    }

    private boolean isAboveLink(MouseEvent e) {
      return myLinkListener.getTagAt(e) != null;
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

  @NotNull
  private Point calcPoint4Graph(@NotNull Point clickPoint) {
    return new Point(clickPoint.x - getXOffset(), PositionUtil.getYInsideRow(clickPoint));
  }

  private int getXOffset() {
    TableColumn rootColumn = getColumnModel().getColumn(AbstractVcsLogTableModel.ROOT_COLUMN);
    return myLogDataHolder.isMultiRoot() ? rootColumn.getWidth() : 0;
  }

  private static class RootCellRenderer extends JPanel implements TableCellRenderer {

    @NotNull private final VcsLogUiImpl myUi;

    @NotNull private Color myColor = UIUtil.getTableBackground();

    RootCellRenderer(@NotNull VcsLogUiImpl ui, boolean multiRoot) {
      myUi = ui;
      int rootWidth = multiRoot ? ROOT_INDICATOR_WIDTH : 0;
      setPreferredSize(new Dimension(rootWidth, -1));
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
      setBackground(isSelected ? table.getSelectionBackground() : JBColor.WHITE);
      setBorder(null);
      applyHighlighters(rendererComponent, row, isSelected);
      return rendererComponent;
    }

  }
}
