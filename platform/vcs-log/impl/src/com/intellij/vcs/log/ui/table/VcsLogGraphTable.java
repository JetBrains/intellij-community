// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.table;

import com.google.common.primitives.Ints;
import com.intellij.ide.CopyProvider;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.ui.LoadingDecorator;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.*;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.VcsCommitStyleFactory;
import com.intellij.vcs.log.VcsLogDataKeys;
import com.intellij.vcs.log.VcsLogHighlighter;
import com.intellij.vcs.log.VcsLogHighlighter.VcsCommitStyle;
import com.intellij.vcs.log.VcsShortCommitDetails;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.data.VcsLogProgress;
import com.intellij.vcs.log.graph.DefaultColorGenerator;
import com.intellij.vcs.log.graph.RowInfo;
import com.intellij.vcs.log.graph.RowType;
import com.intellij.vcs.log.graph.VisibleGraph;
import com.intellij.vcs.log.graph.actions.GraphAnswer;
import com.intellij.vcs.log.impl.CommonUiProperties;
import com.intellij.vcs.log.impl.VcsLogUiProperties;
import com.intellij.vcs.log.paint.GraphCellPainter;
import com.intellij.vcs.log.paint.SimpleGraphCellPainter;
import com.intellij.vcs.log.statistics.VcsLogUsageTriggerCollector;
import com.intellij.vcs.log.ui.AbstractVcsLogUi;
import com.intellij.vcs.log.ui.VcsLogColorManager;
import com.intellij.vcs.log.ui.VcsLogColorManagerImpl;
import com.intellij.vcs.log.ui.render.GraphCommitCell;
import com.intellij.vcs.log.ui.render.GraphCommitCellRenderer;
import com.intellij.vcs.log.util.VcsLogUtil;
import com.intellij.vcs.log.visible.VisiblePack;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.CellEditorListener;
import javax.swing.event.TableModelEvent;
import javax.swing.table.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.util.List;
import java.util.*;

import static com.intellij.util.ObjectUtils.assertNotNull;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static com.intellij.vcs.log.VcsCommitStyleFactory.createStyle;
import static com.intellij.vcs.log.VcsLogHighlighter.TextStyle.BOLD;
import static com.intellij.vcs.log.VcsLogHighlighter.TextStyle.ITALIC;
import static com.intellij.vcs.log.ui.table.GraphTableModel.*;

public class VcsLogGraphTable extends TableWithProgress implements DataProvider, CopyProvider {
  private static final Logger LOG = Logger.getInstance(VcsLogGraphTable.class);

  public static final int ROOT_INDICATOR_WHITE_WIDTH = 5;
  private static final int ROOT_INDICATOR_WIDTH = ROOT_INDICATOR_WHITE_WIDTH + 8;
  private static final int ROOT_NAME_MAX_WIDTH = 200;
  private static final int MAX_DEFAULT_AUTHOR_COLUMN_WIDTH = 300;
  private static final int MAX_ROWS_TO_CALC_WIDTH = 1000;

  public static final String LOADING_COMMITS_TEXT = "Loading commits...";
  public static final String CHANGES_LOG_TEXT = "Changes log";

  @NotNull private final VcsLogData myLogData;
  @NotNull private final VcsLogUiProperties myProperties;
  @NotNull private final VcsLogColorManager myColorManager;
  @NotNull private final MyDummyTableCellEditor myDummyEditor = new MyDummyTableCellEditor();
  @NotNull private final TableCellRenderer myDummyRenderer = new MyDefaultTableCellRenderer();
  @NotNull private final GraphCommitCellRenderer myGraphCommitCellRenderer;
  @NotNull private final GraphTableController myController;
  @NotNull private final StringCellRenderer myStringCellRenderer;
  private boolean myAuthorColumnInitialized = false;

  @Nullable private Selection mySelection = null;

  @NotNull private final Collection<VcsLogHighlighter> myHighlighters = new ArrayList<>();

