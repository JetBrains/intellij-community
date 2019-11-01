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
import com.intellij.openapi.util.ValueKey;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.*;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.util.Consumer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBInsets;
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
import com.intellij.vcs.log.util.VcsLogUiUtil;
import com.intellij.vcs.log.util.VcsLogUtil;
import com.intellij.vcs.log.visible.VisiblePack;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
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

public class VcsLogGraphTable extends TableWithProgress implements DataProvider, CopyProvider {
  private static final Logger LOG = Logger.getInstance(VcsLogGraphTable.class);

  public static final int ROOT_INDICATOR_WHITE_WIDTH = 5;
  private static final int ROOT_INDICATOR_WIDTH = ROOT_INDICATOR_WHITE_WIDTH + 8;
  private static final int ROOT_NAME_MAX_WIDTH = 300;
  private static final int MAX_DEFAULT_DYNAMIC_COLUMN_WIDTH = 300;
  private static final int MAX_ROWS_TO_CALC_WIDTH = 1000;

  public static final String LOADING_COMMITS_TEXT = "Loading commits...";
  public static final String CHANGES_LOG_TEXT = "Changes log";

  @NotNull private final VcsLogData myLogData;
  @NotNull private final String myId;
  @NotNull private final VcsLogUiProperties myProperties;
  @NotNull private final VcsLogColorManager myColorManager;
  @NotNull private final MyDummyTableCellEditor myDummyEditor = new MyDummyTableCellEditor();
  @NotNull private final TableCellRenderer myDummyRenderer = new MyDefaultTableCellRenderer();
  @NotNull private final GraphCommitCellRenderer myGraphCommitCellRenderer;
  @NotNull private final GraphTableController myController;
  @NotNull private final StringCellRenderer myStringCellRenderer;
  @NotNull private final Set<VcsLogColumn> myInitializedColumns = EnumSet.noneOf(VcsLogColumn.class);

  @Nullable private Selection mySelection = null;

  @NotNull private final Collection<VcsLogHighlighter> myHighlighters = new ArrayList<>();

  // BasicTableUI.viewIndexForColumn uses reference equality, so we should not change TableColumn during DnD.
  private final List<TableColumn> myTableColumns = new ArrayList<>();

