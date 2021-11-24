// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.table;

import com.google.common.primitives.Ints;
import com.intellij.ide.CopyProvider;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.ui.LoadingDecorator;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.ValueKey;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.issueLinks.TableLinkMouseListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.*;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.Consumer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.VcsLogHighlighter.VcsCommitStyle;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.data.VcsLogProgress;
import com.intellij.vcs.log.graph.RowInfo;
import com.intellij.vcs.log.graph.RowType;
import com.intellij.vcs.log.graph.VisibleGraph;
import com.intellij.vcs.log.graph.actions.GraphAnswer;
import com.intellij.vcs.log.impl.CommonUiProperties;
import com.intellij.vcs.log.impl.VcsLogUiProperties;
import com.intellij.vcs.log.paint.PositionUtil;
import com.intellij.vcs.log.statistics.VcsLogUsageTriggerCollector;
import com.intellij.vcs.log.ui.VcsLogColorManager;
import com.intellij.vcs.log.ui.VcsLogColorManagerImpl;
import com.intellij.vcs.log.ui.render.GraphCommitCellRenderer;
import com.intellij.vcs.log.ui.render.SimpleColoredComponentLinkMouseListener;
import com.intellij.vcs.log.ui.table.column.*;
import com.intellij.vcs.log.util.VcsLogUiUtil;
import com.intellij.vcs.log.util.VcsLogUtil;
import com.intellij.vcs.log.visible.VisiblePack;
import org.jetbrains.annotations.Nls;
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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.util.List;
import java.util.*;

import static com.intellij.ui.hover.TableHoverListener.getHoveredRow;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static com.intellij.vcs.log.VcsCommitStyleFactory.createStyle;
import static com.intellij.vcs.log.VcsLogHighlighter.TextStyle.BOLD;
import static com.intellij.vcs.log.VcsLogHighlighter.TextStyle.ITALIC;
import static com.intellij.vcs.log.ui.table.column.VcsLogColumnUtilKt.*;

public class VcsLogGraphTable extends TableWithProgress implements DataProvider, CopyProvider, Disposable {
  private static final Logger LOG = Logger.getInstance(VcsLogGraphTable.class);

  public static final int ROOT_INDICATOR_WHITE_WIDTH = 5;
  private static final int ROOT_INDICATOR_WIDTH = ROOT_INDICATOR_WHITE_WIDTH + 8;
  private static final int ROOT_NAME_MAX_WIDTH = 300;
  private static final int MAX_DEFAULT_DYNAMIC_COLUMN_WIDTH = 300;
  private static final int MAX_ROWS_TO_CALC_WIDTH = 1000;

  @SuppressWarnings("UseJBColor")
  private static final Color DEFAULT_HOVERED_BACKGROUND = new JBColor(ColorUtil.withAlpha(new Color(0xC3D2E3), 0.4),
                                                                      new Color(0x464A4D));
  private static final Color HOVERED_BACKGROUND = JBColor.namedColor("VersionControl.Log.Commit.hoveredBackground",
                                                                     DEFAULT_HOVERED_BACKGROUND);

  @NotNull private final VcsLogData myLogData;
  @NotNull private final String myId;
  @NotNull private final VcsLogUiProperties myProperties;
  @NotNull private final VcsLogColorManager myColorManager;

  @NotNull private final MyDummyTableCellEditor myDummyEditor = new MyDummyTableCellEditor();
  @NotNull private final BaseStyleProvider myBaseStyleProvider;
  @NotNull private final GraphCommitCellRenderer myGraphCommitCellRenderer;
  @NotNull private final MyMouseAdapter myMouseAdapter;

  // BasicTableUI.viewIndexForColumn uses reference equality, so we should not change TableColumn during DnD.
  @NotNull private final Map<VcsLogColumn<?>, TableColumn> myTableColumns = new HashMap<>();
  @NotNull private final Set<VcsLogColumn<?>> myInitializedColumns = new HashSet<>();

  @NotNull private final Collection<VcsLogHighlighter> myHighlighters = new LinkedHashSet<>();

  @Nullable private Selection mySelection = null;