  // BasicTableUI.viewIndexForColumn uses reference equality, so we should not change TableColumn during DnD.
  private final List<TableColumn> myTableColumns = new ArrayList<>();

  public VcsLogGraphTable(@NotNull AbstractVcsLogUi ui,
                          @NotNull VcsLogData logData,
                          @NotNull VisiblePack initialDataPack,
                          @NotNull Consumer<Runnable> requestMore) {
    super(new GraphTableModel(initialDataPack, logData, requestMore));
    myLogData = logData;
    myProperties = ui.getProperties();
    myColorManager = ui.getColorManager();

    GraphCellPainter graphCellPainter = new SimpleGraphCellPainter(new DefaultColorGenerator()) {
      @Override
      protected int getRowHeight() {
        return VcsLogGraphTable.this.getRowHeight();
      }
    };
    myGraphCommitCellRenderer = new GraphCommitCellRenderer(logData, graphCellPainter, this, true, false);
    myStringCellRenderer = new StringCellRenderer();

    getEmptyText().setText(CHANGES_LOG_TEXT);
    myLogData.getProgress().addProgressIndicatorListener(new MyProgressListener(), ui);

    initColumns();

    setDefaultRenderer(VirtualFile.class, new RootCellRenderer(myProperties, myColorManager));
    setDefaultRenderer(GraphCommitCell.class, myGraphCommitCellRenderer);
    setDefaultRenderer(String.class, myStringCellRenderer);

    setShowVerticalLines(false);
    setShowHorizontalLines(false);
    setIntercellSpacing(JBUI.emptySize());
    setTableHeader(new InvisibleResizableHeader() {
      @Override
      protected boolean canMoveOrResizeColumn(int modelIndex) {
        return modelIndex != ROOT_COLUMN;
      }
    });

    myController = new GraphTableController(logData, myColorManager, myProperties, this, graphCellPainter, myGraphCommitCellRenderer);

    getSelectionModel().addListSelectionListener(e -> mySelection = null);
    getColumnModel().setColumnSelectionAllowed(false);

    ScrollingUtil.installActions(this, false);
    new IndexSpeedSearch(myLogData.getProject(), myLogData.getIndex(), this) {
      @Override
      protected boolean isSpeedSearchEnabled() {
        return VcsLogGraphTable.this.isSpeedSearchEnabled() && super.isSpeedSearchEnabled();
      }
    };
  }

  private void initColumns() {
    setColumnModel(new MyTableColumnModel(myProperties));
    createDefaultColumnsFromModel();
    ContainerUtil.addAll(myTableColumns, getColumnModel().getColumns());
    setAutoCreateColumnsFromModel(false); // otherwise sizes are recalculated after each TableColumn re-initialization
    onColumnOrderSettingChanged();

    setRootColumnSize();

    for (TableColumn column : myTableColumns) {
      column.setResizable(column.getModelIndex() != ROOT_COLUMN);
    }
  }

  protected boolean isSpeedSearchEnabled() {
    return Registry.is("vcs.log.speedsearch");
  }

  protected void updateEmptyText() {
    getEmptyText().setText(CHANGES_LOG_TEXT);
  }

  public void updateDataPack(@NotNull VisiblePack visiblePack, boolean permGraphChanged) {
    Selection previousSelection = getSelection();
    boolean filtersChanged = !getModel().getVisiblePack().getFilters().equals(visiblePack.getFilters());

    getModel().setVisiblePack(visiblePack);
    previousSelection.restore(visiblePack.getVisibleGraph(), true, permGraphChanged);
    for (VcsLogHighlighter highlighter : myHighlighters) {
      highlighter.update(visiblePack, permGraphChanged);
    }

    if (!getEmptyText().getText().equals(LOADING_COMMITS_TEXT)) {
      updateEmptyText();
    }

    setPaintBusy(false);
    myAuthorColumnInitialized = myAuthorColumnInitialized && !filtersChanged;
    reLayout();
  }

