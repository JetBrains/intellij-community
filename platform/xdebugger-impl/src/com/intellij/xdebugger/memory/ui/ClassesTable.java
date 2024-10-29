// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.memory.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.ui.table.JBTable;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.FList;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.StatusText;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.memory.component.InstancesTracker;
import com.intellij.xdebugger.memory.tracking.TrackerForNewInstancesBase;
import com.intellij.xdebugger.memory.tracking.TrackingType;
import com.intellij.xdebugger.memory.utils.AbstractTableColumnDescriptor;
import com.intellij.xdebugger.memory.utils.AbstractTableModelWithColumns;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ClassesTable extends JBTable implements UiDataProvider, Disposable {
  public static final DataKey<TypeInfo> SELECTED_CLASS_KEY = DataKey.create("ClassesTable.SelectedClass");
  public static final DataKey<ReferenceCountProvider> REF_COUNT_PROVIDER_KEY =
    DataKey.create("ClassesTable.ReferenceCountProvider");

  private static final JBColor CLICKABLE_COLOR = new JBColor(new Color(250, 251, 252), new Color(62, 66, 69));

  private static final SimpleTextAttributes LINK_ATTRIBUTES =
    new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, SimpleTextAttributes.LINK_ATTRIBUTES.getFgColor());
  private static final SimpleTextAttributes UNDERLINE_LINK_ATTRIBUTES = SimpleTextAttributes.LINK_ATTRIBUTES;

  private static final int CLASSES_COLUMN_PREFERRED_WIDTH = 250;
  private static final int COUNT_COLUMN_MIN_WIDTH = 80;
  private static final int DIFF_COLUMN_MIN_WIDTH = 80;
  private static final UnknownDiffValue UNKNOWN_VALUE = new UnknownDiffValue();

  private final DiffViewTableModel myModel;
  private final Map<TypeInfo, DiffValue> myCounts = new ConcurrentHashMap<>();
  private final InstancesTracker myInstancesTracker;
  private final ClassesFilteredViewBase myParent;
  private final ReferenceCountProvider myCountProvider;

  private boolean myOnlyWithDiff;
  private boolean myOnlyTracked;
  private boolean myOnlyWithInstances;
  private MinusculeMatcher myMatcher = NameUtil.buildMatcher("*").build();
  private String myFilteringPattern = "";
  private final MergingUpdateQueue myFilterTypingMergeQueue = new MergingUpdateQueue(
    "Classes table typing merging queue", 500, true,
    this, this, this, true).setRestartTimerOnAdd(true);

  private volatile List<TypeInfo> myItems = Collections.unmodifiableList(new ArrayList<>());
  private boolean myIsShowCounts = true;
  private MouseListener myMouseListener = null;

  @SuppressWarnings("WeakerAccess")
  public ClassesTable(@NotNull Project project, @NotNull ClassesFilteredViewBase parent, boolean onlyWithDiff,
                      boolean onlyWithInstances,
                      boolean onlyTracked) {
    myModel = getTableModel();
    setModel(myModel);

    myOnlyWithDiff = onlyWithDiff;
    myOnlyWithInstances = onlyWithInstances;
    myOnlyTracked = onlyTracked;
    myInstancesTracker = InstancesTracker.getInstance(project);
    myParent = parent;

    customizeColumns();

    setShowGrid(false);
    setIntercellSpacing(new JBDimension(0, 0));

    setDefaultRenderer(TypeInfo.class, new MyClassColumnRenderer());
    setDefaultRenderer(Long.class, new MyCountColumnRenderer());
    setDefaultRenderer(DiffValue.class, new MyDiffColumnRenderer());

    setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    myCountProvider = new ReferenceCountProvider() {
      @Override
      public int getTotalCount(@NotNull TypeInfo ref) {
        return (int)myCounts.get(ref).myCurrentCount;
      }

      @Override
      public int getDiffCount(@NotNull TypeInfo ref) {
        return (int)myCounts.get(ref).diff();
      }

      @Override
      public int getNewInstancesCount(@NotNull TypeInfo ref) {
        TrackerForNewInstancesBase strategy = myParent.getStrategy(ref);
        return strategy == null || !strategy.isReady() ? -1 : strategy.getCount();
      }
    };
  }

  @NotNull
  protected DiffViewTableModel getTableModel() {
    return new DiffViewTableModel();
  }

  protected void customizeColumns() {
    final TableColumnModel columnModel = getColumnModel();
    TableColumn classesColumn = columnModel.getColumn(DiffViewTableModel.CLASSNAME_COLUMN_INDEX);
    TableColumn countColumn = columnModel.getColumn(DiffViewTableModel.COUNT_COLUMN_INDEX);
    TableColumn diffColumn = columnModel.getColumn(DiffViewTableModel.DIFF_COLUMN_INDEX);

    setAutoResizeMode(AUTO_RESIZE_SUBSEQUENT_COLUMNS);
    classesColumn.setPreferredWidth(JBUIScale.scale(CLASSES_COLUMN_PREFERRED_WIDTH));

    countColumn.setMinWidth(JBUIScale.scale(COUNT_COLUMN_MIN_WIDTH));

    diffColumn.setMinWidth(JBUIScale.scale(DIFF_COLUMN_MIN_WIDTH));

    TableRowSorter<DiffViewTableModel> sorter = new TableRowSorter<>(myModel);
    sorter.setRowFilter(new RowFilter<>() {
      @Override
      public boolean include(Entry<? extends DiffViewTableModel, ? extends Integer> entry) {
        int ix = entry.getIdentifier();
        TypeInfo ref = getTypeInfoAt(ix);
        DiffValue diff = myCounts.getOrDefault(ref, UNKNOWN_VALUE);

        boolean isFilteringOptionsRefused = myOnlyWithDiff && diff.diff() == 0
                                            || myOnlyWithInstances && !diff.hasInstance()
                                            || myOnlyTracked && myParent.getStrategy(ref) == null;
        return !(isFilteringOptionsRefused) && myMatcher.matches(ref.name());
      }
    });

    List<RowSorter.SortKey> myDefaultSortingKeys = getTableSortingKeys();
    sorter.setSortKeys(myDefaultSortingKeys);
    setRowSorter(sorter);
  }

  @NotNull
  protected List<RowSorter.SortKey> getTableSortingKeys() {
    return Arrays.asList(
      new RowSorter.SortKey(DiffViewTableModel.DIFF_COLUMN_INDEX, SortOrder.DESCENDING),
      new RowSorter.SortKey(DiffViewTableModel.COUNT_COLUMN_INDEX, SortOrder.DESCENDING),
      new RowSorter.SortKey(DiffViewTableModel.CLASSNAME_COLUMN_INDEX, SortOrder.ASCENDING)
    );
  }

  public interface ReferenceCountProvider {

    int getTotalCount(@NotNull TypeInfo ref);

    int getDiffCount(@NotNull TypeInfo ref);

    int getNewInstancesCount(@NotNull TypeInfo ref);
  }

  @Nullable
  public TypeInfo getSelectedClass() {
    int selectedRow = getSelectedRow();
    if (selectedRow != -1) {
      int ix = convertRowIndexToModel(selectedRow);
      return getTypeInfoAt(ix);
    }

    return null;
  }

  @Nullable
  public TypeInfo getClassByName(@NotNull String name) {
    for (TypeInfo ref : myItems) {
      if (name.equals(ref.name())) {
        return ref;
      }
    }

    return null;
  }

  public boolean isInClickableMode() {
    return myMouseListener != null;
  }

  public void makeClickable(@NotNull Runnable onClick) {
    releaseMouseListener();

    AnAction action = new AnAction() {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        onClick.run();
        releaseMouseListener();
      }
    };

    KeyboardShortcut shortcut = new KeyboardShortcut(KeyStroke.getKeyStroke('l', InputEvent.SHIFT_DOWN_MASK), null);
    action.registerCustomShortcutSet(new CustomShortcutSet(shortcut), null);

    MyMouseAdapter listener = new MyMouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        onClick.run();
        releaseMouseListener();
      }

      @Override
      public void mouseEntered(MouseEvent e) {
        updateTable(true);
      }

      @Override
      public void mouseExited(MouseEvent e) {
        updateTable(false);
      }

      @Override
      void updateTable(boolean mouseOnTable) {
        setBackground(mouseOnTable ? CLICKABLE_COLOR : JBColor.background());
        SimpleTextAttributes linkAttributes = mouseOnTable ? UNDERLINE_LINK_ATTRIBUTES : LINK_ATTRIBUTES;
        getEmptyText().clear()
                      .appendText(XDebuggerBundle.message("memory.view.no.classes.loaded")).appendText(" ")
                      .appendText(XDebuggerBundle.message("memory.view.load.classes"), linkAttributes).appendText(" ");
      }
    };

    listener.updateTable(isUnderMouseCursor());

    myMouseListener = listener;
    addMouseListener(myMouseListener);
    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
  }

  private abstract static class MyMouseAdapter extends MouseAdapter {
    abstract void updateTable(boolean mouseOnTable);
  }

  void exitClickableMode() {
    releaseMouseListener();
    getEmptyText().setText(StatusText.getDefaultEmptyText());
  }

  private void releaseMouseListener() {
    ThreadingAssertions.assertEventDispatchThread();
    if (isInClickableMode()) {
      removeMouseListener(myMouseListener);
      myMouseListener = null;
      setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
      setBackground(JBColor.background());
    }
  }

  public void setBusy(boolean value) {
    setPaintBusy(value);
  }

  void setFilterPattern(String pattern) {
    if (!myFilteringPattern.equals(pattern)) {
      myFilteringPattern = pattern;
      myFilterTypingMergeQueue.queue(new Update(myMatcher, true) {
        @Override
        public void run() {
          String newPattern = "*" + myFilteringPattern;
          if (myMatcher.getPattern().equals(newPattern)) {
            return;
          }
          myMatcher = NameUtil.buildMatcher(newPattern).build();
          fireTableDataChanged();
          if (getSelectedClass() == null && getRowCount() > 0) {
            getSelectionModel().setSelectionInterval(0, 0);
          }
        }
      });
    }
  }

  void setFilteringByInstanceExists(boolean value) {
    if (value != myOnlyWithInstances) {
      myOnlyWithInstances = value;
      fireTableDataChanged();
    }
  }

  void setFilteringByDiffNonZero(boolean value) {
    if (myOnlyWithDiff != value) {
      myOnlyWithDiff = value;
      fireTableDataChanged();
    }
  }

  void setFilteringByTrackingState(boolean value) {
    if (myOnlyTracked != value) {
      myOnlyTracked = value;
      fireTableDataChanged();
    }
  }

  @SuppressWarnings("WeakerAccess")
  public void updateClassesOnly(@NotNull List<? extends TypeInfo> classes) {
    myIsShowCounts = false;
    final LinkedHashMap<TypeInfo, Long> class2Count = new LinkedHashMap<>();
    classes.forEach(x -> class2Count.put(x, 0L));
    updateCountsInternal(class2Count);
  }

  @SuppressWarnings("WeakerAccess")
  public void updateContent(@NotNull Map<TypeInfo, Long> class2Count) {
    myIsShowCounts = true;
    updateCountsInternal(class2Count);
  }

  void hideContent(@NotNull @NlsContexts.StatusText String emptyText) {
    releaseMouseListener();
    getEmptyText().setText(emptyText);

    myModel.hide();
  }

  private void showContent() {
    myModel.show();
  }

  private void updateCountsInternal(@NotNull Map<TypeInfo, Long> class2Count) {
    releaseMouseListener();
    getEmptyText().setText(StatusText.getDefaultEmptyText());

    final TypeInfo selectedClass = myModel.getSelectedClassBeforeHide();
    int newSelectedIndex = -1;
    final boolean isInitialized = !myItems.isEmpty();
    myItems = List.copyOf(class2Count.keySet());

    int i = 0;
    for (final TypeInfo ref : class2Count.keySet()) {
      if (ref.equals(selectedClass)) {
        newSelectedIndex = i;
      }

      final DiffValue oldValue = isInitialized && !myCounts.containsKey(ref)
                                 ? new DiffValue(0, 0)
                                 : myCounts.getOrDefault(ref, UNKNOWN_VALUE);
      myCounts.put(ref, oldValue.update(class2Count.get(ref)));

      i++;
    }

    showContent();

    if (newSelectedIndex != -1 && !myModel.isHidden()) {
      final int ix = convertRowIndexToView(newSelectedIndex);
      changeSelection(ix,
                      DiffViewTableModel.CLASSNAME_COLUMN_INDEX, false, false);
    }

    fireTableDataChanged();
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    sink.set(SELECTED_CLASS_KEY, getSelectedClass());
    sink.set(REF_COUNT_PROVIDER_KEY, myCountProvider);
    DataSink.uiDataSnapshot(sink, myParent);
  }

  public void clean(@NotNull @NlsContexts.StatusText String emptyText) {
    clearSelection();
    releaseMouseListener();
    getEmptyText().setText(emptyText);
    myItems = Collections.emptyList();
    myCounts.clear();
    myModel.mySelectedClassWhenHidden = null;
    fireTableDataChanged();
  }

  @Override
  public void dispose() {
    ApplicationManager.getApplication().invokeLater(() -> clean(""));
  }

  private boolean isUnderMouseCursor() {
    if (ApplicationManager.getApplication().isUnitTestMode() || 
        ApplicationManager.getApplication().isHeadlessEnvironment()) return false;
    try {
      return getMousePosition() != null;
    }
    catch (NullPointerException e) { // A workaround for https://bugs.openjdk.org/browse/JDK-6840067
      return false;
    }
  }

  @Nullable
  private TrackingType getTrackingType(int row) {
    TypeInfo ref = (TypeInfo)getValueAt(row, convertColumnIndexToView(DiffViewTableModel.CLASSNAME_COLUMN_INDEX));
    return myInstancesTracker.getTrackingType(ref.name());
  }

  private void fireTableDataChanged() {
    myModel.fireTableDataChanged();
  }

  public class DiffViewTableModel extends AbstractTableModelWithColumns {
    public final static int CLASSNAME_COLUMN_INDEX = 0;
    final static int COUNT_COLUMN_INDEX = 1;
    public final static int DIFF_COLUMN_INDEX = 2;

    // Workaround: save selection after content of classes table has been hided
    private TypeInfo mySelectedClassWhenHidden = null;
    private boolean myIsWithContent = false;

    DiffViewTableModel() {
      super(getColumnDescriptors());
    }

    TypeInfo getSelectedClassBeforeHide() {
      return mySelectedClassWhenHidden;
    }

    void hide() {
      if (myIsWithContent) {
        mySelectedClassWhenHidden = getSelectedClass();
        myIsWithContent = false;
        clearSelection();
        fireTableDataChanged();
      }
    }

    void show() {
      if (!myIsWithContent) {
        myIsWithContent = true;
        fireTableDataChanged();
      }
    }

    boolean isHidden() {
      return !myIsWithContent;
    }

    @Override
    public int getRowCount() {
      return myIsWithContent ? myItems.size() : 0;
    }
  }

  protected AbstractTableColumnDescriptor @NotNull [] getColumnDescriptors() {
    return new AbstractTableColumnDescriptor[]{
      new AbstractTableColumnDescriptor(XDebuggerBundle.message("memory.view.table.column.name.class"), TypeInfo.class) {
        @Override
        public Object getValue(int ix) {
          return getTypeInfoAt(ix);
        }
      },
      new AbstractTableColumnDescriptor(XDebuggerBundle.message("memory.view.table.column.name.count"), Long.class) {
        @Override
        public Object getValue(int ix) {
          return myCounts.getOrDefault(getTypeInfoAt(ix), UNKNOWN_VALUE).myCurrentCount;
        }
      },
      new AbstractTableColumnDescriptor(XDebuggerBundle.message("memory.view.table.column.name.diff"), DiffValue.class) {
        @Override
        public Object getValue(int ix) {
          return myCounts.getOrDefault(getTypeInfoAt(ix), UNKNOWN_VALUE);
        }
      }
    };
  }

  protected TypeInfo getTypeInfoAt(int ix) {
    return myItems.get(ix);
  }

  /**
   * State transmissions for DiffValue and UnknownDiffValue
   * unknown -> diff
   * diff -> diff
   * <p>
   * State descriptions:
   * Unknown - instances count never executed
   * Diff - actual value
   */
  private static class UnknownDiffValue extends DiffValue {
    UnknownDiffValue() {
      super(-1);
    }

    @Override
    boolean hasInstance() {
      return true;
    }

    @Override
    DiffValue update(long count) {
      return new DiffValue(count);
    }
  }

  private static class DiffValue implements Comparable<DiffValue> {
    private long myOldCount;

    private long myCurrentCount;

    DiffValue(long count) {
      this(count, count);
    }

    DiffValue(long old, long current) {
      myCurrentCount = current;
      myOldCount = old;
    }

    DiffValue update(long count) {
      myOldCount = myCurrentCount;
      myCurrentCount = count;
      return this;
    }

    boolean hasInstance() {
      return myCurrentCount > 0;
    }

    long diff() {
      return myCurrentCount - myOldCount;
    }

    @Override
    public int compareTo(@NotNull DiffValue o) {
      return Long.compare(diff(), o.diff());
    }
  }

  public abstract static class MyTableCellRenderer extends ColoredTableCellRenderer {
    @Override
    protected void customizeCellRenderer(@NotNull JTable table, @Nullable Object value, boolean isSelected,
                                         boolean hasFocus, int row, int column) {

      if (hasFocus) {
        setBorder(new EmptyBorder(getBorder().getBorderInsets(this)));
      }

      if (value != null) {
        addText(value, isSelected, row);
      }
    }

    protected abstract void addText(@NotNull Object value, boolean isSelected, int row);
  }

  private class MyClassColumnRenderer extends MyTableCellRenderer {
    @Override
    protected void addText(@NotNull Object value, boolean isSelected,
                           int row) {
      String presentation = ((TypeInfo)value).name();
      append(" ");
      if (isSelected) {
        FList<TextRange> textRanges = myMatcher.matchingFragments(presentation);
        if (textRanges != null) {
          SimpleTextAttributes attributes = new SimpleTextAttributes(getBackground(), getForeground(), null,
                                                                     SimpleTextAttributes.STYLE_SEARCH_MATCH);
          SpeedSearchUtil.appendColoredFragments(this, presentation, textRanges,
                                                 SimpleTextAttributes.REGULAR_ATTRIBUTES, attributes);
        }
      }
      else {
        append(String.format("%s", presentation), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
    }
  }

  private abstract class MyNumericRenderer extends MyTableCellRenderer {
    @Override
    protected void addText(@NotNull Object value, boolean isSelected, int row) {
      if (myIsShowCounts) {
        setTextAlign(SwingConstants.RIGHT);
        appendText(value, row);
      }
    }

    abstract void appendText(@NotNull Object value, int row);
  }

  private class MyCountColumnRenderer extends MyNumericRenderer {
    @Override
    void appendText(@NotNull Object value, int row) {
      //noinspection HardCodedStringLiteral
      append(value.toString());
    }
  }

  private class MyDiffColumnRenderer extends MyNumericRenderer {
    private final SimpleTextAttributes myClickableCellAttributes =
      new SimpleTextAttributes(SimpleTextAttributes.STYLE_UNDERLINE, JBColor.BLUE);

    @Override
    void appendText(@NotNull Object value, int row) {
      TrackingType trackingType = getTrackingType(row);
      if (trackingType != null) {
        setIcon(AllIcons.Debugger.Db_watch);
        setTransparentIconBackground(true);
      }

      TypeInfo ref = getTypeInfoAt(convertRowIndexToModel(row));

      long diff = myCountProvider.getDiffCount(ref);
      String text = String.format("%s%d", diff > 0 ? "+" : "", diff);

      int newInstancesCount = myCountProvider.getNewInstancesCount(ref);
      if (newInstancesCount >= 0) {
        if (newInstancesCount == diff) {
          append(text, diff == 0 ? SimpleTextAttributes.REGULAR_ATTRIBUTES : myClickableCellAttributes);
        }
        else {
          append(text, SimpleTextAttributes.REGULAR_ATTRIBUTES);
          if (newInstancesCount != 0) {
            //noinspection HardCodedStringLiteral
            append(String.format(" (%d)", newInstancesCount), myClickableCellAttributes);
          }
        }
      }
      else {
        append(text, SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
    }
  }
}