  public VcsLogGraphTable(@NotNull String logId, @NotNull VcsLogData logData,
                          @NotNull VcsLogUiProperties uiProperties, @NotNull VcsLogColorManager colorManager,
                          @NotNull Consumer<Runnable> requestMore, @NotNull Disposable disposable) {
    super(new GraphTableModel(logData, requestMore, uiProperties));
    Disposer.register(disposable, this);

    myLogData = logData;
    myId = logId;
    myProperties = uiProperties;
    myColorManager = colorManager;

    myBaseStyleProvider = new BaseStyleProvider(this);

    getEmptyText().setText(VcsLogBundle.message("vcs.log.default.status"));
    myLogData.getProgress().addProgressIndicatorListener(new MyProgressListener(), this);

    initColumnModel();
    onColumnOrderSettingChanged();
    setRootColumnSize();
    myGraphCommitCellRenderer = (GraphCommitCellRenderer)myTableColumns.get(Commit.INSTANCE).getCellRenderer();
    VcsLogColumnManager.getInstance().addCurrentColumnsListener(this, new MyCurrentColumnsListener());
    VcsLogColumnManager.getInstance().addColumnModelListener(this, (column, index) -> {
      getModel().fireTableStructureChanged();
    });

    setShowVerticalLines(false);
    setShowHorizontalLines(false);
    setIntercellSpacing(JBUI.emptySize());
    setTableHeader(new InvisibleResizableHeader() {
      @Override
      protected boolean canMoveOrResizeColumn(int modelIndex) {
        return modelIndex != VcsLogColumnManager.getInstance().getModelIndex(Root.INSTANCE);
      }
    });

    myMouseAdapter = new MyMouseAdapter();
    addMouseMotionListener(myMouseAdapter);
    addMouseListener(myMouseAdapter);

    getSelectionModel().addListSelectionListener(e -> mySelection = null);
    getColumnModel().setColumnSelectionAllowed(false);

    ScrollingUtil.installActions(this, false);
  }

  public @NotNull @NonNls String getId() {
    return myId;
  }

  @Override
  public void dispose() {
  }

  private void initColumnModel() {
    TableColumnModel columnModel = new MyTableColumnModel(myProperties);
    setColumnModel(columnModel);
    setAutoCreateColumnsFromModel(false); // otherwise sizes are recalculated after each TableColumn re-initialization
  }

  protected void updateEmptyText() {
    getEmptyText().setText(VcsLogBundle.message("vcs.log.default.status"));
  }

  protected void setErrorEmptyText(@NotNull Throwable error, @NlsContexts.StatusText @NotNull String defaultText) {
    String message = ObjectUtils.chooseNotNull(error.getLocalizedMessage(), defaultText);
    String shortenedMessage = StringUtil.shortenTextWithEllipsis(message, 150, 0, true);
    //noinspection HardCodedStringLiteral
    getEmptyText().setText(shortenedMessage.replace('\n', ' '));
  }

  protected void appendActionToEmptyText(@Nls @NotNull String text, @NotNull Runnable action) {
    getEmptyText().appendSecondaryText(text, SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES, e -> action.run());
  }

  public void updateDataPack(@NotNull VisiblePack visiblePack, boolean permGraphChanged) {
    boolean filtersChanged = !getModel().getVisiblePack().getFilters().equals(visiblePack.getFilters());

    Selection previousSelection = getSelection();
    getModel().setVisiblePack(visiblePack);
    previousSelection.restore(visiblePack.getVisibleGraph(), true, permGraphChanged);

    for (VcsLogHighlighter highlighter : myHighlighters) {
      highlighter.update(visiblePack, permGraphChanged);
    }

    if (!getEmptyText().getText().equals(VcsLogBundle.message("vcs.log.loading.status"))) {
      updateEmptyText();
    }

    setPaintBusy(false);
    if (filtersChanged) myInitializedColumns.clear();
    reLayout();
  }

  public void onColumnOrderSettingChanged() {
    List<VcsLogColumn<?>> columnOrder = getColumnOrderFromProperties();
    if (columnOrder != null) {
      TableColumnModel columnModel = getColumnModel();
      int columnCount = getVisibleColumnCount();
      for (int i = columnCount - 1; i >= 0; i--) {
        columnModel.removeColumn(columnModel.getColumn(i));
      }

      for (VcsLogColumn<?> column : columnOrder) {
        myTableColumns.computeIfAbsent(column, (k) -> createTableColumn(column));
        columnModel.addColumn(myTableColumns.get(column));
      }
    }

    reLayout();
  }