  public void onColumnOrderSettingChanged() {
    TableColumnModel columnModel = getColumnModel();

    List<Integer> columnOrder = getColumnOrderFromProperties();
    if (columnOrder != null) {
      int columnCount = getVisibleColumnCount();
      for (int i = columnCount - 1; i >= 0; i--) {
        columnModel.removeColumn(columnModel.getColumn(i));
      }

      for (Integer expectedColumnIndex : columnOrder) {
        columnModel.addColumn(myTableColumns.get(expectedColumnIndex));
      }
    }

    reLayout();
  }

  @Nullable
  private List<Integer> getColumnOrderFromProperties() {
    if (!myProperties.exists(CommonUiProperties.COLUMN_ORDER)) return null;

    List<Integer> columnOrder = myProperties.get(CommonUiProperties.COLUMN_ORDER);
    if (isValidColumnOrder(columnOrder)) return columnOrder;

    LOG.debug("Incorrect column order was saved in properties " + columnOrder + ", replacing it with default order.");
    saveColumnOrderToSettings();
    return null;
  }

  private boolean isValidColumnOrder(@NotNull List<Integer> columnOrder) {
    int columnCount = getTotalColumnCount();
    if (!columnOrder.contains(ROOT_COLUMN)) return false;
    if (!columnOrder.contains(COMMIT_COLUMN)) return false;
    for (Integer index : columnOrder) {
      if (index == null || index < 0 || index >= columnCount) return false;
    }
    return true;
  }

  private void saveColumnOrderToSettings() {
    if (myProperties.exists(CommonUiProperties.COLUMN_ORDER)) {
      myProperties.set(CommonUiProperties.COLUMN_ORDER, getVisibleColumns());
    }
  }

  @NotNull
  private List<Integer> getVisibleColumns() {
    List<Integer> columnOrder = new ArrayList<>();

    for (int i = 0; i < getVisibleColumnCount(); i++) {
      columnOrder.add(getColumnModel().getColumn(i).getModelIndex());
    }
    return columnOrder;
  }

  private int getTotalColumnCount() {
    return getModel().getColumnCount();
  }

  private int getVisibleColumnCount() {
    return getColumnModel().getColumnCount();
  }

  public void reLayout() {
    if (getTableHeader().getResizingColumn() == null) {
      updateAuthorAndDataWidth();
      super.doLayout();
      repaint();
    }
  }

  public void forceReLayout(int column) {
    if (column == AUTHOR_COLUMN) myAuthorColumnInitialized = false;
    reLayout();
  }

  @Override
  public void doLayout() {
    if (getTableHeader().getResizingColumn() == null) {
      updateAuthorAndDataWidth();
    }
    super.doLayout();
  }

  public void resetColumnWidth(int column) {
    VcsLogUsageTriggerCollector.triggerUsage("ColumnWidthReset");
    if (CommonUiProperties.getColumnWidth(myProperties, column) != -1) {
      CommonUiProperties.saveColumnWidth(myProperties, column, -1);
    }
    else {
      forceReLayout(column);
    }
  }

  private void updateAuthorAndDataWidth() {
    for (int columnIndex : DYNAMIC_COLUMNS) {
      TableColumn column = getColumnByModelIndex(columnIndex);
      if (column == null) continue;

      int width = CommonUiProperties.getColumnWidth(myProperties, columnIndex);
      if (width <= 0 || width > getWidth()) {
        if (columnIndex != AUTHOR_COLUMN || !myAuthorColumnInitialized) {
          width = getColumnWidthFromData(column);
        }
        else {
          width = -1;
        }
      }

      if (width > 0 && width != column.getPreferredWidth()) {
        column.setPreferredWidth(width);
      }
    }

    updateCommitColumnWidth();
  }

