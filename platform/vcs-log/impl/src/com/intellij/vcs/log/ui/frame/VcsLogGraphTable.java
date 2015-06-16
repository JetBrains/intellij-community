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

import com.intellij.icons.AllIcons;
import com.intellij.ide.CopyProvider;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesTreeBrowser;
import com.intellij.openapi.vcs.changes.issueLinks.TableLinkMouseListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.table.JBTable;
import com.intellij.util.Function;
import com.intellij.util.NotNullProducer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.VcsCommitStyleFactory;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.VcsLogHighlighter;
import com.intellij.vcs.log.data.VcsLogDataHolder;
import com.intellij.vcs.log.data.VisiblePack;
import com.intellij.vcs.log.graph.*;
import com.intellij.vcs.log.graph.actions.GraphAction;
import com.intellij.vcs.log.graph.actions.GraphAnswer;
import com.intellij.vcs.log.printer.idea.GraphCellPainter;
import com.intellij.vcs.log.printer.idea.PositionUtil;
import com.intellij.vcs.log.printer.idea.SimpleGraphCellPainter;
import com.intellij.vcs.log.ui.VcsLogColorManager;
import com.intellij.vcs.log.ui.VcsLogUiImpl;
import com.intellij.vcs.log.ui.render.GraphCommitCell;
import com.intellij.vcs.log.ui.render.GraphCommitCellRender;
import com.intellij.vcs.log.ui.render.RefPainter;
import com.intellij.vcs.log.ui.tables.GraphTableModel;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntProcedure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.swing.table.DefaultTableCellHeaderRenderer;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

public class VcsLogGraphTable extends JBTable implements TypeSafeDataProvider, CopyProvider {
  private static final Logger LOG = Logger.getInstance(VcsLogGraphTable.class);

  public static final int ROOT_INDICATOR_COLORED_WIDTH = 8;
  public static final int ROOT_INDICATOR_WHITE_WIDTH = 5;
  private static final int ROOT_INDICATOR_WIDTH = ROOT_INDICATOR_WHITE_WIDTH + ROOT_INDICATOR_COLORED_WIDTH;
  private static final int ROOT_NAME_MAX_WIDTH = 200;
  private static final int MAX_DEFAULT_AUTHOR_COLUMN_WIDTH = 200;
  private static final int MAX_ROWS_TO_CALC_WIDTH = 1000;
  private static final int MAX_ROWS_TO_CALC_OFFSET = 100;

  @NotNull private final VcsLogUiImpl myUI;
  private final VcsLogDataHolder myLogDataHolder;
  private final MyDummyTableCellEditor myDummyEditor = new MyDummyTableCellEditor();
  @NotNull private final TableCellRenderer myDummyRenderer = new DefaultTableCellRenderer();
  private final TableModelListener myColumnSizeInitializer = new TableModelListener() {
    @Override
    public void tableChanged(TableModelEvent e) {
      if (initColumnSize()) {
        getModel().removeTableModelListener(this);
      }
    }
  };

  private boolean myColumnsSizeInitialized = false;

  @NotNull private final Collection<VcsLogHighlighter> myHighlighters = ContainerUtil.newArrayList();

  private final GraphCellPainter myGraphCellPainter = new SimpleGraphCellPainter(new com.intellij.vcs.log.printer.idea.ColorGenerator() {
    @Override
    public Color getColor(int colorId) {
      return ColorGenerator.getColor(colorId);
    }
  }) {
    @Override
    protected int getRowHeight() {
      return VcsLogGraphTable.this.getRowHeight();
    }
  };

  public VcsLogGraphTable(@NotNull VcsLogUiImpl UI, @NotNull final VcsLogDataHolder logDataHolder, @NotNull VisiblePack initialDataPack) {
    super();
    myUI = UI;
    myLogDataHolder = logDataHolder;

    setDefaultRenderer(VirtualFile.class, new RootCellRenderer(myUI));
    setDefaultRenderer(GraphCommitCell.class, new GraphCommitCellRender(myUI.getColorManager(), logDataHolder, myGraphCellPainter, this));
    setDefaultRenderer(String.class, new StringCellRenderer());

    setRowHeight(RefPainter.REF_HEIGHT);
    setShowHorizontalLines(false);
    setIntercellSpacing(JBUI.emptySize());

    MouseAdapter mouseAdapter = new MyMouseAdapter();
    addMouseMotionListener(mouseAdapter);
    addMouseListener(mouseAdapter);
    MyHeaderMouseAdapter headerAdapter = new MyHeaderMouseAdapter();
    getTableHeader().addMouseListener(headerAdapter);
    getTableHeader().addMouseMotionListener(headerAdapter);

    getTableHeader().setReorderingAllowed(false);

    PopupHandler.installPopupHandler(this, VcsLogUiImpl.POPUP_ACTION_GROUP, VcsLogUiImpl.VCS_LOG_TABLE_PLACE);
    TableScrollingUtil.installActions(this, false);

    setModel(new GraphTableModel(initialDataPack, myLogDataHolder, myUI));
  }

