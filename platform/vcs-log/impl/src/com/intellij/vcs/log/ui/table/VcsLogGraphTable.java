// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.table;

import com.google.common.primitives.Ints;
import com.intellij.ide.CopyProvider;
import com.intellij.ide.bookmark.BookmarksManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.ui.LoadingDecorator;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.util.registry.RegistryValueListener;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.issueLinks.TableLinkMouseListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.*;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.NamedColorUtil;
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
import com.intellij.vcs.log.statistics.VcsLogUsageTriggerCollector;
import com.intellij.vcs.log.ui.VcsLogColorManager;
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys;
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
import static com.intellij.vcs.log.ui.table.column.VcsLogColumnUtilKt.*;
import static java.util.Collections.emptySet;

public class VcsLogGraphTable extends TableWithProgress
  implements VcsLogCommitList, UiCompatibleDataProvider, CopyProvider, Disposable {

  private static final Logger LOG = Logger.getInstance(VcsLogGraphTable.class);

  private static final int MAX_DEFAULT_DYNAMIC_COLUMN_WIDTH = 300;
  private static final int MAX_ROWS_TO_CALC_WIDTH = 1000;

  private static final Color DEFAULT_HOVERED_BACKGROUND = new JBColor(ColorUtil.withAlpha(new Color(0xC3D2E3), 0.4),
                                                                      new Color(0x464A4D));
  private static final Color HOVERED_BACKGROUND = JBColor.namedColor("VersionControl.Log.Commit.hoveredBackground",
                                                                     DEFAULT_HOVERED_BACKGROUND);

  private static final Color SELECTION_BACKGROUND = JBColor.namedColor("VersionControl.Log.Commit.selectionBackground",
                                                                       UIUtil.getListSelectionBackground(true));

  private static final Color SELECTION_BACKGROUND_INACTIVE = JBColor.namedColor("VersionControl.Log.Commit.selectionInactiveBackground",
                                                                                UIUtil.getListSelectionBackground(false));

  private static final Color SELECTION_FOREGROUND = JBColor.namedColor("VersionControl.Log.Commit.selectionForeground",
                                                                       NamedColorUtil.getListSelectionForeground(true));

  private static final Color SELECTION_FOREGROUND_INACTIVE = JBColor.namedColor("VersionControl.Log.Commit.selectionInactiveForeground",
                                                                                NamedColorUtil.getListSelectionForeground(false));
  private final @NotNull VcsLogData myLogData;
  private final @NotNull String myId;
  private final @NotNull VcsLogUiProperties myProperties;
  private final @NotNull VcsLogColorManager myColorManager;

  private final @NotNull BaseStyleProvider myBaseStyleProvider;
  private final @NotNull GraphCommitCellRenderer myGraphCommitCellRenderer;
  private final @NotNull MyMouseAdapter myMouseAdapter;

  // BasicTableUI.viewIndexForColumn uses reference equality, so we should not change TableColumn during DnD.
  private final @NotNull Map<VcsLogColumn<?>, TableColumn> myTableColumns = new HashMap<>();
  private final @NotNull Set<VcsLogColumn<?>> myInitializedColumns = new HashSet<>();

  private final @NotNull Collection<VcsLogHighlighter> myHighlighters = new LinkedHashSet<>();

  private @Nullable SelectionSnapshot mySelectionSnapshot = null;

  private boolean myDisposed = false;

  public VcsLogGraphTable(@NotNull String logId, @NotNull VcsLogData logData,
                          @NotNull VcsLogUiProperties uiProperties, @NotNull VcsLogColorManager colorManager,
                          @NotNull Runnable requestMore, @NotNull Disposable disposable) {
    super(new GraphTableModel(logData, requestMore, uiProperties));
    Disposer.register(disposable, this);

    myLogData = logData;
    myId = logId;
    myProperties = uiProperties;
    myColorManager = colorManager;

    myBaseStyleProvider = new BaseStyleProvider(this);

    getEmptyText().setText(VcsLogBundle.message("vcs.log.default.status"));
    myLogData.getProgress().addProgressIndicatorListener(new MyProgressListener(), this);

    setColumnModel(new MyTableColumnModel(myProperties));
    onColumnOrderSettingChanged();
    setRootColumnSize();
    subscribeOnNewUiSwitching();

    myGraphCommitCellRenderer = getGraphCommitCellRenderer();
    ApplicationManager.getApplication().getMessageBus().connect(this)
      .subscribe(VcsLogCustomColumnListener.TOPIC, new MyColumnsAvailabilityListener());
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

    getSelectionModel().addListSelectionListener(e -> mySelectionSnapshot = null);
    getColumnModel().setColumnSelectionAllowed(false);

    putClientProperty(BookmarksManager.ALLOWED, true);
    ScrollingUtil.installActions(this, false);
  }

  @Override
  public boolean getAutoCreateColumnsFromModel() {
    // otherwise sizes are recalculated after each TableColumn re-initialization
    return false;
  }

  public @NotNull @NonNls String getId() {
    return myId;
  }

  @Override
  public void dispose() {
    myDisposed = true;
  }

  @Override
  public @NotNull VcsLogCommitSelection getSelection() {
    return getModel().createSelection(getSelectedRows());
  }

  protected void updateEmptyText() {
    getEmptyText().setText(VcsLogBundle.message("vcs.log.default.status"));
  }

  protected void setErrorEmptyText(@NotNull Throwable error, @NlsContexts.StatusText @NotNull String defaultText) {
    String message = ObjectUtils.chooseNotNull(error.getLocalizedMessage(), defaultText);
    String shortenedMessage = StringUtil.shortenTextWithEllipsis(message, 150, 0, true);
    getEmptyText().setText(shortenedMessage.replace('\n', ' '));
  }

  protected void appendActionToEmptyText(@Nls @NotNull String text, @NotNull Runnable action) {
    getEmptyText().appendSecondaryText(text, SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES, e -> action.run());
  }

  public void updateDataPack(@NotNull VisiblePack visiblePack, boolean permGraphChanged) {
    boolean filtersChanged = !getModel().getVisiblePack().getFilters().equals(visiblePack.getFilters());

    SelectionSnapshot previousSelection = getSelectionSnapshot();
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
        boolean isAvailable = !(column instanceof VcsLogCustomColumn<?> customColumn) ||
                              VcsLogCustomColumn.isAvailable(customColumn, myLogData);
        if (isAvailable) {
          myTableColumns.computeIfAbsent(column, (k) -> createTableColumn(column));
          columnModel.addColumn(myTableColumns.get(column));
        }
      }
    }

    reLayout();
  }

  private @Nullable List<VcsLogColumn<?>> getColumnOrderFromProperties() {
    List<VcsLogColumn<?>> columnOrder = getColumnsOrder(myProperties);
    if (isValidColumnOrder(columnOrder)) {
      return columnOrder;
    }

    LOG.debug("Incorrect column order was saved in properties " + columnOrder + ", replacing it with default order.");
    List<VcsLogColumn<?>> visibleColumns = getVisibleColumns();
    if (!visibleColumns.isEmpty()) {
      updateOrder(myProperties, visibleColumns);
      return null;
    }
    List<VcsLogColumn<?>> validColumnOrder = makeValidColumnOrder(columnOrder);
    updateOrder(myProperties, validColumnOrder);
    return validColumnOrder;
  }

  private @NotNull List<Integer> getVisibleColumnIndices() {
    List<Integer> columnOrder = new ArrayList<>();

    for (int i = 0; i < getVisibleColumnCount(); i++) {
      columnOrder.add(getColumnModel().getColumn(i).getModelIndex());
    }
    return columnOrder;
  }

  public @NotNull List<VcsLogColumn<?>> getVisibleColumns() {
    return ContainerUtil.map(getVisibleColumnIndices(),
                             columnModelIndex -> VcsLogColumnManager.getInstance().getColumn(columnModelIndex));
  }

  public boolean isColumnVisible(@NotNull VcsLogColumn<?> column) {
    for (int i = 0; i < getVisibleColumnCount(); i++) {
      if (VcsLogColumnManager.getInstance().getColumn(getColumnModel().getColumn(i).getModelIndex()) == column) {
        return true;
      }
    }
    return false;
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

  private @NotNull TableColumn createTableColumn(VcsLogColumn<?> column) {
    TableColumn tableColumn = new TableColumn(VcsLogColumnManager.getInstance().getModelIndex(column));
    tableColumn.setResizable(column.isResizable());
    tableColumn.setCellRenderer(createColumnRenderer(column));
    return tableColumn;
  }

  private void subscribeOnNewUiSwitching() {
    Registry.get(ExperimentalUI.KEY).addListener(new RegistryValueListener() {
      @Override
      public void afterValueChanged(@NotNull RegistryValue value) {
        updateColumnRenderers();
        setRootColumnSize();
      }
    }, this);
  }

  private void updateColumnRenderers() {
    myTableColumns.forEach((logColumn, tableColumn) -> {
      tableColumn.setCellRenderer(createColumnRenderer(logColumn));
    });
  }

  private @NotNull TableCellRenderer createColumnRenderer(@NotNull VcsLogColumn<?> column) {
    TableCellRenderer renderer = column.createTableCellRenderer(this);
    if (ExperimentalUI.isNewUI() && column != Root.INSTANCE) {
      renderer = new VcsLogNewUiTableCellRenderer(column, renderer, myColorManager::hasMultiplePaths);
    }
    return renderer;
  }

  private @NotNull GraphCommitCellRenderer getGraphCommitCellRenderer() {
    TableCellRenderer cellRenderer = getCommitColumn().getCellRenderer();
    if (cellRenderer instanceof VcsLogNewUiTableCellRenderer) {
      cellRenderer = ((VcsLogNewUiTableCellRenderer)cellRenderer).getBaseRenderer();
    }
    return (GraphCommitCellRenderer)Objects.requireNonNull(cellRenderer);
  }

  private static @Nullable VcsLogCellRenderer getVcsLogCellRenderer(@NotNull TableColumn column) {
    TableCellRenderer renderer = column.getCellRenderer();
    return renderer instanceof VcsLogCellRenderer ? (VcsLogCellRenderer)renderer : null;
  }

  public @NotNull VcsLogUiProperties getProperties() {
    return myProperties;
  }

  public @NotNull VcsLogColorManager getColorManager() {
    return myColorManager;
  }

  public @NotNull VcsLogData getLogData() {
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

    VcsLogCellRenderer cellRenderer = getVcsLogCellRenderer(column);
    if (cellRenderer == null) {
      return column.getPreferredWidth();
    }
    VcsLogCellRenderer.PreferredWidth preferredWidth = cellRenderer.getPreferredWidth();
    if (preferredWidth instanceof VcsLogCellRenderer.PreferredWidth.Fixed fixedWidth) {
      int width = fixedWidth.getFunction().invoke(this);
      if (width >= 0) {
        return width;
      }
      else {
        // negative values are returned because of the migration
        // from com.intellij.vcs.log.ui.table.VcsLogCellRenderer.getPreferredWidth(javax.swing.JTable)
        return column.getPreferredWidth();
      }
    }
    if (getModel().getRowCount() <= 0 || myInitializedColumns.contains(logColumn) || preferredWidth == null) {
      return column.getPreferredWidth();
    }

    VcsLogCellRenderer.PreferredWidth.FromData widthFromData = (VcsLogCellRenderer.PreferredWidth.FromData)preferredWidth;

    int maxRowsToCheck = Math.min(MAX_ROWS_TO_CALC_WIDTH, getRowCount());
    int maxValueWidth = 0;
    int unloaded = 0;
    for (int row = 0; row < maxRowsToCheck; row++) {
      Object value = getModel().getValueAt(row, logColumn);
      Integer width = widthFromData.getFunction().invoke(this, value, row, getColumnViewIndex(logColumn));
      if (width == null) {
        unloaded++;
        continue;
      }
      maxValueWidth = Math.max(width, maxValueWidth);
    }

    int width = Math.min(maxValueWidth, JBUIScale.scale(MAX_DEFAULT_DYNAMIC_COLUMN_WIDTH));
    if (unloaded * 2 <= maxRowsToCheck) myInitializedColumns.add(logColumn);
    return width;
  }

  public @Nullable VcsLogColumn<?> getVcsLogColumn(int viewIndex) {
    int modelIndex = convertColumnIndexToModel(viewIndex);
    return modelIndex < 0 ? null : VcsLogColumnManager.getInstance().getColumn(modelIndex);
  }

  public final int getColumnViewIndex(@NotNull VcsLogColumn<?> column) {
    return convertColumnIndexToView(VcsLogColumnManager.getInstance().getModelIndex(column));
  }

  public @Nullable TableColumn getTableColumn(@NotNull VcsLogColumn<?> column) {
    int viewIndex = getColumnViewIndex(column);
    return viewIndex != -1 ? getColumnModel().getColumn(viewIndex) : null;
  }

  public @NotNull TableColumn getRootColumn() {
    return Objects.requireNonNull(getTableColumn(Root.INSTANCE));
  }

  public @NotNull TableColumn getCommitColumn() {
    return Objects.requireNonNull(getTableColumn(Commit.INSTANCE));
  }

  private @Nullable VcsLogCellController getController(@NotNull VcsLogColumn<?> column) {
    TableColumn tableColumn = getTableColumn(column);
    if (tableColumn == null) return null;

    VcsLogCellRenderer cellRenderer = getVcsLogCellRenderer(tableColumn);
    return cellRenderer == null ? null : cellRenderer.getCellController();
  }

  @NotNull
  Point getPointInCell(@NotNull Point clickPoint, @NotNull VcsLogColumn<?> vcsLogColumn) {
    int columnIndex = getColumnViewIndex(vcsLogColumn);
    int left = getColumnDataRectLeft(columnIndex);
    int top = getCellRectTop(clickPoint.y);
    return new Point(clickPoint.x - left, clickPoint.y - top);
  }

  private int getCellRectTop(int y) {
    int rowHeight = getRowHeight();
    int rowIndex = y / rowHeight;
    return rowIndex * rowHeight;
  }

  int getColumnDataRectLeft(int viewColumnIndex) {
    int x = getCellRect(0, viewColumnIndex, false).x;
    if (!ExperimentalUI.isNewUI()) return x;
    return x + VcsLogNewUiTableCellRenderer.getAdditionalOffset(viewColumnIndex);
  }

  private void setRootColumnSize() {
    TableColumn column = getRootColumn();

    RootCellRenderer rootCellRenderer = (RootCellRenderer)column.getCellRenderer();
    rootCellRenderer.updateInsets();

    int rootWidth = rootCellRenderer.getColumnWidth();
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

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    int[] selectedRows = getSelectedRows();
    Set<VirtualFile> roots = selectedRows.length == 0 || selectedRows.length > VcsLogUtil.MAX_SELECTED_COMMITS ? emptySet() :
                             ContainerUtil.map2SetNotNull(Ints.asList(selectedRows), row -> getModel().getRootAtRow(row));
    sink.set(PlatformDataKeys.COPY_PROVIDER, this);
    sink.set(VcsLogInternalDataKeys.VCS_LOG_GRAPH_TABLE, this);

    if (roots.size() == 1) {
      sink.set(VcsDataKeys.VCS, myLogData.getLogProvider(Objects.requireNonNull(getFirstItem(roots))).getSupportedVcs());
    }
    if (selectedRows.length == 1) {
      sink.set(VcsLogDataKeys.VCS_LOG_BRANCHES, getModel().getBranchesAtRow(selectedRows[0]));
      sink.set(VcsLogDataKeys.VCS_LOG_REFS, getModel().getRefsAtRow(selectedRows[0]));
    }
    if (selectedRows.length != 0) {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < Math.min(VcsLogUtil.MAX_SELECTED_COMMITS, selectedRows.length); i++) {
        sb.append(getModel().getValueAt(selectedRows[i], Commit.INSTANCE));
        if (i != selectedRows.length - 1) sb.append("\n");
      }
      sink.set(VcsDataKeys.PRESET_COMMIT_MESSAGE, sb.toString());
    }
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
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
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

  public @NotNull SimpleTextAttributes applyHighlighters(@NotNull Component rendererComponent,
                                                         int row,
                                                         int column,
                                                         boolean hasFocus,
                                                         final boolean selected) {
    VcsCommitStyle style = getStyle(row, column, hasFocus, selected, row == getHoveredRow(this));

    assert style.getBackground() != null && style.getForeground() != null && style.getTextStyle() != null;

    rendererComponent.setBackground(style.getBackground());
    rendererComponent.setForeground(style.getForeground());

    return switch (style.getTextStyle()) {
      case BOLD -> SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES;
      case ITALIC -> SimpleTextAttributes.REGULAR_ITALIC_ATTRIBUTES;
      default -> SimpleTextAttributes.REGULAR_ATTRIBUTES;
    };
  }

  public @NotNull VcsCommitStyle getBaseStyle(int row, int column, boolean hasFocus, boolean selected) {
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
    VcsShortCommitDetails details = myLogData.getCommitMetadataCache().getCachedData(commitId);
    if (details != null) {
      int columnModelIndex = convertColumnIndexToModel(column);
      List<VcsCommitStyle> styles = ContainerUtil.map(myHighlighters, highlighter -> {
        try {
          return highlighter.getStyle(commitId, details, columnModelIndex, selected);
        }
        catch (ProcessCanceledException e) {
          return VcsCommitStyle.DEFAULT;
        }
        catch (Throwable t) {
          LOG.error("Exception while getting style from highlighter " + highlighter, t);
          return VcsCommitStyle.DEFAULT;
        }
      });
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
      mySelectionSnapshot = null;
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
  public @NotNull GraphTableModel getModel() {
    return (GraphTableModel)super.getModel();
  }

  @Override
  public @NotNull GraphTableModel getListModel() {
    return getModel();
  }

  @NotNull
  SelectionSnapshot getSelectionSnapshot() {
    if (mySelectionSnapshot == null) mySelectionSnapshot = new SelectionSnapshot(this);
    return mySelectionSnapshot;
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

  public @NotNull VisibleGraph<Integer> getVisibleGraph() {
    return getModel().getVisiblePack().getVisibleGraph();
  }

  @Override
  public int getRowHeight() {
    return myGraphCommitCellRenderer.getPreferredHeight();
  }

  @Override
  protected void paintFooter(@NotNull Graphics g, int x, int y, int width, int height) {
    paintTopBottomBorder(g, x, y, width, height);
  }

  private void paintTopBottomBorder(@NotNull Graphics g, int x, int y, int width, int height) {
    int targetRow = getRowCount() - 1;
    if (targetRow >= 0 && targetRow < getRowCount()) {
      g.setColor(getStyle(targetRow, getColumnViewIndex(Commit.INSTANCE), hasFocus(), false, false).getBackground());
    }
    else {
      g.setColor(getBaseStyle(targetRow, getColumnViewIndex(Commit.INSTANCE), hasFocus(), false).getBackground());
    }
    g.fillRect(x, y, width, height);
  }

  public boolean isResizingColumns() {
    return getCursor() == Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR);
  }

  private static @NotNull Color getHoveredBackgroundColor(@NotNull Color background) {
    int alpha = HOVERED_BACKGROUND.getAlpha();
    if (alpha == 255) return HOVERED_BACKGROUND;
    if (alpha == 0) return background;
    return ColorUtil.mix(new Color(HOVERED_BACKGROUND.getRGB()), background, alpha / 255.0);
  }

  static Font getTableFont() {
    return UIManager.getFont("Table.font");
  }

  private static class BaseStyleProvider {
    private final @NotNull JTable myTable;
    private final @NotNull TableCellRenderer myDummyRenderer = new DefaultTableCellRenderer();

    BaseStyleProvider(@NotNull JTable table) {
      myTable = table;
    }

    public @NotNull VcsCommitStyle getBaseStyle(int row, int column, boolean hasFocus, boolean selected) {
      Component dummyRendererComponent = myDummyRenderer.getTableCellRendererComponent(myTable, "", selected, hasFocus, row, column);
      Color background = selected ? getSelectionBackground(myTable.hasFocus()) : getTableBackground();

      return createStyle(dummyRendererComponent.getForeground(), background, VcsLogHighlighter.TextStyle.NORMAL);
    }
  }

  private class MyMouseAdapter extends MouseAdapter {
    private static final int BORDER_THICKNESS = 3;
    private final @NotNull TableLinkMouseListener myLinkListener = new MyLinkMouseListener();
    private @Nullable Cursor myLastCursor = null;

    @Override
    public void mouseClicked(MouseEvent e) {
      if (getExpandableItemsHandler().isEnabled() && myLinkListener.onClick(e, e.getClickCount())) {
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
          Cursor cursor =
            SwingUtilities.isLeftMouseButton(e) ? controller.performMouseClick(row, e) : null;
          handleCursor(cursor);
        }
      }
    }

    public boolean isOnLeftBorder(@NotNull MouseEvent e, int column) {
      return Math.abs(getCellRect(0, column, false).x - e.getPoint().x) <= JBUIScale.scale(BORDER_THICKNESS);
    }

    public boolean isOnRightBorder(@NotNull MouseEvent e, int column) {
      return Math.abs(getCellRect(0, column, false).x +
                      getColumnModel().getColumn(column).getWidth() - e.getPoint().x) <= JBUIScale.scale(BORDER_THICKNESS);
    }

    @Override
    public void mouseMoved(MouseEvent e) {
      if (getRowCount() == 0) return;
      if (isResizingColumns()) return;
      getExpandableItemsHandler().setEnabled(true);

      int row = rowAtPoint(e.getPoint());
      if (row >= 0 && row < getRowCount()) {
        VcsLogColumn<?> column = getVcsLogColumn(columnAtPoint(e.getPoint()));
        if (column == null) return;

        VcsLogCellController controller = getController(column);
        if (controller != null) {
          VcsLogCellController.MouseMoveResult mouseMoveResult = controller.performMouseMove(row, e);
          handleCursor(mouseMoveResult.getCursor());
          if (!mouseMoveResult.getContinueProcessing()) {
            return;
          }
        }
      }

      if (myLinkListener.getTagAt(e) != null) {
        swapCursor();
        return;
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

    private static class MyLinkMouseListener extends SimpleColoredComponentLinkMouseListener {
      @Override
      public @Nullable Object getTagAt(@NotNull MouseEvent e) {
        return ObjectUtils.tryCast(super.getTagAt(e), SimpleColoredComponent.BrowserLauncherTag.class);
      }
    }
  }

  @Override
  public void changeSelection(int rowIndex, int columnIndex, boolean toggle, boolean extend) {
    if (shouldChangeSelect(EventQueue.getCurrentEvent(), rowIndex, columnIndex )) {
      super.changeSelection(rowIndex, columnIndex, toggle, extend);
    }
  }

  /**
   * Allows avoiding selection update when graph is clicked
   */
  private boolean shouldChangeSelect(@Nullable AWTEvent event, int rowIndex, int columnIndex) {
    if (!(event instanceof MouseEvent)) return true;

    VcsLogColumn<?> column = getVcsLogColumn(columnIndex);
    if (column == null) return false;
    VcsLogCellController controller = getController(column);
    if (controller == null) return true;

    return controller.shouldSelectCell(rowIndex, (MouseEvent)event);
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
    private final @NotNull VcsLogUiProperties myProperties;

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
      if (columnIndex == newIndex) {
        super.moveColumn(columnIndex, newIndex);
        return;
      }

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

  private class MyColumnsAvailabilityListener implements VcsLogCustomColumnListener {
    @Override
    public void columnAvailabilityChanged() {
      ApplicationManager.getApplication().invokeLater(() -> {
        onColumnOrderSettingChanged();
      }, o -> myDisposed);
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

  public static @NotNull Color getTableBackground() {
    return ExperimentalUI.isNewUI() ? JBUI.CurrentTheme.ToolWindow.background() : UIUtil.getListBackground();
  }

  public static @NotNull Color getSelectionBackground(boolean hasFocus) {
    return hasFocus ? SELECTION_BACKGROUND : SELECTION_BACKGROUND_INACTIVE;
  }

  public static @NotNull Color getSelectionForeground(boolean hasFocus) {
    return hasFocus ? SELECTION_FOREGROUND : SELECTION_FOREGROUND_INACTIVE;
  }
}