  private int getColumnWidthFromData(@NotNull TableColumn column) {
    int index = column.getModelIndex();

    Font tableFont = getTableFont();
    if (index == AUTHOR_COLUMN) {
      if (getModel().getRowCount() <= 0) {
        return column.getPreferredWidth();
      }

      // detect author with the longest name
      int maxRowsToCheck = Math.min(MAX_ROWS_TO_CALC_WIDTH, getRowCount());
      int maxAuthorWidth = 0;
      int unloaded = 0;
      for (int row = 0; row < maxRowsToCheck; row++) {
        String value = getModel().getValueAt(row, AUTHOR_COLUMN).toString();
        if (value.isEmpty()) {
          unloaded++;
          continue;
        }
        Font font = tableFont;
        VcsLogHighlighter.TextStyle style = getStyle(row, convertColumnIndexToView(AUTHOR_COLUMN), false, false).getTextStyle();
        if (BOLD.equals(style)) {
          font = tableFont.deriveFont(Font.BOLD);
        }
        else if (ITALIC.equals(style)) {
          font = tableFont.deriveFont(Font.ITALIC);
        }
        maxAuthorWidth = Math.max(getFontMetrics(font).stringWidth(value + "*"), maxAuthorWidth);
      }

      int width = Math.min(maxAuthorWidth + myStringCellRenderer.getHorizontalTextPadding(),
                           JBUIScale.scale(MAX_DEFAULT_AUTHOR_COLUMN_WIDTH));
      if (unloaded * 2 <= maxRowsToCheck) myAuthorColumnInitialized = true;
      return width;
    }
    else if (index == DATE_COLUMN) {
      // all dates have nearly equal sizes
      return getFontMetrics(getTableFont().deriveFont(Font.BOLD)).stringWidth(DateFormatUtil.formatDateTime(new Date())) +
             myStringCellRenderer.getHorizontalTextPadding();
    }
    else if (index == HASH_COLUMN) {
      if (getModel().getRowCount() <= 0) {
        return column.getPreferredWidth();
      }
      // all hashes have nearly equal sizes
      String hash = getModel().getValueAt(0, HASH_COLUMN).toString();
      return getFontMetrics(getTableFont().deriveFont(Font.BOLD)).stringWidth(hash) +
             myStringCellRenderer.getHorizontalTextPadding();
    }
    LOG.error("Can only calculate author, hash or date columns width from data, yet given column " + index);
    return column.getPreferredWidth();
  }

  @Nullable
  public TableColumn getColumnByModelIndex(int index) {
    int viewIndex = convertColumnIndexToView(index);
    return viewIndex != -1 ? getColumnModel().getColumn(viewIndex) : null;
  }

  @NotNull
  public TableColumn getRootColumn() {
    //noinspection ConstantConditions
    return getColumnByModelIndex(ROOT_COLUMN);
  }

  @NotNull
  public TableColumn getCommitColumn() {
    //noinspection ConstantConditions
    return getColumnByModelIndex(COMMIT_COLUMN);
  }

  private static Font getTableFont() {
    return UIManager.getFont("Table.font");
  }

  private void updateCommitColumnWidth() {
    int size = getWidth();
    for (int i = 0; i < getTotalColumnCount(); i++) {
      if (i == COMMIT_COLUMN) continue;
      TableColumn column = getColumnByModelIndex(i);
      if (column != null) size -= column.getPreferredWidth();
    }

    getCommitColumn().setPreferredWidth(size);
  }

  private void setRootColumnSize() {
    TableColumn column = getRootColumn();
    int rootWidth;
    if (!myColorManager.isMultipleRoots()) {
      rootWidth = 0;
    }
    else if (!isShowRootNames()) {
      rootWidth = JBUIScale.scale(ROOT_INDICATOR_WIDTH);
    }
    else {
      int width = 0;
      for (VirtualFile file : myLogData.getRoots()) {
        Font tableFont = getTableFont();
        width = Math.max(getFontMetrics(tableFont).stringWidth(file.getName() + "  "), width);
      }
      rootWidth = Math.min(width, JBUIScale.scale(ROOT_NAME_MAX_WIDTH));
    }

    // NB: all further instructions and their order are important, otherwise the minimum size which is less than 15 won't be applied
    column.setMinWidth(rootWidth);
    column.setMaxWidth(rootWidth);
    column.setPreferredWidth(rootWidth);
  }

