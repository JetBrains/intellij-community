/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.google.common.primitives.Ints;
import com.intellij.ide.CopyProvider;
import com.intellij.ide.IdeTooltip;
import com.intellij.ide.IdeTooltipManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.ui.LoadingDecorator;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.issueLinks.TableLinkMouseListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.table.JBTable;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.LoadingDetails;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.data.VcsLogProgress;
import com.intellij.vcs.log.data.VisiblePack;
import com.intellij.vcs.log.graph.*;
import com.intellij.vcs.log.graph.actions.GraphAction;
import com.intellij.vcs.log.graph.actions.GraphAnswer;
import com.intellij.vcs.log.impl.VcsLogUtil;
import com.intellij.vcs.log.paint.GraphCellPainter;
import com.intellij.vcs.log.paint.PositionUtil;
import com.intellij.vcs.log.paint.SimpleGraphCellPainter;
import com.intellij.vcs.log.ui.VcsLogActionPlaces;
import com.intellij.vcs.log.ui.VcsLogColorManager;
import com.intellij.vcs.log.ui.VcsLogColorManagerImpl;
import com.intellij.vcs.log.ui.VcsLogUiImpl;
import com.intellij.vcs.log.ui.render.GraphCommitCell;
import com.intellij.vcs.log.ui.render.GraphCommitCellRenderer;
import com.intellij.vcs.log.ui.tables.GraphTableModel;
import com.intellij.vcs.log.util.VcsUserUtil;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.plaf.basic.BasicTableHeaderUI;
import javax.swing.table.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

public class VcsLogGraphTable extends TableWithProgress implements DataProvider, CopyProvider {
  private static final Logger LOG = Logger.getInstance(VcsLogGraphTable.class);

  public static final int ROOT_INDICATOR_COLORED_WIDTH = 8;
  public static final int ROOT_INDICATOR_WHITE_WIDTH = 5;
  private static final int ROOT_INDICATOR_WIDTH = ROOT_INDICATOR_WHITE_WIDTH + ROOT_INDICATOR_COLORED_WIDTH;
  private static final int ROOT_NAME_MAX_WIDTH = 200;
  private static final int MAX_DEFAULT_AUTHOR_COLUMN_WIDTH = 200;
  private static final int MAX_ROWS_TO_CALC_WIDTH = 1000;
  private static final int MAX_ROWS_TO_CALC_OFFSET = 100;

  @NotNull private final VcsLogUiImpl myUi;
  @NotNull private final VcsLogData myLogData;
  @NotNull private final MyDummyTableCellEditor myDummyEditor = new MyDummyTableCellEditor();
  @NotNull private final TableCellRenderer myDummyRenderer = new DefaultTableCellRenderer();
  @NotNull private final GraphCommitCellRenderer myGraphCommitCellRenderer;
  private boolean myColumnsSizeInitialized = false;
  @Nullable private Selection mySelection = null;

  @NotNull private final Collection<VcsLogHighlighter> myHighlighters = ContainerUtil.newArrayList();

  @NotNull private final GraphCellPainter myGraphCellPainter = new SimpleGraphCellPainter(new DefaultColorGenerator()) {
    @Override
    protected int getRowHeight() {
      return VcsLogGraphTable.this.getRowHeight();
    }
  };