  @Nullable
  private List<VcsLogColumn<?>> getColumnOrderFromProperties() {
    List<VcsLogColumn<?>> columnOrder = getColumnsOrder(myProperties);
    if (isValidColumnOrder(columnOrder)) {
      return columnOrder;
    }

    LOG.debug("Incorrect column order was saved in properties " + columnOrder + ", replacing it with default order.");
    updateOrder(myProperties, getVisibleColumns());
    return null;
  }

  @NotNull
  private List<Integer> getVisibleColumnIndices() {
    List<Integer> columnOrder = new ArrayList<>();

    for (int i = 0; i < getVisibleColumnCount(); i++) {
      columnOrder.add(getColumnModel().getColumn(i).getModelIndex());
    }
    return columnOrder;
  }

  @NotNull
  public List<VcsLogColumn<?>> getVisibleColumns() {
    return ContainerUtil.map2List(getVisibleColumnIndices(),
                                  columnModelIndex -> VcsLogColumnManager.getInstance().getColumn(columnModelIndex));
  }

  private int getVisibleColumnCount() {
    return getColumnModel().getColumnCount();
  }

  public void onColumnDataChanged(@NotNull VcsLogColumn<?> column) {
    if (getRowCount() == 0) return;
    TableColumn tableColumn = getTableColumn(column);
    if (tableColumn != null) {
      reLayout();
      getModel().fireTableChanged(new TableModelEvent(getModel(), 0, getRowCount() - 1, tableColumn.getModelIndex()));
    }
  }

  private void reLayout() {
    if (getTableHeader().getResizingColumn() == null) {
      updateDynamicColumnsWidth();
      super.doLayout();
      repaint();
    }
  }