  public void rootColumnUpdated() {
    setRootColumnSize();
    reLayout();
    repaint();
  }

  @Override
  public String getToolTipText(@NotNull MouseEvent event) {
    int row = rowAtPoint(event.getPoint());
    int column = convertColumnIndexToModel(columnAtPoint(event.getPoint()));
    if (column < 0 || row < 0) {
      return null;
    }
    if (column == ROOT_COLUMN) {
      Object at = getValueAt(row, column);
      if (at instanceof VirtualFile) {
        return "<html><b>" +
               ((VirtualFile)at).getPresentableUrl() +
               "</b><br/>Click to " +
               (isShowRootNames() ? "collapse" : "expand") +
               "</html>";
      }
    }
    return null;
  }

  private boolean isShowRootNames() {
    return myProperties.exists(CommonUiProperties.SHOW_ROOT_NAMES) && myProperties.get(CommonUiProperties.SHOW_ROOT_NAMES);
  }

  public void jumpToRow(int rowIndex) {
    if (rowIndex >= 0 && rowIndex <= getRowCount() - 1) {
      scrollRectToVisible(getCellRect(rowIndex, 0, false));
      setRowSelectionInterval(rowIndex, rowIndex);
      if (!hasFocus()) {
        IdeFocusManager.getInstance(myLogData.getProject()).requestFocus(this, true);
      }
    }
  }

