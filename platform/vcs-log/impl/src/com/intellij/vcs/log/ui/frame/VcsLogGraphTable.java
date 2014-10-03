/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.vcs.log.ui.frame;

import com.intellij.ide.CopyProvider;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesTreeBrowser;
import com.intellij.openapi.vcs.changes.issueLinks.TableLinkMouseListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.TableScrollingUtil;
import com.intellij.ui.table.JBTable;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsLogHighlighter;
import com.intellij.vcs.log.data.VcsLogDataHolder;
import com.intellij.vcs.log.data.VisiblePack;
import com.intellij.vcs.log.graph.ColorGenerator;
import com.intellij.vcs.log.graph.PrintElement;
import com.intellij.vcs.log.graph.actions.GraphAnswer;
import com.intellij.vcs.log.graph.actions.GraphMouseAction;
import com.intellij.vcs.log.printer.idea.GraphCellPainter;
import com.intellij.vcs.log.printer.idea.PositionUtil;
import com.intellij.vcs.log.printer.idea.SimpleGraphCellPainter;
import com.intellij.vcs.log.ui.VcsLogUiImpl;
import com.intellij.vcs.log.ui.render.GraphCommitCell;
import com.intellij.vcs.log.ui.render.GraphCommitCellRender;
import com.intellij.vcs.log.ui.tables.GraphTableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.intellij.vcs.log.printer.idea.PrintParameters.HEIGHT_CELL;

public class VcsLogGraphTable extends JBTable implements TypeSafeDataProvider, CopyProvider {

  private static final int ROOT_INDICATOR_WIDTH = 5;
  private static final int MAX_DEFAULT_AUTHOR_COLUMN_WIDTH = 200;
  private static final int MAX_ROWS_TO_CALC_WIDTH = 1000;
  private static final int MAX_ROWS_TO_CALC_OFFSET = 100;

  @NotNull private final VcsLogUiImpl myUI;
  private final VcsLogDataHolder myLogDataHolder;
  private final GraphCommitCellRender myGraphCommitCellRender;

  private boolean myColumnsSizeInitialized = false;
  private final AtomicInteger myRepaintFreezedCounter = new AtomicInteger();

  @NotNull private final Collection<VcsLogHighlighter> myHighlighters = ContainerUtil.newArrayList();

  private final GraphCellPainter myGraphCellPainter = new SimpleGraphCellPainter(new com.intellij.vcs.log.printer.idea.ColorGenerator() {
    @Override
    public Color getColor(int colorId) {
      return ColorGenerator.getColor(colorId);
    }
  });

  @NotNull private VisiblePack myDataPack;

  public VcsLogGraphTable(@NotNull VcsLogUiImpl UI, @NotNull final VcsLogDataHolder logDataHolder, @NotNull VisiblePack initialDataPack) {
    super();
    myUI = UI;
    myLogDataHolder = logDataHolder;
    myDataPack = initialDataPack;
    myGraphCommitCellRender = new GraphCommitCellRender(myUI.getColorManager(), logDataHolder, myGraphCellPainter,
                                                        myDataPack.getVisibleGraph(), this);

    setDefaultRenderer(VirtualFile.class, new RootCellRenderer(myUI, myLogDataHolder.isMultiRoot()));
    setDefaultRenderer(GraphCommitCell.class, myGraphCommitCellRender);
    setDefaultRenderer(String.class, new StringCellRenderer());

    setRowHeight(HEIGHT_CELL);
    setShowHorizontalLines(false);
    setIntercellSpacing(new Dimension(0, 0));

    MouseAdapter mouseAdapter = new MyMouseAdapter();
    addMouseMotionListener(mouseAdapter);
    addMouseListener(mouseAdapter);

    PopupHandler.installPopupHandler(this, VcsLogUiImpl.POPUP_ACTION_GROUP, VcsLogUiImpl.VCS_LOG_TABLE_PLACE);
    TableScrollingUtil.installActions(this, false);
  }

  @Override
  public void setModel(@NotNull TableModel model) {
    super.setModel(model);
    // initialize sizes once, when the real non-empty model is set
    if (!myColumnsSizeInitialized && model.getRowCount() > 0) {
      myColumnsSizeInitialized = true;
      setColumnPreferredSize();
      setAutoCreateColumnsFromModel(false); // otherwise sizes are recalculated after each TableColumn re-initialization
    }
  }