  public void updateDataPack(@NotNull VisiblePack visiblePack) {
    VcsLogGraphTable.Selection previousSelection = getSelection();
    getGraphTableModel().setVisiblePack(visiblePack);
    previousSelection.restore(visiblePack.getVisibleGraph(), true);
    setPaintBusy(false);
  }

  @Override
  public void setModel(@NotNull TableModel model) {
    super.setModel(model);
    if (getModel().getRowCount() > 0) {
      initColumnSize();
    }
    else {
      model.addTableModelListener(myColumnSizeInitializer);
    }
  }

  private boolean initColumnSize() {
    if (!myColumnsSizeInitialized && getModel().getRowCount() > 0) {
      myColumnsSizeInitialized = true;
      setColumnPreferredSize();
      setAutoCreateColumnsFromModel(false); // otherwise sizes are recalculated after each TableColumn re-initialization

      getColumnModel().getColumn(GraphTableModel.ROOT_COLUMN).setHeaderRenderer(new RootHeaderRenderer());
      return true;
    }
    return false;
  }

  private void setColumnPreferredSize() {
    for (int i = 0; i < getColumnCount(); i++) {
      TableColumn column = getColumnModel().getColumn(i);
      if (i == GraphTableModel.ROOT_COLUMN) { // thin stripe, or root name, or nothing
        setRootColumnSize(column);
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

  private void setRootColumnSize(TableColumn column) {
    int rootWidth;
    if (!myUI.isMultipleRoots()) {
      rootWidth = 0;
    }
    else if (!myUI.isShowRootNames()) {
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
    for (VirtualFile file : myLogDataHolder.getRoots()) {
      Font tableFont = UIManager.getFont("Table.font");
      width = Math.max(getFontMetrics(tableFont).stringWidth(file.getName() + "  "), width);
    }
    return width;
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
        return "<html><b>" +
               ((VirtualFile)at).getPresentableUrl() +
               "</b><br/>Click to " +
               (myUI.isShowRootNames() ? "collapse" : "expand") +
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
    List<VcsFullCommitDetails> details = myUI.getVcsLog().getSelectedDetails();
    if (!details.isEmpty()) {
      CopyPasteManager.getInstance().setContents(new StringSelection(StringUtil.join(details, new Function<VcsFullCommitDetails, String>() {
        @Override
        public String fun(VcsFullCommitDetails details) {
          return details.getSubject();
        }
      }, "\n")));
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

  private VcsLogHighlighter.VcsCommitStyle getStyle(int row, int column, String text, boolean hasFocus, final boolean selected) {
    Component dummyRendererComponent = myDummyRenderer.getTableCellRendererComponent(this, text, selected, hasFocus, row, column);

    VisibleGraph<Integer> visibleGraph = getVisibleGraph();
    if (row < 0 || row >= visibleGraph.getVisibleCommitCount()) {
      LOG.error("Visible graph has " + visibleGraph.getVisibleCommitCount() + " commits, yet we want row " + row);
      return VcsCommitStyleFactory
        .createStyle(dummyRendererComponent.getForeground(), dummyRendererComponent.getBackground(), VcsLogHighlighter.TextStyle.NORMAL);
    }

    final RowInfo<Integer> rowInfo = visibleGraph.getRowInfo(row);

    VcsLogHighlighter.VcsCommitStyle defaultStyle = VcsCommitStyleFactory
      .createStyle(rowInfo.getRowType() == RowType.UNMATCHED ? JBColor.GRAY : dummyRendererComponent.getForeground(),
                   dummyRendererComponent.getBackground(), VcsLogHighlighter.TextStyle.NORMAL);

    List<VcsLogHighlighter.VcsCommitStyle> styles =
      ContainerUtil.map(myHighlighters, new Function<VcsLogHighlighter, VcsLogHighlighter.VcsCommitStyle>() {
        @Override
        public VcsLogHighlighter.VcsCommitStyle fun(VcsLogHighlighter highlighter) {
          return highlighter.getStyle(rowInfo.getCommit(), selected);
        }
      });

    return VcsCommitStyleFactory.combine(ContainerUtil.append(styles, defaultStyle));
  }

  public void viewportSet(JViewport viewport) {
    viewport.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        TableModel model = getModel();
        if (model instanceof AbstractTableModel) {
          Couple<Integer> visibleRows = TableScrollingUtil.getVisibleRows(VcsLogGraphTable.this);
          ((AbstractTableModel)model)
            .fireTableChanged(new TableModelEvent(model, visibleRows.first - 1, visibleRows.second, GraphTableModel.ROOT_COLUMN));
        }
      }
    });
  }

  private boolean expandOrCollapseRoots(@NotNull MouseEvent e) {
    TableColumn column = getRootColumnOrNull(e);
    if (column != null) {
      myUI.setShowRootNames(!myUI.isShowRootNames());
      return true;
    }
    return false;
  }

  public void rootColumnUpdated() {
    setColumnPreferredSize();
    setRootColumnSize(getColumnModel().getColumn(GraphTableModel.ROOT_COLUMN));
  }

  @Nullable
  private TableColumn getRootColumnOrNull(@NotNull MouseEvent e) {
    if (!myLogDataHolder.isMultiRoot()) return null;
    int column = convertColumnIndexToModel(columnAtPoint(e.getPoint()));
    if (column == GraphTableModel.ROOT_COLUMN) {
      return getColumnModel().getColumn(column);
    }
    return null;
  }

  public static JBColor getRootBackgroundColor(@NotNull VirtualFile root, @NotNull VcsLogColorManager colorManager) {
    final Color rootColor = colorManager.getRootColor(root);
    return new JBColor(new NotNullProducer<Color>() {
      @NotNull
      @Override
      public Color produce() {
        return ColorUtil.mix(rootColor, UIUtil.getTableBackground(), 0.75);
      }
    });
  }

  public void handleAnswer(@Nullable GraphAnswer<Integer> answer, boolean dataCouldChange, @Nullable Selection previousSelection) {
    if (dataCouldChange) {
      GraphTableModel graphTableModel = (GraphTableModel)getModel();

      graphTableModel.fireTableDataChanged();

      // since fireTableDataChanged clears selection we restore it here
      if (previousSelection != null) {
        previousSelection.restore(getVisibleGraph(), answer == null || answer.getCommitToJump() != null);
      }
    }

    myUI.repaintUI(); // in case of repaintUI doing something more than just repainting this table in some distant future

    if (answer == null) {
      return;
    }

    if (answer.getCursorToSet() != null) {
      setCursor(answer.getCursorToSet());
    }
    if (answer.getCommitToJump() != null) {
      Integer row = getGraphTableModel().getVisiblePack().getVisibleGraph().getVisibleRowIndex(answer.getCommitToJump());
      if (row != null && row >= 0) {
        jumpToRow(row);
      }
      // TODO wait for the full log and then jump
    }
  }

  @NotNull
  private GraphTableModel getGraphTableModel() {
    return (GraphTableModel)getModel();
  }

  @NotNull
  public Selection getSelection() {
    return new Selection(this);
  }

  private static class Selection {
    @NotNull private final VcsLogGraphTable myTable;
    @NotNull private final TIntHashSet mySelectedCommits;
    @Nullable private final Integer myVisibleSelectedCommit;
    @Nullable private final Integer myDelta;
    private final boolean myScrollToTop;


    public Selection(@NotNull VcsLogGraphTable table) {
      myTable = table;
      List<Integer> selectedRows = ContainerUtil.sorted(toList(myTable.getSelectedRows()));
      Couple<Integer> visibleRows = TableScrollingUtil.getVisibleRows(myTable);
      myScrollToTop = visibleRows.first - 1 == 0;

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

    private static List<Integer> toList(int[] array) {
      List<Integer> result = ContainerUtil.newArrayList();
      for (int i : array) {
        result.add(i);
      }
      return result;
    }

    public void restore(@NotNull VisibleGraph<Integer> newVisibleGraph, boolean scrollToSelection) {
      Pair<TIntHashSet, Integer> toSelectAndScroll = findRowsToSelectAndScroll(myTable.getGraphTableModel(), newVisibleGraph);
      if (!toSelectAndScroll.first.isEmpty()) {
        myTable.getSelectionModel().setValueIsAdjusting(true);
        toSelectAndScroll.first.forEach(new TIntProcedure() {
          @Override
          public boolean execute(int row) {
            myTable.addRowSelectionInterval(row, row);
            return true;
          }
        });
        myTable.getSelectionModel().setValueIsAdjusting(false);
      }
      if (scrollToSelection) {
        if (myScrollToTop) {
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

  private class MyHeaderMouseAdapter extends MouseAdapter {
    @Override
    public void mouseMoved(MouseEvent e) {
      Component component = e.getComponent();
      if (component != null) {
        if (getRootColumnOrNull(e) != null) {
          component.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }
        else {
          component.setCursor(null);
        }
      }
    }

    @Override
    public void mouseClicked(MouseEvent e) {
      if (e.getClickCount() == 1) {
        expandOrCollapseRoots(e);
      }
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

      if (e.getClickCount() == 1 && !expandOrCollapseRoots(e)) {
        performAction(e, GraphAction.Type.MOUSE_CLICK);
      }
    }

    @Override
    public void mouseMoved(MouseEvent e) {
      if (isAboveLink(e) || isAboveRoots(e)) {
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      }
      else {
        performAction(e, GraphAction.Type.MOUSE_OVER);
      }
    }

    private void performAction(@NotNull MouseEvent e, @NotNull final GraphAction.Type actionType) {
      int row = PositionUtil.getRowIndex(e.getPoint(), getRowHeight());
      if (row > getRowCount() - 1) {
        return;
      }
      Point point = calcPoint4Graph(e.getPoint());
      Collection<? extends PrintElement> printElements = getVisibleGraph().getRowInfo(row).getPrintElements();
      PrintElement printElement = myGraphCellPainter.mouseOver(printElements, point.x, point.y);

      Selection previousSelection = getSelection();
      GraphAnswer<Integer> answer =
        getVisibleGraph().getActionController().performAction(new GraphAction.GraphActionImpl(printElement, actionType));
      handleAnswer(answer, actionType == GraphAction.Type.MOUSE_CLICK && printElement != null, previousSelection);
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

  @NotNull
  public VisibleGraph<Integer> getVisibleGraph() {
    return getGraphTableModel().getVisiblePack().getVisibleGraph();
  }

  @NotNull
  private Point calcPoint4Graph(@NotNull Point clickPoint) {
    return new Point(clickPoint.x - getXOffset(), PositionUtil.getYInsideRow(clickPoint, getRowHeight()));
  }

  private int getXOffset() {
    TableColumn rootColumn = getColumnModel().getColumn(GraphTableModel.ROOT_COLUMN);
    return myLogDataHolder.isMultiRoot() ? rootColumn.getWidth() : 0;
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
        int readableRow = TableScrollingUtil.getReadableRow(table, Math.round(myUi.getTable().getRowHeight() * 0.5f));
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

  @Override
  public TableCellEditor getCellEditor() {
    // this fixes selection problems by prohibiting selection when user clicks on graph (CellEditor does that)
    // what is fun about this code is that if you set cell editor in constructor with setCellEditor method it would not work
    return myDummyEditor;
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

  private class RootHeaderRenderer extends DefaultTableCellHeaderRenderer {
    private final Icon myIcon = AllIcons.General.ComboArrowRight;

    @NotNull
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

      if (myUI.isShowRootNames()) {
        setIcon(null);
        setText("Roots");
      }
      else {
        setIcon(myIcon);
        setText("");
      }
      return this;
    }

    @Override
    public Dimension getPreferredSize() {
      Dimension dimension = super.getPreferredSize();
      if (getText() == null || getText().isEmpty()) {
        setText("Roots");
        dimension.height = super.getPreferredSize().height;
        setText("");
        return dimension;
      }
      return dimension;
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
      PrintElement printElement = myGraphCellPainter.mouseOver(printElements, point.x, point.y);
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
}