  @Nullable
  @Override
  public Object getData(@NotNull @NonNls String dataId) {
    if (PlatformDataKeys.COPY_PROVIDER.is(dataId)) {
      return this;
    }
    else if (VcsDataKeys.VCS.is(dataId)) {
      int[] selectedRows = getSelectedRows();
      if (selectedRows.length == 0 || selectedRows.length > VcsLogUtil.MAX_SELECTED_COMMITS) return null;
      Set<VirtualFile> roots = ContainerUtil.map2Set(Ints.asList(selectedRows), row -> getModel().getRoot(row));
      if (roots.size() == 1) {
        return myLogData.getLogProvider(assertNotNull(getFirstItem(roots))).getSupportedVcs();
      }
    }
    else if (VcsLogDataKeys.VCS_LOG_BRANCHES.is(dataId)) {
      int[] selectedRows = getSelectedRows();
      if (selectedRows.length != 1) return null;
      return getModel().getBranchesAtRow(selectedRows[0]);
    }
    else if (VcsLogDataKeys.VCS_LOG_REFS.is(dataId)) {
      int[] selectedRows = getSelectedRows();
      if (selectedRows.length != 1) return null;
      return getModel().getRefsAtRow(selectedRows[0]);
    }
    else if (VcsDataKeys.PRESET_COMMIT_MESSAGE.is(dataId)) {
      int[] selectedRows = getSelectedRows();
      if (selectedRows.length == 0) return null;

      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < Math.min(VcsLogUtil.MAX_SELECTED_COMMITS, selectedRows.length); i++) {
        sb.append(getModel().getValueAt(selectedRows[i], COMMIT_COLUMN).toString());
        if (i != selectedRows.length - 1) sb.append("\n");
      }
      return sb.toString();
    }
    return null;
  }

  @Override
  public void performCopy(@NotNull DataContext dataContext) {
    StringBuilder sb = new StringBuilder();

    List<Integer> visibleColumns = getVisibleColumns();
    int[] selectedRows = getSelectedRows();
    for (int i = 0; i < Math.min(VcsLogUtil.MAX_SELECTED_COMMITS, selectedRows.length); i++) {
      int row = selectedRows[i];
      sb.append(StringUtil.join(visibleColumns, j -> {
        if (j == ROOT_COLUMN) return "";
        else return getModel().getValueAt(row, j).toString();
      }, " "));
      if (i != selectedRows.length - 1) sb.append("\n");
    }

    CopyPasteManager.getInstance().setContents(new StringSelection(sb.toString()));
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

  @NotNull
  public SimpleTextAttributes applyHighlighters(@NotNull Component rendererComponent,
                                                int row,
                                                int column,
                                                boolean hasFocus,
                                                final boolean selected) {
    VcsCommitStyle style = getStyle(row, column, hasFocus, selected);

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

  @NotNull
  public VcsCommitStyle getBaseStyle(int row, int column, boolean hasFocus, boolean selected) {
    Component dummyRendererComponent = myDummyRenderer.getTableCellRendererComponent(this, "", selected, hasFocus, row, column);
    return createStyle(dummyRendererComponent.getForeground(), dummyRendererComponent.getBackground(),
                       VcsLogHighlighter.TextStyle.NORMAL);
  }

  @NotNull
  VcsCommitStyle getStyle(int row, int column, boolean hasFocus, boolean selected) {
    VcsCommitStyle baseStyle = getBaseStyle(row, column, hasFocus, selected);

    VisibleGraph<Integer> visibleGraph = getVisibleGraph();
    if (row < 0 || row >= visibleGraph.getVisibleCommitCount()) {
      LOG.error("Visible graph has " + visibleGraph.getVisibleCommitCount() + " commits, yet we want row " + row);
      return baseStyle;
    }

    RowInfo<Integer> rowInfo = visibleGraph.getRowInfo(row);

    VcsCommitStyle defaultStyle = createStyle(rowInfo.getRowType() == RowType.UNMATCHED ? JBColor.GRAY : baseStyle.getForeground(),
                                              baseStyle.getBackground(), VcsLogHighlighter.TextStyle.NORMAL);

    Integer commitId = rowInfo.getCommit();
    VcsShortCommitDetails details = myLogData.getMiniDetailsGetter().getCommitDataIfAvailable(commitId);
    if (details == null) return defaultStyle;

    List<VcsCommitStyle> styles = ContainerUtil.map(myHighlighters, highlighter -> highlighter.getStyle(commitId, details, selected));
    return VcsCommitStyleFactory.combine(ContainerUtil.append(styles, defaultStyle));
  }

  public void viewportSet(JViewport viewport) {
    viewport.addChangeListener(e -> {
      AbstractTableModel model = getModel();
      Couple<Integer> visibleRows = ScrollingUtil.getVisibleRows(this);
      if (visibleRows.first >= 0) model.fireTableChanged(new TableModelEvent(model, visibleRows.first, visibleRows.second, ROOT_COLUMN));
      mySelection = null;
    });
  }

  public static JBColor getRootBackgroundColor(@NotNull VirtualFile root, @NotNull VcsLogColorManager colorManager) {
    return VcsLogColorManagerImpl.getBackgroundColor(colorManager.getRootColor(root));
  }

  @Override
  public void setCursor(Cursor cursor) {
    super.setCursor(cursor);
    Component layeredPane = ComponentUtil.findParentByCondition(this, component -> component instanceof LoadingDecorator.CursorAware);
    if (layeredPane != null) {
      layeredPane.setCursor(cursor);
    }
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

  public void handleAnswer(@Nullable GraphAnswer<Integer> answer) {
    myController.handleGraphAnswer(answer, true, null, null);
  }

  public void showTooltip(int row) {
    myController.showTooltip(row);
  }

  public void setCompactReferencesView(boolean compact) {
    myGraphCommitCellRenderer.setCompactReferencesView(compact);
    repaint();
  }

  public void setShowTagNames(boolean showTagsNames) {
    myGraphCommitCellRenderer.setShowTagsNames(showTagsNames);
    repaint();
  }

  @NotNull
  public VisibleGraph<Integer> getVisibleGraph() {
    return getModel().getVisiblePack().getVisibleGraph();
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
      g.setColor(getStyle(lastRow, convertColumnIndexToView(COMMIT_COLUMN), hasFocus(), false).getBackground());
      g.fillRect(x, y, width, height);
      if (myColorManager.isMultipleRoots()) {
        g.setColor(getRootBackgroundColor(getModel().getRoot(lastRow), myColorManager));

        int rootWidth = getRootColumn().getWidth();
        if (!isShowRootNames()) rootWidth -= JBUIScale.scale(ROOT_INDICATOR_WHITE_WIDTH);

        g.fillRect(x, y, rootWidth, height);
      }
    }
    else {
      g.setColor(getBaseStyle(lastRow, convertColumnIndexToView(COMMIT_COLUMN), hasFocus(), false).getBackground());
      g.fillRect(x, y, width, height);
    }
  }

  public boolean isResizingColumns() {
    return getCursor() == Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR);
  }

  private static class MyDefaultTableCellRenderer extends DefaultTableCellRenderer {
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      component.setBackground(isSelected
                              ? table.hasFocus() ? UIUtil.getListSelectionBackground() : UIUtil.getListUnfocusedSelectionBackground()
                              : UIUtil.getListBackground());
      return component;
    }
  }

  private class StringCellRenderer extends ColoredTableCellRenderer {
    @Override
    protected void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
      setBorder(null);
      if (value == null) {
        return;
      }
      append(value.toString(), applyHighlighters(this, row, column, hasFocus, selected));
      if (column == convertColumnIndexToView(COMMIT_COLUMN) || column == convertColumnIndexToView(AUTHOR_COLUMN)) {
        SpeedSearchUtil.applySpeedSearchHighlighting(table, this, false, selected);
      }
    }

    public int getHorizontalTextPadding() {
      Insets borderInsets = getMyBorder().getBorderInsets(this);
      Insets ipad = getIpad();
      return borderInsets.left + borderInsets.right + ipad.left + ipad.right;
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

      return myController.findPrintElement((MouseEvent)anEvent) == null;
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

  private class MyProgressListener implements VcsLogProgress.ProgressListener {
    @Override
    public void progressStarted(@NotNull Collection<? extends VcsLogProgress.ProgressKey> keys) {
      getEmptyText().setText(LOADING_COMMITS_TEXT);
    }

    @Override
    public void progressChanged(@NotNull Collection<? extends VcsLogProgress.ProgressKey> keys) {
    }

    @Override
    public void progressStopped() {
      updateEmptyText();
    }
  }

  private class MyTableColumnModel extends DefaultTableColumnModel {
    @NotNull private final VcsLogUiProperties myProperties;

    MyTableColumnModel(@NotNull VcsLogUiProperties properties) {
      myProperties = properties;
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
      // for some reason if I just add a PropertyChangeListener to a column it's not called
      // and TableColumnModelListener.columnMarginChanged does not provide any information which column was changed
      if (getTableHeader().getResizingColumn() == null) return;
      if ("width".equals(evt.getPropertyName())) {
        for (int columnIndex : DYNAMIC_COLUMNS) {
          TableColumn column = getColumnByModelIndex(columnIndex);
          if (evt.getSource().equals(column)) {
            CommonUiProperties.saveColumnWidth(myProperties, columnIndex, column.getWidth());
          }
        }
      }
      super.propertyChange(evt);
    }

    @Override
    public void moveColumn(int columnIndex, int newIndex) {
      if (convertColumnIndexToModel(columnIndex) == ROOT_COLUMN || convertColumnIndexToModel(newIndex) == ROOT_COLUMN) return;
      super.moveColumn(columnIndex, newIndex);
      saveColumnOrderToSettings();
    }
  }
}