  private void setColumnPreferredSize() {
    for (int i = 0; i < getColumnCount(); i++) {
      TableColumn column = getColumnModel().getColumn(i);
      if (i == GraphTableModel.ROOT_COLUMN) { // thin stripe or nothing
        int rootWidth = myUI.getColorManager().isMultipleRoots() ? ROOT_INDICATOR_WIDTH : 0;
        // NB: all further instructions and their order are important, otherwise the minimum size which is less than 15 won't be applied
        column.setMinWidth(rootWidth);
        column.setMaxWidth(rootWidth);
        column.setPreferredWidth(rootWidth);
      }
      else if (i == GraphTableModel.COMMIT_COLUMN) { // let commit message occupy as much as possible
        column.setPreferredWidth(Short.MAX_VALUE);
      }
      else if (i == GraphTableModel.AUTHOR_COLUMN) { // detect author with the longest name
        // to avoid querying the last row (it would lead to full graph loading)
        int maxRowsToCheck = Math.min(MAX_ROWS_TO_CALC_WIDTH, getRowCount() - MAX_ROWS_TO_CALC_OFFSET);
        if (maxRowsToCheck < 0) { // but if the log is small, check all of them
          maxRowsToCheck = getRowCount();
        }
        int contentWidth = calcMaxContentColumnWidth(i, maxRowsToCheck);
        column.setMinWidth(Math.min(contentWidth, MAX_DEFAULT_AUTHOR_COLUMN_WIDTH));
        column.setWidth(column.getMinWidth());
      }
      else if (i == GraphTableModel.DATE_COLUMN) { // all dates have nearly equal sizes
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
    if (column == GraphTableModel.ROOT_COLUMN) {
      Object at = getValueAt(row, column);
      if (at instanceof VirtualFile) {
        return ((VirtualFile)at).getPresentableUrl();
      }
    }
    return null;
  }

  public void jumpToRow(int rowIndex) {
    if (rowIndex >= 0 && rowIndex <= getRowCount() - 1) {
      scrollRectToVisible(getCellRect(rowIndex, 0, false));
      setRowSelectionInterval(rowIndex, rowIndex);
      scrollRectToVisible(getCellRect(rowIndex, 0, false));
    }
  }

  @Override
  protected void paintComponent(@NotNull Graphics g) {
    if (myRepaintFreezedCounter.get() > 0) {
      return;
    }
    super.paintComponent(g);
  }

  /**
   * Freeze repaint to avoid repainting during changing the Graph.
   */
  public void executeWithoutRepaint(@NotNull Runnable action) {
    myRepaintFreezedCounter.incrementAndGet();
    try {
      action.run();
    }
    finally {
      myRepaintFreezedCounter.decrementAndGet();
    }
  }

  @Nullable
  public List<Change> getSelectedChanges() {
    TableModel model = getModel();
    if (!(model instanceof GraphTableModel)) {
      return null;
    }
    List<Change> changes = ((GraphTableModel)model).getSelectedChanges(sortSelectedRows());
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
      Hash hash = ((GraphTableModel)getModel()).getHashAtRow(row);
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

  public void updateDataPack(@NotNull VisiblePack dataPack) {
    myDataPack = dataPack;
    myGraphCommitCellRender.updateVisibleGraph(dataPack.getVisibleGraph());
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
      Color color = highlighter.getForeground(myDataPack.getVisibleGraph().getRowInfo(row).getCommit(), selected);
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
        performAction(e, MyGraphMouseAction.Type.CLICK);
      }
    }

    @Override
    public void mouseMoved(MouseEvent e) {
      if (isAboveLink(e)) {
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      }
      else {
        performAction(e, MyGraphMouseAction.Type.OVER);
      }
    }

    private void performAction(@NotNull MouseEvent e, @NotNull final MyGraphMouseAction.Type actionType) {
      int row = PositionUtil.getRowIndex(e.getPoint());
      if (row > getRowCount() - 1) {
        return;
      }
      Point point = calcPoint4Graph(e.getPoint());
      Collection<PrintElement> printElements = myDataPack.getVisibleGraph().getRowInfo(row).getPrintElements();
      PrintElement printElement = myGraphCellPainter.mouseOver(printElements, point.x, point.y);

      GraphAnswer<Integer> answer = myDataPack.getVisibleGraph().getActionController().performMouseAction(
        new MyGraphMouseAction(printElement, actionType));
      myUI.handleAnswer(answer, actionType == MyGraphMouseAction.Type.CLICK && printElement != null);
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

    private class MyGraphMouseAction implements GraphMouseAction {
      private final PrintElement myPrintElement;
      private final Type myActionType;

      public MyGraphMouseAction(PrintElement printElement, Type actionType) {
        myPrintElement = printElement;
        myActionType = actionType;
      }

      @Nullable
      @Override
      public PrintElement getAffectedElement() {
        return myPrintElement;
      }

      @NotNull
      @Override
      public Type getType() {
        return myActionType;
      }
    }
  }

  @NotNull
  private Point calcPoint4Graph(@NotNull Point clickPoint) {
    return new Point(clickPoint.x - getXOffset(), PositionUtil.getYInsideRow(clickPoint));
  }

  private int getXOffset() {
    TableColumn rootColumn = getColumnModel().getColumn(GraphTableModel.ROOT_COLUMN);
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

  private class StringCellRenderer extends ColoredTableCellRenderer {
    @Override
    protected void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
      if (value == null) {
        return;
      }
      append(value.toString());
      applyHighlighters(this, row, selected);
      setBorder(null);
    }

  }
}