  public VcsLogGraphTable(@NotNull VcsLogUiImpl ui, @NotNull VcsLogData logData, @NotNull VisiblePack initialDataPack) {
    super(new GraphTableModel(initialDataPack, logData, ui));
    getEmptyText().setText("Changes log");

    myUi = ui;
    myLogData = logData;
    myGraphCommitCellRenderer = new GraphCommitCellRenderer(logData, myGraphCellPainter, this);

    myLogData.getProgress().addProgressIndicatorListener(new MyProgressListener(), ui);

    setDefaultRenderer(VirtualFile.class, new RootCellRenderer(myUi));
    setDefaultRenderer(GraphCommitCell.class, myGraphCommitCellRenderer);
    setDefaultRenderer(String.class, new StringCellRenderer());

    setShowHorizontalLines(false);
    setIntercellSpacing(JBUI.emptySize());
    setTableHeader(new InvisibleResizableHeader());

    MouseAdapter mouseAdapter = new MyMouseAdapter();
    addMouseMotionListener(mouseAdapter);
    addMouseListener(mouseAdapter);

    getSelectionModel().addListSelectionListener(new MyListSelectionListener());

    PopupHandler.installPopupHandler(this, VcsLogActionPlaces.POPUP_ACTION_GROUP, VcsLogActionPlaces.VCS_LOG_TABLE_PLACE);
    ScrollingUtil.installActions(this, false);

    initColumnSize();
    addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        updateCommitColumnWidth();
      }
    });
  }

  public void updateDataPack(@NotNull VisiblePack visiblePack, boolean permGraphChanged) {
    VcsLogGraphTable.Selection previousSelection = getSelection();
    getModel().setVisiblePack(visiblePack);
    previousSelection.restore(visiblePack.getVisibleGraph(), true, permGraphChanged);

    for (VcsLogHighlighter highlighter : myHighlighters) {
      highlighter.update(visiblePack, permGraphChanged);
    }

    setPaintBusy(false);
    initColumnSize();
  }

  boolean initColumnSize() {
    if (!myColumnsSizeInitialized && getModel().getRowCount() > 0) {
      myColumnsSizeInitialized = setColumnPreferredSize();
      if (myColumnsSizeInitialized) {
        setAutoCreateColumnsFromModel(false); // otherwise sizes are recalculated after each TableColumn re-initialization
        for (int column = 0; column < getColumnCount(); column++) {
          getColumnModel().getColumn(column).setResizable(column != GraphTableModel.ROOT_COLUMN);
        }
      }
      return myColumnsSizeInitialized;
    }
    return false;
  }

  private boolean setColumnPreferredSize() {
    boolean sizeCalculated = false;
    Font tableFont = UIManager.getFont("Table.font");
    for (int i = 0; i < getColumnCount(); i++) {
      TableColumn column = getColumnModel().getColumn(i);
      if (i == GraphTableModel.ROOT_COLUMN) { // thin stripe, or root name, or nothing
        setRootColumnSize(column);
      }
      else if (i == GraphTableModel.AUTHOR_COLUMN) { // detect author with the longest name
        // to avoid querying the last row (it would lead to full graph loading)
        int maxRowsToCheck = Math.min(MAX_ROWS_TO_CALC_WIDTH, getRowCount() - MAX_ROWS_TO_CALC_OFFSET);
        if (maxRowsToCheck < 0) { // but if the log is small, check all of them
          maxRowsToCheck = getRowCount();
        }
        int maxWidth = 0;
        for (int row = 0; row < maxRowsToCheck; row++) {
          String value = getModel().getValueAt(row, i).toString();
          maxWidth = Math.max(getFontMetrics(tableFont.deriveFont(Font.BOLD)).stringWidth(value), maxWidth);
          if (!value.isEmpty()) sizeCalculated = true;
        }
        int min = Math.min(maxWidth + UIUtil.DEFAULT_HGAP, MAX_DEFAULT_AUTHOR_COLUMN_WIDTH);
        column.setPreferredWidth(min);
      }
      else if (i == GraphTableModel.DATE_COLUMN) { // all dates have nearly equal sizes
        int min = getFontMetrics(tableFont.deriveFont(Font.BOLD)).stringWidth("mm" + DateFormatUtil.formatDateTime(new Date()));
        column.setPreferredWidth(min);
      }
    }

    updateCommitColumnWidth();

    return sizeCalculated;
  }

  private void updateCommitColumnWidth() {
    int size = getWidth();
    for (int i = 0; i < getColumnCount(); i++) {
      if (i == GraphTableModel.COMMIT_COLUMN) continue;
      TableColumn column = getColumnModel().getColumn(i);
      size -= column.getPreferredWidth();
    }

    TableColumn commitColumn = getColumnModel().getColumn(GraphTableModel.COMMIT_COLUMN);
    commitColumn.setPreferredWidth(size);
  }

  private void setRootColumnSize(@NotNull TableColumn column) {
    int rootWidth;
    if (!myUi.isMultipleRoots()) {
      rootWidth = 0;
    }
    else if (!myUi.isShowRootNames()) {
      rootWidth = ROOT_INDICATOR_WIDTH;
    }
    else {
      rootWidth = Math.min(calculateMaxRootWidth(), ROOT_NAME_MAX_WIDTH);
    }

    // NB: all further instructions and their order are important, otherwise the minimum size which is less than 15 won't be applied
    column.setMinWidth(rootWidth);
    column.setMaxWidth(rootWidth);
    column.setPreferredWidth(rootWidth);
  }

  private int calculateMaxRootWidth() {
    int width = 0;
    for (VirtualFile file : myLogData.getRoots()) {
      Font tableFont = UIManager.getFont("Table.font");
      width = Math.max(getFontMetrics(tableFont).stringWidth(file.getName() + "  "), width);
    }
    return width;
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
        return "<html><b>" +
               ((VirtualFile)at).getPresentableUrl() +
               "</b><br/>Click to " +
               (myUi.isShowRootNames() ? "collapse" : "expand") +
               "</html>";
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

  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
    if (PlatformDataKeys.COPY_PROVIDER.is(dataId)) {
      return this;
    }
    return null;
  }

  @Override
  public void performCopy(@NotNull DataContext dataContext) {
    VcsLog log = VcsLogDataKeys.VCS_LOG.getData(dataContext);
    if (log == null) return;
    List<VcsFullCommitDetails> details = VcsLogUtil.collectFirstPackOfLoadedSelectedDetails(log);
    if (details.isEmpty()) return;
    String text = StringUtil.join(details, commit -> getPresentableText(commit, true), "\n");
    CopyPasteManager.getInstance().setContents(new StringSelection(text));
  }

  @Override
  public boolean isCopyEnabled(@NotNull DataContext dataContext) {
    return getSelectedRowCount() > 0;
  }

  @NotNull
  private static String getPresentableText(@NotNull VcsFullCommitDetails commit, boolean withMessage) {
    // implementation reflected by com.intellij.openapi.vcs.history.FileHistoryPanelImpl.getPresentableText()
    StringBuilder sb = new StringBuilder();
    sb.append(commit.getId().toShortString()).append(" ");
    sb.append(commit.getAuthor().getName());
    long time = commit.getAuthorTime();
    sb.append(" on ").append(DateFormatUtil.formatDate(time)).append(" at ").append(DateFormatUtil.formatTime(time));
    if (!Comparing.equal(commit.getAuthor(), commit.getCommitter())) {
      sb.append(" (committed by ").append(commit.getCommitter().getName()).append(")");
    }
    if (withMessage) {
      sb.append(" ").append(commit.getSubject());
    }
    return sb.toString();
  }

  @Override
  public boolean isCopyVisible(@NotNull DataContext dataContext) {
    return true;
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

  public SimpleTextAttributes applyHighlighters(@NotNull Component rendererComponent,
                                                int row,
                                                int column,
                                                String text,
                                                boolean hasFocus,
                                                final boolean selected) {
    VcsLogHighlighter.VcsCommitStyle style = getStyle(row, column, text, hasFocus, selected);

    assert style.getBackground() != null && style.getForeground() != null && style.getTextStyle() != null;

    rendererComponent.setBackground(style.getBackground());
    rendererComponent.setForeground(style.getForeground());

    switch (style.getTextStyle()) {
      case BOLD:
        return SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES;
      case ITALIC:
        return SimpleTextAttributes.REGULAR_ITALIC_ATTRIBUTES;
      default:
    }
    return SimpleTextAttributes.REGULAR_ATTRIBUTES;
  }

  private VcsLogHighlighter.VcsCommitStyle getBaseStyle(int row, int column, String text, boolean hasFocus, boolean selected) {
    Component dummyRendererComponent = myDummyRenderer.getTableCellRendererComponent(this, text, selected, hasFocus, row, column);
    return VcsCommitStyleFactory
      .createStyle(dummyRendererComponent.getForeground(), dummyRendererComponent.getBackground(), VcsLogHighlighter.TextStyle.NORMAL);
  }

  private VcsLogHighlighter.VcsCommitStyle getStyle(int row, int column, String text, boolean hasFocus, boolean selected) {
    VcsLogHighlighter.VcsCommitStyle baseStyle = getBaseStyle(row, column, text, hasFocus, selected);

    VisibleGraph<Integer> visibleGraph = getVisibleGraph();
    if (row < 0 || row >= visibleGraph.getVisibleCommitCount()) {
      LOG.error("Visible graph has " + visibleGraph.getVisibleCommitCount() + " commits, yet we want row " + row);
      return baseStyle;
    }

    RowInfo<Integer> rowInfo = visibleGraph.getRowInfo(row);

    VcsLogHighlighter.VcsCommitStyle defaultStyle = VcsCommitStyleFactory
      .createStyle(rowInfo.getRowType() == RowType.UNMATCHED ? JBColor.GRAY : baseStyle.getForeground(), baseStyle.getBackground(),
                   VcsLogHighlighter.TextStyle.NORMAL);

    final VcsShortCommitDetails details = myLogData.getMiniDetailsGetter().getCommitDataIfAvailable(rowInfo.getCommit());
    if (details == null || details instanceof LoadingDetails) return defaultStyle;

    List<VcsLogHighlighter.VcsCommitStyle> styles =
      ContainerUtil.map(myHighlighters, highlighter -> highlighter.getStyle(details, selected));
    return VcsCommitStyleFactory.combine(ContainerUtil.append(styles, defaultStyle));
  }

  public void viewportSet(JViewport viewport) {
    viewport.addChangeListener(e -> {
      AbstractTableModel model = getModel();
      Couple<Integer> visibleRows = ScrollingUtil.getVisibleRows(this);
      model.fireTableChanged(new TableModelEvent(model, visibleRows.first - 1, visibleRows.second, GraphTableModel.ROOT_COLUMN));
    });
  }

  private boolean expandOrCollapseRoots(@NotNull MouseEvent e) {
    TableColumn column = getRootColumnOrNull(e);
    if (column != null) {
      VcsLogUtil.triggerUsage("RootColumnClick");
      myUi.setShowRootNames(!myUi.isShowRootNames());
      return true;
    }
    return false;
  }

  public void rootColumnUpdated() {
    setRootColumnSize(getColumnModel().getColumn(GraphTableModel.ROOT_COLUMN));
    updateCommitColumnWidth();
  }

  @Nullable
  private TableColumn getRootColumnOrNull(@NotNull MouseEvent e) {
    if (!myLogData.isMultiRoot()) return null;
    int column = convertColumnIndexToModel(columnAtPoint(e.getPoint()));
    if (column == GraphTableModel.ROOT_COLUMN) {
      return getColumnModel().getColumn(column);
    }
    return null;
  }

  public static JBColor getRootBackgroundColor(@NotNull VirtualFile root, @NotNull VcsLogColorManager colorManager) {
    return VcsLogColorManagerImpl.getBackgroundColor(colorManager.getRootColor(root));
  }

  public void handleAnswer(@Nullable GraphAnswer<Integer> answer,
                           boolean dataCouldChange,
                           @Nullable Selection previousSelection,
                           @Nullable MouseEvent e) {
    if (dataCouldChange) {
      getModel().fireTableDataChanged();

      // since fireTableDataChanged clears selection we restore it here
      if (previousSelection != null) {
        previousSelection.restore(getVisibleGraph(), answer == null || (answer.getCommitToJump() != null && answer.doJump()), false);
      }
    }

    myUi.repaintUI(); // in case of repaintUI doing something more than just repainting this table in some distant future

    if (answer == null) {
      return;
    }

    if (answer.getCursorToSet() != null) {
      setCursor(answer.getCursorToSet());
    }
    if (answer.getCommitToJump() != null) {
      Integer row = getModel().getVisiblePack().getVisibleGraph().getVisibleRowIndex(answer.getCommitToJump());
      if (row != null && row >= 0 && answer.doJump()) {
        jumpToRow(row);
        // TODO wait for the full log and then jump
        return;
      }
      if (e != null) showToolTip(getArrowTooltipText(answer.getCommitToJump(), row), e);
    }
  }

  @Override
  public void setCursor(Cursor cursor) {
    super.setCursor(cursor);
    Component layeredPane = UIUtil.findParentByCondition(this, component -> component instanceof LoadingDecorator.CursorAware);
    if (layeredPane != null) {
      layeredPane.setCursor(cursor);
    }
  }

  @NotNull
  private String getArrowTooltipText(int commit, @Nullable Integer row) {
    VcsShortCommitDetails details;
    if (row != null && row >= 0) {
      details = getModel().getShortDetails(row); // preload rows around the commit
    }
    else {
      details = myLogData.getMiniDetailsGetter().getCommitData(commit, Collections.singleton(commit)); // preload just the commit
    }

    String balloonText = "";
    if (details instanceof LoadingDetails) {
      CommitId commitId = myLogData.getCommitId(commit);
      if (commitId != null) {
        balloonText = "Jump to commit" + " " + commitId.getHash().toShortString();
        if (myUi.isMultipleRoots()) {
          balloonText += " in " + commitId.getRoot().getName();
        }
      }
    }
    else {
      balloonText = "Jump to <b>\"" +
                    StringUtil.shortenTextWithEllipsis(details.getSubject(), 50, 0, "...") +
                    "\"</b> by " +
                    VcsUserUtil.getShortPresentation(details.getAuthor()) +
                    CommitPanel.formatDateTime(details.getAuthorTime());
    }
    return balloonText;
  }

  protected void showToolTip(@NotNull String text, @NotNull MouseEvent e) {
    // standard tooltip does not allow to customize its location, and locating tooltip above can obscure some important info
    Point point = new Point(e.getX() + 5, e.getY());

    JEditorPane tipComponent = IdeTooltipManager.initPane(text, new HintHint(this, point).setAwtTooltip(true), null);
    IdeTooltip tooltip = new IdeTooltip(this, point, new Wrapper(tipComponent)).setPreferredPosition(Balloon.Position.atRight);
    IdeTooltipManager.getInstance().show(tooltip, false);
  }

  @Override
  @NotNull
  public GraphTableModel getModel() {
    return (GraphTableModel)super.getModel();
  }

  @NotNull
  public Selection getSelection() {
    if (mySelection == null) mySelection = new Selection(this);
    return mySelection;
  }

  private static class Selection {
    @NotNull private final VcsLogGraphTable myTable;
    @NotNull private final TIntHashSet mySelectedCommits;
    @Nullable private final Integer myVisibleSelectedCommit;
    @Nullable private final Integer myDelta;
    private final boolean myIsOnTop;


    public Selection(@NotNull VcsLogGraphTable table) {
      myTable = table;
      List<Integer> selectedRows = ContainerUtil.sorted(Ints.asList(myTable.getSelectedRows()));
      Couple<Integer> visibleRows = ScrollingUtil.getVisibleRows(myTable);
      myIsOnTop = visibleRows.first - 1 == 0;

      VisibleGraph<Integer> graph = myTable.getVisibleGraph();

      mySelectedCommits = new TIntHashSet();

      Integer visibleSelectedCommit = null;
      Integer delta = null;
      for (int row : selectedRows) {
        if (row < graph.getVisibleCommitCount()) {
          Integer commit = graph.getRowInfo(row).getCommit();
          mySelectedCommits.add(commit);
          if (visibleRows.first - 1 <= row && row <= visibleRows.second && visibleSelectedCommit == null) {
            visibleSelectedCommit = commit;
            delta = myTable.getCellRect(row, 0, false).y - myTable.getVisibleRect().y;
          }
        }
      }
      if (visibleSelectedCommit == null && visibleRows.first - 1 >= 0) {
        visibleSelectedCommit = graph.getRowInfo(visibleRows.first - 1).getCommit();
        delta = myTable.getCellRect(visibleRows.first - 1, 0, false).y - myTable.getVisibleRect().y;
      }

      myVisibleSelectedCommit = visibleSelectedCommit;
      myDelta = delta;
    }

    public void restore(@NotNull VisibleGraph<Integer> newVisibleGraph, boolean scrollToSelection, boolean permGraphChanged) {
      Pair<TIntHashSet, Integer> toSelectAndScroll = findRowsToSelectAndScroll(myTable.getModel(), newVisibleGraph);
      if (!toSelectAndScroll.first.isEmpty()) {
        myTable.getSelectionModel().setValueIsAdjusting(true);
        toSelectAndScroll.first.forEach(row -> {
          myTable.addRowSelectionInterval(row, row);
          return true;
        });
        myTable.getSelectionModel().setValueIsAdjusting(false);
      }
      if (scrollToSelection) {
        if (myIsOnTop && permGraphChanged) { // scroll on top when some fresh commits arrive
          scrollToRow(0, 0);
        }
        else if (toSelectAndScroll.second != null) {
          assert myDelta != null;
          scrollToRow(toSelectAndScroll.second, myDelta);
        }
      }
      // sometimes commits that were selected are now collapsed
      // currently in this case selection disappears
      // in the future we need to create a method in LinearGraphController that allows to calculate visible commit for our commit
      // or answer from collapse action could return a map that gives us some information about what commits were collapsed and where
    }

    private void scrollToRow(Integer row, Integer delta) {
      Rectangle startRect = myTable.getCellRect(row, 0, true);
      myTable.scrollRectToVisible(
        new Rectangle(startRect.x, Math.max(startRect.y - delta, 0), startRect.width, myTable.getVisibleRect().height));
    }

    @NotNull
    private Pair<TIntHashSet, Integer> findRowsToSelectAndScroll(@NotNull GraphTableModel model,
                                                                 @NotNull VisibleGraph<Integer> visibleGraph) {
      TIntHashSet rowsToSelect = new TIntHashSet();

      if (model.getRowCount() == 0) {
        // this should have been covered by facade.getVisibleCommitCount,
        // but if the table is empty (no commits match the filter), the GraphFacade is not updated, because it can't handle it
        // => it has previous values set.
        return Pair.create(rowsToSelect, null);
      }

      Integer rowToScroll = null;
      for (int row = 0;
           row < visibleGraph.getVisibleCommitCount() && (rowsToSelect.size() < mySelectedCommits.size() || rowToScroll == null);
           row++) { //stop iterating if found all hashes
        int commit = visibleGraph.getRowInfo(row).getCommit();
        if (mySelectedCommits.contains(commit)) {
          rowsToSelect.add(row);
        }
        if (myVisibleSelectedCommit != null && myVisibleSelectedCommit == commit) {
          rowToScroll = row;
        }
      }
      return Pair.create(rowsToSelect, rowToScroll);
    }
  }

  private class MyMouseAdapter extends MouseAdapter {
    @NotNull private final TableLinkMouseListener myLinkListener = new TableLinkMouseListener();

    @Override
    public void mouseClicked(MouseEvent e) {
      if (myLinkListener.onClick(e, e.getClickCount())) {
        return;
      }

      if (e.getClickCount() == 1 && !expandOrCollapseRoots(e)) {
        performAction(e, GraphAction.Type.MOUSE_CLICK);
      }
    }

    @Override
    public void mouseMoved(MouseEvent e) {
      if (isAboveLink(e) || isAboveRoots(e)) {
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      }
      else if (!(VcsLogGraphTable.this.getCursor() == Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR))) {
        performAction(e, GraphAction.Type.MOUSE_OVER);
      }
    }

    private void performAction(@NotNull MouseEvent e, @NotNull final GraphAction.Type actionType) {
      int row = PositionUtil.getRowIndex(e.getPoint(), getRowHeight());
      if (row < 0 || row > getRowCount() - 1) {
        return;
      }
      Point point = calcPoint4Graph(e.getPoint());
      Collection<? extends PrintElement> printElements = getVisibleGraph().getRowInfo(row).getPrintElements();
      PrintElement printElement = myGraphCellPainter.getElementUnderCursor(printElements, point.x, point.y);

      boolean isClickOnGraphElement = actionType == GraphAction.Type.MOUSE_CLICK && printElement != null;
      if (isClickOnGraphElement) {
        triggerElementClick(printElement);
      }

      Selection previousSelection = getSelection();
      GraphAnswer<Integer> answer =
        getVisibleGraph().getActionController().performAction(new GraphAction.GraphActionImpl(printElement, actionType));
      handleAnswer(answer, isClickOnGraphElement, previousSelection, e);
    }

    private boolean isAboveLink(MouseEvent e) {
      return myLinkListener.getTagAt(e) != null;
    }

    private boolean isAboveRoots(MouseEvent e) {
      TableColumn column = getRootColumnOrNull(e);
      int row = rowAtPoint(e.getPoint());
      return column != null && (row >= 0 && row < getRowCount());
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

  private static void triggerElementClick(@NotNull PrintElement printElement) {
    if (printElement instanceof NodePrintElement) {
      VcsLogUtil.triggerUsage("GraphNodeClick");
    }
    else if (printElement instanceof EdgePrintElement) {
      if (((EdgePrintElement)printElement).hasArrow()) {
        VcsLogUtil.triggerUsage("GraphArrowClick");
      }
    }
  }

  @NotNull
  public VisibleGraph<Integer> getVisibleGraph() {
    return getModel().getVisiblePack().getVisibleGraph();
  }

  @NotNull
  private Point calcPoint4Graph(@NotNull Point clickPoint) {
    return new Point(clickPoint.x - getXOffset(), PositionUtil.getYInsideRow(clickPoint, getRowHeight()));
  }

  private int getXOffset() {
    TableColumn rootColumn = getColumnModel().getColumn(GraphTableModel.ROOT_COLUMN);
    return myLogData.isMultiRoot() ? rootColumn.getWidth() : 0;
  }

  @Override
  public TableCellEditor getCellEditor() {
    // this fixes selection problems by prohibiting selection when user clicks on graph (CellEditor does that)
    // what is fun about this code is that if you set cell editor in constructor with setCellEditor method it would not work
    return myDummyEditor;
  }

  @Override
  public int getRowHeight() {
    return myGraphCommitCellRenderer.getPreferredHeight();
  }

  @Override
  protected void paintFooter(@NotNull Graphics g, int x, int y, int width, int height) {
    int lastRow = getRowCount() - 1;
    if (lastRow >= 0) {
      g.setColor(getStyle(lastRow, GraphTableModel.COMMIT_COLUMN, "", hasFocus(), false).getBackground());
      g.fillRect(x, y, width, height);
      if (myUi.isMultipleRoots()) {
        g.setColor(getRootBackgroundColor(getModel().getRoot(lastRow), myUi.getColorManager()));

        int rootWidth = getColumnModel().getColumn(GraphTableModel.ROOT_COLUMN).getWidth();
        if (!myUi.isShowRootNames()) rootWidth -= ROOT_INDICATOR_WHITE_WIDTH;

        g.fillRect(x, y, rootWidth, height);
      }
    }
    else {
      g.setColor(getBaseStyle(lastRow, GraphTableModel.COMMIT_COLUMN, "", hasFocus(), false).getBackground());
      g.fillRect(x, y, width, height);
    }
  }

  private static class RootCellRenderer extends JBLabel implements TableCellRenderer {
    @NotNull private final VcsLogUiImpl myUi;
    @NotNull private Color myColor = UIUtil.getTableBackground();
    @NotNull private Color myBorderColor = UIUtil.getTableBackground();
    private boolean isNarrow = true;

    RootCellRenderer(@NotNull VcsLogUiImpl ui) {
      super("", CENTER);
      myUi = ui;
    }

    @Override
    protected void paintComponent(Graphics g) {
      setFont(UIManager.getFont("Table.font"));
      g.setColor(myColor);

      int width = getWidth();

      if (isNarrow) {
        g.fillRect(0, 0, width - ROOT_INDICATOR_WHITE_WIDTH, myUi.getTable().getRowHeight());
        g.setColor(myBorderColor);
        g.fillRect(width - ROOT_INDICATOR_WHITE_WIDTH, 0, ROOT_INDICATOR_WHITE_WIDTH, myUi.getTable().getRowHeight());
      }
      else {
        g.fillRect(0, 0, width, myUi.getTable().getRowHeight());
      }

      super.paintComponent(g);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      String text;
      Color color;

      if (value instanceof VirtualFile) {
        VirtualFile root = (VirtualFile)value;
        int readableRow = ScrollingUtil.getReadableRow(table, Math.round(myUi.getTable().getRowHeight() * 0.5f));
        if (row < readableRow) {
          text = "";
        }
        else if (row == 0 || !value.equals(table.getModel().getValueAt(row - 1, column)) || readableRow == row) {
          text = root.getName();
        }
        else {
          text = "";
        }
        color = getRootBackgroundColor(root, myUi.getColorManager());
      }
      else {
        text = null;
        color = UIUtil.getTableBackground(isSelected);
      }

      myColor = color;
      Color background = ((VcsLogGraphTable)table).getStyle(row, column, text, hasFocus, isSelected).getBackground();
      assert background != null;
      myBorderColor = background;
      setForeground(UIUtil.getTableForeground(false));

      if (myUi.isShowRootNames()) {
        setText(text);
        isNarrow = false;
      }
      else {
        setText("");
        isNarrow = true;
      }

      return this;
    }

    @Override
    public void setBackground(Color bg) {
      myBorderColor = bg;
    }
  }

  private class StringCellRenderer extends ColoredTableCellRenderer {
    @Override
    protected void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
      if (value == null) {
        return;
      }
      append(value.toString(), applyHighlighters(this, row, column, value.toString(), hasFocus, selected));
      setBorder(null);
    }
  }

  private class MyDummyTableCellEditor implements TableCellEditor {
    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
      return null;
    }

    @Override
    public Object getCellEditorValue() {
      return null;
    }

    @Override
    public boolean isCellEditable(EventObject anEvent) {
      return false;
    }

    @Override
    public boolean shouldSelectCell(EventObject anEvent) {
      if (!(anEvent instanceof MouseEvent)) return true;
      MouseEvent e = (MouseEvent)anEvent;

      int row = PositionUtil.getRowIndex(e.getPoint(), getRowHeight());
      if (row > getRowCount() - 1) {
        return false;
      }
      Point point = calcPoint4Graph(e.getPoint());
      Collection<? extends PrintElement> printElements = getVisibleGraph().getRowInfo(row).getPrintElements();
      PrintElement printElement = myGraphCellPainter.getElementUnderCursor(printElements, point.x, point.y);
      return printElement == null;
    }

    @Override
    public boolean stopCellEditing() {
      return false;
    }

    @Override
    public void cancelCellEditing() {

    }

    @Override
    public void addCellEditorListener(CellEditorListener l) {

    }

    @Override
    public void removeCellEditorListener(CellEditorListener l) {

    }
  }

  private class InvisibleResizableHeader extends JBTable.JBTableHeader {
    @NotNull private final MyBasicTableHeaderUI myHeaderUI;
    @Nullable private Cursor myCursor = null;

    public InvisibleResizableHeader() {
      myHeaderUI = new MyBasicTableHeaderUI(this);
      // need a header to resize columns, so use header that is not visible
      setDefaultRenderer(new EmptyTableCellRenderer());
      setReorderingAllowed(false);
    }

    @Override
    public void setTable(JTable table) {
      JTable oldTable = getTable();
      if (oldTable != null) {
        oldTable.removeMouseListener(myHeaderUI);
        oldTable.removeMouseMotionListener(myHeaderUI);
      }

      super.setTable(table);

      if (table != null) {
        table.addMouseListener(myHeaderUI);
        table.addMouseMotionListener(myHeaderUI);
      }
    }

    @Override
    public void setCursor(@Nullable Cursor cursor) {
      /* this method and the next one fixes cursor:
         BasicTableHeaderUI.MouseInputHandler behaves like nobody else sets cursor
         so we remember what it set last time and keep it unaffected by other cursor changes in the table
       */
      JTable table = getTable();
      if (table != null) {
        table.setCursor(cursor);
        myCursor = cursor;
      }
      else {
        super.setCursor(cursor);
      }
    }

    @Override
    public Cursor getCursor() {
      if (myCursor == null) {
        JTable table = getTable();
        if (table == null) return super.getCursor();
        return table.getCursor();
      }
      return myCursor;
    }

    @NotNull
    @Override
    public Rectangle getHeaderRect(int column) {
      // if a header has zero height, mouse pointer can never be inside it, so we pretend it is one pixel high
      Rectangle headerRect = super.getHeaderRect(column);
      return new Rectangle(headerRect.x, headerRect.y, headerRect.width, 1);
    }
  }

  private static class EmptyTableCellRenderer implements TableCellRenderer {
    @NotNull
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      JPanel panel = new JPanel(new BorderLayout());
      panel.setMaximumSize(new Dimension(0, 0));
      return panel;
    }
  }

  // this class redirects events from the table to BasicTableHeaderUI.MouseInputHandler
  private static class MyBasicTableHeaderUI extends BasicTableHeaderUI implements MouseInputListener {
    public MyBasicTableHeaderUI(@NotNull JTableHeader tableHeader) {
      header = tableHeader;
      mouseInputListener = createMouseInputListener();
    }

    @NotNull
    private MouseEvent convertMouseEvent(@NotNull MouseEvent e) {
      // create a new event, almost exactly the same, but in the header
      return new MouseEvent(e.getComponent(), e.getID(), e.getWhen(), e.getModifiers(), e.getX(), 0, e.getXOnScreen(), header.getY(),
                            e.getClickCount(), e.isPopupTrigger(), e.getButton());
    }

    @Override
    public void mouseClicked(@NotNull MouseEvent e) {
    }

    @Override
    public void mousePressed(@NotNull MouseEvent e) {
      if (isOnBorder(e)) return;
      mouseInputListener.mousePressed(convertMouseEvent(e));
    }

    @Override
    public void mouseReleased(@NotNull MouseEvent e) {
      if (isOnBorder(e)) return;
      mouseInputListener.mouseReleased(convertMouseEvent(e));
    }

    @Override
    public void mouseEntered(@NotNull MouseEvent e) {
    }

    @Override
    public void mouseExited(@NotNull MouseEvent e) {
    }

    @Override
    public void mouseDragged(@NotNull MouseEvent e) {
      if (isOnBorder(e)) return;
      mouseInputListener.mouseDragged(convertMouseEvent(e));
    }

    @Override
    public void mouseMoved(@NotNull MouseEvent e) {
      if (isOnBorder(e)) return;
      mouseInputListener.mouseMoved(convertMouseEvent(e));
    }

    public boolean isOnBorder(@NotNull MouseEvent e) {
      return Math.abs(header.getTable().getWidth() - e.getPoint().x) <= JBUI.scale(3);
    }
  }

  private class MyListSelectionListener implements ListSelectionListener {
    @Override
    public void valueChanged(ListSelectionEvent e) {
      mySelection = null;
    }
  }

  private class MyProgressListener implements VcsLogProgress.ProgressListener {
    @NotNull private String myText = "";

    @Override
    public void progressStarted() {
      myText = getEmptyText().getText();
      getEmptyText().setText("Loading History...");
    }

    @Override
    public void progressStopped() {
      getEmptyText().setText(myText);
    }
  }
}