  public void forceReLayout(@NotNull VcsLogColumn<?> column) {
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

  private void resetColumnWidth(@NotNull VcsLogColumn<?> column) {
    VcsLogUsageTriggerCollector.triggerColumnReset(myLogData.getProject());
    if (VcsLogColumnUtilKt.getWidth(column, myProperties) != -1) {
      setWidth(column, myProperties, -1);
    }
    else {
      forceReLayout(column);
    }
  }

  @NotNull
  private TableColumn createTableColumn(VcsLogColumn<?> column) {
    TableColumn tableColumn = new TableColumn(VcsLogColumnManager.getInstance().getModelIndex(column));
    tableColumn.setResizable(column.isResizable());
    tableColumn.setCellRenderer(column.createTableCellRenderer(this));
    return tableColumn;
  }

  @NotNull
  public VcsLogUiProperties getProperties() {
    return myProperties;
  }

  @NotNull
  public VcsLogColorManager getColorManager() {
    return myColorManager;
  }

  @NotNull
  public VcsLogData getLogData() {
    return myLogData;
  }

  private void updateDynamicColumnsWidth() {
    for (VcsLogColumn<?> logColumn : VcsLogColumnManager.getInstance().getCurrentDynamicColumns()) {
      TableColumn column = getTableColumn(logColumn);
      if (column == null) continue;

      int width = VcsLogColumnUtilKt.getWidth(logColumn, myProperties);
      if (width <= 0 || width > getWidth()) {
        width = getColumnWidthFromData(column);
      }

      if (width > 0 && width != column.getPreferredWidth()) {
        column.setPreferredWidth(width);
      }
    }

    int size = getWidth();
    for (VcsLogColumn<?> logColumn : VcsLogColumnManager.getInstance().getCurrentColumns()) {
      if (logColumn == Commit.INSTANCE) continue;
      TableColumn column = getTableColumn(logColumn);
      if (column != null) size -= column.getPreferredWidth();
    }

    getCommitColumn().setPreferredWidth(size);
  }

  private int getColumnWidthFromData(@NotNull TableColumn column) {
    int index = column.getModelIndex();
    VcsLogColumn<?> logColumn = VcsLogColumnManager.getInstance().getColumn(index);

    TableCellRenderer columnRenderer = myTableColumns.get(logColumn).getCellRenderer();
    if (columnRenderer instanceof VcsLogCellRenderer) {
      Integer width = ((VcsLogCellRenderer)columnRenderer).getPreferredWidth(this);
      if (width != null) {
        return width;
      }
    }
    if (getModel().getRowCount() <= 0 ||
        !(getModel().getValueAt(0, logColumn) instanceof String) ||
        myInitializedColumns.contains(logColumn)) {
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
      VcsLogHighlighter.TextStyle style = getStyle(row, getColumnViewIndex(logColumn), false, false, false).getTextStyle();
      if (BOLD.equals(style)) {
        font = tableFont.deriveFont(Font.BOLD);
      }
      else if (ITALIC.equals(style)) {
        font = tableFont.deriveFont(Font.ITALIC);
      }
      maxValueWidth = Math.max(getFontMetrics(font).stringWidth(value + "*"), maxValueWidth);
    }

    int horizontalPadding = columnRenderer instanceof SimpleColoredComponent ?
                            VcsLogUiUtil.getHorizontalTextPadding((SimpleColoredComponent)columnRenderer) : 0;
    int width = Math.min(maxValueWidth + horizontalPadding, JBUIScale.scale(MAX_DEFAULT_DYNAMIC_COLUMN_WIDTH));
    if (unloaded * 2 <= maxRowsToCheck) myInitializedColumns.add(logColumn);
    return width;
  }

  @Nullable
  public VcsLogColumn<?> getVcsLogColumn(int viewIndex) {
    int modelIndex = convertColumnIndexToModel(viewIndex);
    return modelIndex < 0 ? null : VcsLogColumnManager.getInstance().getColumn(modelIndex);
  }

  public final int getColumnViewIndex(@NotNull VcsLogColumn<?> column) {
    return convertColumnIndexToView(VcsLogColumnManager.getInstance().getModelIndex(column));
  }

  @Nullable
  public TableColumn getTableColumn(@NotNull VcsLogColumn<?> column) {
    int viewIndex = getColumnViewIndex(column);
    return viewIndex != -1 ? getColumnModel().getColumn(viewIndex) : null;
  }

  @NotNull
  public TableColumn getRootColumn() {
    return Objects.requireNonNull(getTableColumn(Root.INSTANCE));
  }

  @NotNull
  public TableColumn getCommitColumn() {
    return Objects.requireNonNull(getTableColumn(Commit.INSTANCE));
  }

  @Nullable
  private VcsLogCellController getController(@NotNull VcsLogColumn<?> column) {
    TableColumn tableColumn = getTableColumn(column);
    if (tableColumn == null) return null;

    TableCellRenderer renderer = tableColumn.getCellRenderer();
    if (!(renderer instanceof VcsLogCellRenderer)) {
      return null;
    }
    return ((VcsLogCellRenderer)renderer).getCellController();
  }

  @NotNull
  Point getPointInCell(@NotNull Point clickPoint, @NotNull VcsLogColumn<?> vcsLogColumn) {
    int width = 0;
    for (int i = 0; i < getColumnModel().getColumnCount(); i++) {
      TableColumn column = getColumnModel().getColumn(i);
      if (column.getModelIndex() == VcsLogColumnManager.getInstance().getModelIndex(vcsLogColumn)) break;
      width += column.getWidth();
    }
    return new Point(clickPoint.x - width, PositionUtil.getYInsideRow(clickPoint, getRowHeight()));
  }

  int getColumnLeftXCoordinate(int viewColumnIndex) {
    int x = 0;
    for (int i = 0; i < viewColumnIndex; i++) {
      x += getColumnModel().getColumn(i).getWidth();
    }
    return x;
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

  private boolean isShowRootNames() {
    return myProperties.exists(CommonUiProperties.SHOW_ROOT_NAMES) && myProperties.get(CommonUiProperties.SHOW_ROOT_NAMES);
  }

  public void jumpToRow(int rowIndex, boolean focus) {
    if (rowIndex >= 0 && rowIndex <= getRowCount() - 1) {
      scrollRectToVisible(getCellRect(rowIndex, 0, false));
      setRowSelectionInterval(rowIndex, rowIndex);
      if (focus && !hasFocus()) {
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
          return myLogData.getLogProvider(Objects.requireNonNull(getFirstItem(roots))).getSupportedVcs();
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
          sb.append(getModel().getValueAt(selectedRows[i], Commit.INSTANCE));
          if (i != selectedRows.length - 1) sb.append("\n");
        }
        return sb.toString();
      })
      .orNull();
  }

  @Override
  public void performCopy(@NotNull DataContext dataContext) {
    StringBuilder sb = new StringBuilder();

    List<Integer> visibleColumns = getVisibleColumnIndices();
    int[] selectedRows = getSelectedRows();
    for (int i = 0; i < Math.min(VcsLogUtil.MAX_SELECTED_COMMITS, selectedRows.length); i++) {
      int row = selectedRows[i];
      sb.append(StringUtil.join(visibleColumns, j -> {
        if (j == VcsLogColumnManager.getInstance().getModelIndex(Root.INSTANCE)) {
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
    highlighter.update(getModel().getVisiblePack(), true);
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
    VcsCommitStyle style = getStyle(row, column, hasFocus, selected, row == getHoveredRow(this));

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
    return myBaseStyleProvider.getBaseStyle(row, column, hasFocus, selected);
  }

  @NotNull
  VcsCommitStyle getStyle(int row, int column, boolean hasFocus, boolean selected, boolean hovered) {
    VcsCommitStyle baseStyle = getBaseStyle(row, column, hasFocus, selected);

    VisibleGraph<Integer> visibleGraph = getVisibleGraph();
    if (row < 0 || row >= visibleGraph.getVisibleCommitCount()) {
      LOG.error("Visible graph has " + visibleGraph.getVisibleCommitCount() + " commits, yet we want row " + row);
      return baseStyle;
    }

    RowInfo<Integer> rowInfo = visibleGraph.getRowInfo(row);
    VcsCommitStyle style = createStyle(rowInfo.getRowType() == RowType.UNMATCHED ? JBColor.GRAY : baseStyle.getForeground(),
                                       baseStyle.getBackground(), VcsLogHighlighter.TextStyle.NORMAL);

    int commitId = rowInfo.getCommit();
    VcsShortCommitDetails details = myLogData.getMiniDetailsGetter().getCommitDataIfAvailable(commitId);
    if (details != null) {
      List<VcsCommitStyle> styles = ContainerUtil.map(myHighlighters, highlighter -> highlighter.getStyle(commitId, details, selected));
      style = VcsCommitStyleFactory.combine(ContainerUtil.append(styles, style));
    }

    if (!selected && hovered) {
      Color background = Objects.requireNonNull(style.getBackground());
      VcsCommitStyle lightSelectionBgStyle = VcsCommitStyleFactory.background(getHoveredBackgroundColor(background));
      style = VcsCommitStyleFactory.combine(Arrays.asList(lightSelectionBgStyle, style));
    }

    return style;
  }

  @Override
  protected @Nullable Color getHoveredRowBackground() {
    return null; // do not overwrite renderer background
  }

  public void viewportSet(JViewport viewport) {
    viewport.addChangeListener(e -> {
      if (isShowRootNames()) {
        AbstractTableModel model = getModel();
        Couple<Integer> visibleRows = ScrollingUtil.getVisibleRows(this);
        if (visibleRows.first >= 0) {
          TableModelEvent evt = new TableModelEvent(model, visibleRows.first, visibleRows.second,
                                                    VcsLogColumnManager.getInstance().getModelIndex(Root.INSTANCE));
          model.fireTableChanged(evt);
        }
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

  public void handleAnswer(@NotNull GraphAnswer<Integer> answer) {
    GraphCommitCellController controller = (GraphCommitCellController)Objects.requireNonNull(getController(Commit.INSTANCE));
    Cursor cursor = controller.handleGraphAnswer(answer, true, null, null);
    myMouseAdapter.handleCursor(cursor);
  }

  public void showTooltip(int row, @NotNull VcsLogColumn<?> column) {
    if (column != Commit.INSTANCE) return;
    GraphCommitCellController controller = (GraphCommitCellController)Objects.requireNonNull(getController(column));
    controller.showTooltip(row);
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
      g.setColor(getStyle(targetRow, getColumnViewIndex(Commit.INSTANCE), hasFocus(), false, false).getBackground());
      g.fillRect(x, y, width, height);
      if (myColorManager.hasMultiplePaths()) {
        g.setColor(getPathBackgroundColor(getModel().getValueAt(targetRow, Root.INSTANCE), myColorManager));

        int rootWidth = getRootColumn().getWidth();
        if (!isShowRootNames()) rootWidth -= JBUIScale.scale(ROOT_INDICATOR_WHITE_WIDTH);

        g.fillRect(x, y, rootWidth, height);
      }
    }
    else {
      g.setColor(getBaseStyle(targetRow, getColumnViewIndex(Commit.INSTANCE), hasFocus(), false).getBackground());
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
  private static Color getHoveredBackgroundColor(@NotNull Color background) {
    int alpha = HOVERED_BACKGROUND.getAlpha();
    if (alpha == 255) return HOVERED_BACKGROUND;
    if (alpha == 0) return background;
    return ColorUtil.mix(new Color(HOVERED_BACKGROUND.getRGB()), background, alpha / 255.0);
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

  private static class BaseStyleProvider {
    @NotNull private final JTable myTable;
    @NotNull private final TableCellRenderer myDummyRenderer = new DefaultTableCellRenderer();

    BaseStyleProvider(@NotNull JTable table) {
      myTable = table;
    }

    @NotNull
    public VcsCommitStyle getBaseStyle(int row, int column, boolean hasFocus, boolean selected) {
      Component dummyRendererComponent = myDummyRenderer.getTableCellRendererComponent(myTable, "", selected, hasFocus, row, column);
      Color background = selected ? UIUtil.getListSelectionBackground(myTable.hasFocus()) : UIUtil.getListBackground();
      return createStyle(dummyRendererComponent.getForeground(), background, VcsLogHighlighter.TextStyle.NORMAL);
    }
  }

  private class MyMouseAdapter extends MouseAdapter {
    private static final int BORDER_THICKNESS = 3;
    @NotNull private final TableLinkMouseListener myLinkListener = new MyLinkMouseListener();
    @Nullable private Cursor myLastCursor = null;

    @Override
    public void mouseClicked(MouseEvent e) {
      if (myLinkListener.onClick(e, e.getClickCount())) {
        return;
      }

      int c = columnAtPoint(e.getPoint());
      VcsLogColumn<?> column = getVcsLogColumn(c);
      if (column == null) return;
      if (e.getClickCount() == 2) {
        // when we reset column width, commit column eats all the remaining space
        // (or gives the required space)
        // so it is logical that we reset column width by right border if it is on the left of the commit column
        // and by the left border otherwise
        int commitColumnIndex = getColumnViewIndex(Commit.INSTANCE);
        boolean useLeftBorder = c > commitColumnIndex;
        if ((useLeftBorder ? isOnLeftBorder(e, c) : isOnRightBorder(e, c)) && column.isDynamic()) {
          resetColumnWidth(column);
        }
        else {
          // user may have clicked just outside of the border
          // in that case, c is not the column we are looking for
          int c2 = columnAtPoint(new Point(e.getPoint().x + (useLeftBorder ? 1 : -1) * JBUIScale.scale(BORDER_THICKNESS),
                                           e.getPoint().y));
          VcsLogColumn<?> column2 = getVcsLogColumn(c2);
          if (column2 != null && (useLeftBorder ? isOnLeftBorder(e, c2) : isOnRightBorder(e, c2)) && column2.isDynamic()) {
            resetColumnWidth(column2);
          }
        }
      }

      int row = rowAtPoint(e.getPoint());
      if ((row >= 0 && row < getRowCount()) && e.getClickCount() == 1) {
        VcsLogCellController controller = getController(column);
        if (controller != null) {
          Cursor cursor = controller.performMouseClick(row, e);
          handleCursor(cursor);
        }
      }
    }

    public boolean isOnLeftBorder(@NotNull MouseEvent e, int column) {
      return Math.abs(getColumnLeftXCoordinate(column) - e.getPoint().x) <= JBUIScale.scale(BORDER_THICKNESS);
    }

    public boolean isOnRightBorder(@NotNull MouseEvent e, int column) {
      return Math.abs(getColumnLeftXCoordinate(column) +
                      getColumnModel().getColumn(column).getWidth() - e.getPoint().x) <= JBUIScale.scale(BORDER_THICKNESS);
    }

    @Override
    public void mouseMoved(MouseEvent e) {
      if (getRowCount() == 0) return;
      if (isResizingColumns()) return;
      getExpandableItemsHandler().setEnabled(true);

      if (myLinkListener.getTagAt(e) != null) {
        swapCursor();
        return;
      }

      int row = rowAtPoint(e.getPoint());
      if (row >= 0 && row < getRowCount()) {
        VcsLogColumn<?> column = getVcsLogColumn(columnAtPoint(e.getPoint()));
        if (column == null) return;

        VcsLogCellController controller = getController(column);
        if (controller != null) {
          Cursor cursor = controller.performMouseMove(row, e);
          handleCursor(cursor);
          return;
        }
      }

      restoreCursor();
    }

    private void handleCursor(@Nullable Cursor cursor) {
      if (cursor != null) {
        if (cursor.getType() == Cursor.DEFAULT_CURSOR) {
          restoreCursor();
        }
        else if (cursor.getType() == Cursor.HAND_CURSOR) {
          swapCursor();
        }
      }
    }

    private void swapCursor() {
      if (getCursor().getType() != Cursor.HAND_CURSOR && myLastCursor == null) {
        Cursor newCursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
        myLastCursor = getCursor();
        setCursor(newCursor);
      }
    }

    private void restoreCursor() {
      if (getCursor().getType() != Cursor.DEFAULT_CURSOR) {
        setCursor(UIUtil.cursorIfNotDefault(myLastCursor));
      }
      myLastCursor = null;
    }

    @Override
    public void mouseEntered(MouseEvent e) {
      // Do nothing
    }

    @Override
    public void mouseExited(MouseEvent e) {
      getExpandableItemsHandler().setEnabled(true);
    }

    private class MyLinkMouseListener extends SimpleColoredComponentLinkMouseListener {
      @Nullable
      @Override
      public Object getTagAt(@NotNull MouseEvent e) {
        return ObjectUtils.tryCast(super.getTagAt(e), SimpleColoredComponent.BrowserLauncherTag.class);
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

      MouseEvent e = (MouseEvent)anEvent;

      int row = rowAtPoint(e.getPoint());
      if (row < 0 || row >= getRowCount()) return true;

      VcsLogColumn<?> column = getVcsLogColumn(columnAtPoint(e.getPoint()));
      if (column == null) return true;
      VcsLogCellController controller = getController(column);
      if (controller == null) return true;

      return controller.shouldSelectCell(row, e);
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
        getEmptyText().setText(VcsLogBundle.message("vcs.log.loading.status"));
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
        for (VcsLogColumn<?> logColumn : VcsLogColumnManager.getInstance().getCurrentDynamicColumns()) {
          TableColumn column = getTableColumn(logColumn);
          if (evt.getSource().equals(column)) {
            setWidth(logColumn, myProperties, column.getWidth());
          }
        }
      }
      super.propertyChange(evt);
    }

    @Override
    public void moveColumn(int columnIndex, int newIndex) {
      VcsLogColumn<?> column = getVcsLogColumn(columnIndex);
      if (column == null ||
          column == Root.INSTANCE || getVcsLogColumn(newIndex) == Root.INSTANCE ||
          !supportsColumnsReordering(myProperties)) {
        return;
      }
      super.moveColumn(columnIndex, newIndex);
      VcsLogColumnUtilKt.moveColumn(myProperties, column, newIndex);
    }
  }

  private final class MyTopBottomBorder implements Border {
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

  private class MyCurrentColumnsListener implements VcsLogColumnManager.CurrentColumnsListener {
    @Override
    public void columnAdded(@NotNull VcsLogColumn<?> column) {
      onColumnOrderSettingChanged();
    }

    @Override
    public void columnRemoved(@NotNull VcsLogColumn<?> column) {
      myTableColumns.remove(column);
      myInitializedColumns.remove(column);
      onColumnOrderSettingChanged();
    }
  }
}