  public VcsLogGraphTable(@NotNull AbstractVcsLogUi ui,
                          @NotNull VcsLogData logData,
                          @NotNull Consumer<Runnable> requestMore) {
    super(new GraphTableModel(logData, requestMore, ui.getProperties()));
    myLogData = logData;
    myId = ui.getId();
    myProperties = ui.getProperties();
    myColorManager = ui.getColorManager();

    GraphCellPainter graphCellPainter = new SimpleGraphCellPainter(new DefaultColorGenerator()) {
      @Override
      protected int getRowHeight() {
        return VcsLogGraphTable.this.getRowHeight();
      }
    };
    myGraphCommitCellRenderer = new GraphCommitCellRenderer(logData, graphCellPainter, this);
    myStringCellRenderer = new StringCellRenderer();

    getEmptyText().setText(CHANGES_LOG_TEXT);
    myLogData.getProgress().addProgressIndicatorListener(new MyProgressListener(), ui);

    initColumns();

    setDefaultRenderer(FilePath.class, new RootCellRenderer(myProperties, myColorManager));
    setDefaultRenderer(GraphCommitCell.class, myGraphCommitCellRenderer);
    setDefaultRenderer(String.class, myStringCellRenderer);

    setShowVerticalLines(false);
    setShowHorizontalLines(false);
    setIntercellSpacing(JBUI.emptySize());
    setTableHeader(new InvisibleResizableHeader() {
      @Override
      protected boolean canMoveOrResizeColumn(int modelIndex) {
        return modelIndex != VcsLogColumn.ROOT.ordinal();
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
      column.setResizable(column.getModelIndex() != VcsLogColumn.ROOT.ordinal());
    }
  }

  protected boolean isSpeedSearchEnabled() {
    return Registry.is("vcs.log.speedsearch");
  }

  protected void updateEmptyText() {
    getEmptyText().setText(CHANGES_LOG_TEXT);
  }

  protected void setErrorEmptyText(@NotNull Throwable error, @NotNull String defaultText) {
    String message = ObjectUtils.chooseNotNull(error.getMessage(), defaultText);
    message = StringUtil.shortenTextWithEllipsis(message, 150, 0, true).replace('\n', ' ');
    getEmptyText().setText(message);
  }

  protected void appendActionToEmptyText(@NotNull String text, @NotNull Runnable action) {
    getEmptyText().appendSecondaryText(text, VcsLogUiUtil.getLinkAttributes(), e -> action.run());
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
    if (filtersChanged) myInitializedColumns.clear();
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
    if (VcsLogColumn.isValidColumnOrder(columnOrder)) return columnOrder;

    LOG.debug("Incorrect column order was saved in properties " + columnOrder + ", replacing it with default order.");
    saveColumnOrderToSettings();
    return null;
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

  private int getVisibleColumnCount() {
    return getColumnModel().getColumnCount();
  }

  public void reLayout() {
    if (getTableHeader().getResizingColumn() == null) {
      updateDynamicColumnsWidth();
      super.doLayout();
      repaint();
    }
  }

  public void forceReLayout(@NotNull VcsLogColumn column) {
    myInitializedColumns.remove(column);
    reLayout();
  }

  @Override
  public void doLayout() {
    if (getTableHeader().getResizingColumn() == null) {
      updateDynamicColumnsWidth();
    }
    super.doLayout();
  }

  public void resetColumnWidth(@NotNull VcsLogColumn column) {
    VcsLogUsageTriggerCollector.triggerUsage(VcsLogUsageTriggerCollector.VcsLogEvent.COLUMN_RESET, null);
    if (CommonUiProperties.getColumnWidth(myProperties, column) != -1) {
      CommonUiProperties.saveColumnWidth(myProperties, column, -1);
    }
    else {
      forceReLayout(column);
    }
  }

  private void updateDynamicColumnsWidth() {
    for (VcsLogColumn logColumn : VcsLogColumn.DYNAMIC_COLUMNS) {
      TableColumn column = getTableColumn(logColumn);
      if (column == null) continue;

      int width = CommonUiProperties.getColumnWidth(myProperties, logColumn);
      if (width <= 0 || width > getWidth()) {
        if (logColumn.getContentSample() != null || !myInitializedColumns.contains(logColumn)) {
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

    int size = getWidth();
    for (VcsLogColumn logColumn : VcsLogColumn.values()) {
      if (logColumn == VcsLogColumn.COMMIT) continue;
      TableColumn column = getTableColumn(logColumn);
      if (column != null) size -= column.getPreferredWidth();
    }

    getCommitColumn().setPreferredWidth(size);
  }

  private int getColumnWidthFromData(@NotNull TableColumn column) {
    int index = column.getModelIndex();
    VcsLogColumn logColumn = VcsLogColumn.fromOrdinal(index);

    String contentSample = logColumn.getContentSample();
    if (contentSample != null) {
      return getFontMetrics(getTableFont().deriveFont(Font.BOLD)).stringWidth(contentSample) +
             VcsLogUiUtil.getHorizontalTextPadding(myStringCellRenderer);
    }
    if (getModel().getRowCount() <= 0 || logColumn.getContentClass() != String.class) {
      return column.getPreferredWidth();
    }

    Font tableFont = getTableFont();
    // detect the longest value
    int maxRowsToCheck = Math.min(MAX_ROWS_TO_CALC_WIDTH, getRowCount());
    int maxValueWidth = 0;
    int unloaded = 0;
    for (int row = 0; row < maxRowsToCheck; row++) {
      String value = getModel().getValueAt(row, logColumn).toString();
      if (value.isEmpty()) {
        unloaded++;
        continue;
      }
      Font font = tableFont;
      VcsLogHighlighter.TextStyle style = getStyle(row, getColumnViewIndex(logColumn), false, false).getTextStyle();
      if (BOLD.equals(style)) {
        font = tableFont.deriveFont(Font.BOLD);
      }
      else if (ITALIC.equals(style)) {
        font = tableFont.deriveFont(Font.ITALIC);
      }
      maxValueWidth = Math.max(getFontMetrics(font).stringWidth(value + "*"), maxValueWidth);
    }

    int width = Math.min(maxValueWidth + VcsLogUiUtil.getHorizontalTextPadding(myStringCellRenderer),
                         JBUIScale.scale(MAX_DEFAULT_DYNAMIC_COLUMN_WIDTH));
    if (unloaded * 2 <= maxRowsToCheck) myInitializedColumns.add(logColumn);
    return width;
  }

  @Nullable
  public VcsLogColumn getVcsLogColumn(int viewIndex) {
    int index = convertColumnIndexToModel(viewIndex);
    return index < 0 ? null : VcsLogColumn.fromOrdinal(index);
  }

  public final int getColumnViewIndex(@NotNull VcsLogColumn column) {
    return convertColumnIndexToView(column.ordinal());
  }

  @Nullable
  public TableColumn getTableColumn(@NotNull VcsLogColumn column) {
    int viewIndex = getColumnViewIndex(column);
    return viewIndex != -1 ? getColumnModel().getColumn(viewIndex) : null;
  }

  @NotNull
  public TableColumn getRootColumn() {
    return Objects.requireNonNull(getTableColumn(VcsLogColumn.ROOT));
  }

  @NotNull
  public TableColumn getCommitColumn() {
    return Objects.requireNonNull(getTableColumn(VcsLogColumn.COMMIT));
  }

  private void setRootColumnSize() {
    TableColumn column = getRootColumn();
    int rootWidth;
    if (!myColorManager.hasMultiplePaths()) {
      rootWidth = 0;
    }
    else if (!isShowRootNames()) {
      rootWidth = JBUIScale.scale(ROOT_INDICATOR_WIDTH);
    }
    else {
      int width = 0;
      for (FilePath file : myColorManager.getPaths()) {
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
    VcsLogColumn column = getVcsLogColumn(columnAtPoint(event.getPoint()));
    if (column == null || row < 0) {
      return null;
    }
    if (column == VcsLogColumn.ROOT) {
      Object path = getValueAt(row, column.ordinal());
      if (path instanceof FilePath) {
        return "<html><b>" +
               myColorManager.getLongName((FilePath)path) +
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
    return ValueKey.match(dataId)
      .ifEq(PlatformDataKeys.COPY_PROVIDER).then(this)
      .ifEq(VcsDataKeys.VCS).thenGet(() -> {
        int[] selectedRows = getSelectedRows();
        if (selectedRows.length == 0 || selectedRows.length > VcsLogUtil.MAX_SELECTED_COMMITS) return null;
        Set<VirtualFile> roots = ContainerUtil.map2Set(Ints.asList(selectedRows), row -> getModel().getRootAtRow(row));
        if (roots.size() == 1) {
          return myLogData.getLogProvider(assertNotNull(getFirstItem(roots))).getSupportedVcs();
        }
        return null;
      })
      .ifEq(VcsLogDataKeys.VCS_LOG_BRANCHES).thenGet(() -> {
        int[] selectedRows = getSelectedRows();
        if (selectedRows.length != 1) return null;
        return getModel().getBranchesAtRow(selectedRows[0]);
      })
      .ifEq(VcsLogDataKeys.VCS_LOG_REFS).thenGet(() -> {
        int[] selectedRows = getSelectedRows();
        if (selectedRows.length != 1) return null;
        return getModel().getRefsAtRow(selectedRows[0]);
      })
      .ifEq(VcsDataKeys.PRESET_COMMIT_MESSAGE).thenGet(() -> {
        int[] selectedRows = getSelectedRows();
        if (selectedRows.length == 0) return null;

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(VcsLogUtil.MAX_SELECTED_COMMITS, selectedRows.length); i++) {
          sb.append(getModel().getValueAt(selectedRows[i], VcsLogColumn.COMMIT).toString());
          if (i != selectedRows.length - 1) sb.append("\n");
        }
        return sb.toString();
      })
      .orNull();
  }

  @Override
  public void performCopy(@NotNull DataContext dataContext) {
    StringBuilder sb = new StringBuilder();

    List<Integer> visibleColumns = getVisibleColumns();
    int[] selectedRows = getSelectedRows();
    for (int i = 0; i < Math.min(VcsLogUtil.MAX_SELECTED_COMMITS, selectedRows.length); i++) {
      int row = selectedRows[i];
      sb.append(StringUtil.join(visibleColumns, j -> {
        if (j == VcsLogColumn.ROOT.ordinal()) {
          return "";
        }
        else {
          return getModel().getValueAt(row, j).toString();
        }
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

    int commitId = rowInfo.getCommit();
    VcsShortCommitDetails details = myLogData.getMiniDetailsGetter().getCommitDataIfAvailable(commitId);
    if (details == null) return defaultStyle;

    List<VcsCommitStyle> styles = ContainerUtil.map(myHighlighters, highlighter -> highlighter.getStyle(commitId, details, selected));
    return VcsCommitStyleFactory.combine(ContainerUtil.append(styles, defaultStyle));
  }

  public void viewportSet(JViewport viewport) {
    viewport.addChangeListener(e -> {
      AbstractTableModel model = getModel();
      Couple<Integer> visibleRows = ScrollingUtil.getVisibleRows(this);
      if (visibleRows.first >= 0) {
        TableModelEvent evt = new TableModelEvent(model, visibleRows.first, visibleRows.second, VcsLogColumn.ROOT.ordinal());
        model.fireTableChanged(evt);
      }
      mySelection = null;
    });
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

  public void showTooltip(int row, @NotNull VcsLogColumn column) {
    myController.showTooltip(row, column);
  }

  public void setCompactReferencesView(boolean compact) {
    myGraphCommitCellRenderer.setCompactReferencesView(compact);
    repaint();
  }

  public void setShowTagNames(boolean showTagsNames) {
    myGraphCommitCellRenderer.setShowTagsNames(showTagsNames);
    repaint();
  }

  public void setLabelsLeftAligned(boolean leftAligned) {
    myGraphCommitCellRenderer.setLeftAligned(leftAligned);
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
    paintTopBottomBorder(g, x, y, width, height, false);
  }

  private void paintTopBottomBorder(@NotNull Graphics g, int x, int y, int width, int height, boolean isTopBorder) {
    int targetRow = isTopBorder ? 0 : getRowCount() - 1;
    if (targetRow >= 0 && targetRow < getRowCount()) {
      g.setColor(getStyle(targetRow, getColumnViewIndex(VcsLogColumn.COMMIT), hasFocus(), false).getBackground());
      g.fillRect(x, y, width, height);
      if (myColorManager.hasMultiplePaths()) {
        g.setColor(getPathBackgroundColor((FilePath)getModel().getValueAt(targetRow, VcsLogColumn.ROOT), myColorManager));

        int rootWidth = getRootColumn().getWidth();
        if (!isShowRootNames()) rootWidth -= JBUIScale.scale(ROOT_INDICATOR_WHITE_WIDTH);

        g.fillRect(x, y, rootWidth, height);
      }
    }
    else {
      g.setColor(getBaseStyle(targetRow, getColumnViewIndex(VcsLogColumn.COMMIT), hasFocus(), false).getBackground());
      g.fillRect(x, y, width, height);
    }
  }

  @NotNull
  public Border createTopBottomBorder(int top, int bottom) {
    return new MyTopBottomBorder(top, bottom);
  }

  public boolean isResizingColumns() {
    return getCursor() == Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR);
  }

  @NotNull
  public static JBColor getRootBackgroundColor(@NotNull VirtualFile root, @NotNull VcsLogColorManager colorManager) {
    return VcsLogColorManagerImpl.getBackgroundColor(colorManager.getRootColor(root));
  }

  @NotNull
  public static JBColor getPathBackgroundColor(@NotNull FilePath filePath, @NotNull VcsLogColorManager colorManager) {
    return VcsLogColorManagerImpl.getBackgroundColor(colorManager.getPathColor(filePath));
  }

  static Font getTableFont() {
    return UIManager.getFont("Table.font");
  }

  private static class MyDefaultTableCellRenderer extends DefaultTableCellRenderer {
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      component.setBackground(isSelected
                              ? table.hasFocus() ? UIUtil.getListSelectionBackground(true) : UIUtil.getListSelectionBackground(false)
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
      if (column == getColumnViewIndex(VcsLogColumn.COMMIT) || column == getColumnViewIndex(VcsLogColumn.AUTHOR)) {
        SpeedSearchUtil.applySpeedSearchHighlighting(table, this, false, selected);
      }
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

      return myController.shouldSelectCell((MouseEvent)anEvent);
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
      progressChanged(keys);
    }

    @Override
    public void progressChanged(@NotNull Collection<? extends VcsLogProgress.ProgressKey> keys) {
      if (VcsLogUiUtil.isProgressVisible(keys, myId)) {
        getEmptyText().setText(LOADING_COMMITS_TEXT);
      }
      else {
        updateEmptyText();
      }
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
        for (VcsLogColumn logColumn : VcsLogColumn.DYNAMIC_COLUMNS) {
          TableColumn column = getTableColumn(logColumn);
          if (evt.getSource().equals(column)) {
            CommonUiProperties.saveColumnWidth(myProperties, logColumn, column.getWidth());
          }
        }
      }
      super.propertyChange(evt);
    }

    @Override
    public void moveColumn(int columnIndex, int newIndex) {
      if (getVcsLogColumn(columnIndex) == VcsLogColumn.ROOT || getVcsLogColumn(newIndex) == VcsLogColumn.ROOT) return;
      super.moveColumn(columnIndex, newIndex);
      saveColumnOrderToSettings();
    }
  }

  private class MyTopBottomBorder implements Border {
    @NotNull private final JBInsets myInsets;

    private MyTopBottomBorder(int top, int bottom) {
      myInsets = JBUI.insets(top, 0, bottom, 0);
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
      if (myInsets.top > 0) paintTopBottomBorder(g, x, y, width, myInsets.top, true);
      if (myInsets.bottom > 0) paintTopBottomBorder(g, x, y + height - myInsets.bottom, width, myInsets.bottom, false);
    }

    @Override
    public Insets getBorderInsets(Component c) {
      return myInsets;
    }

    @Override
    public boolean isBorderOpaque() {
      return true;
    }
  }
}
