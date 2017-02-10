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
package git4idea.rebase;

import com.intellij.icons.AllIcons;
import com.intellij.ide.CopyProvider;
import com.intellij.ide.TextCopyProvider;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ListWithSelection;
import com.intellij.util.PairFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ComboBoxTableCellRenderer;
import com.intellij.util.ui.EditableModel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import git4idea.GitUtil;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Interactive rebase editor. It allows reordering of the entries and changing commit status.
 */
public class GitRebaseEditor extends DialogWrapper implements DataProvider {

  @NotNull private final Project myProject;
  @NotNull private final VirtualFile myRoot;

  @NotNull private final MyTableModel myTableModel;
  @NotNull private final JBTable myCommitsTable;
  @NotNull private final CopyProvider myCopyProvider;

  protected GitRebaseEditor(@NotNull Project project, @NotNull VirtualFile gitRoot, @NotNull List<GitRebaseEntry> entries)
    throws IOException {
    super(project, true);
    myProject = project;
    myRoot = gitRoot;
    setTitle(GitBundle.getString("rebase.editor.title"));
    setOKButtonText(GitBundle.getString("rebase.editor.button"));

    myTableModel = new MyTableModel(entries);
    myCommitsTable = new JBTable(myTableModel);
    myCommitsTable.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
    myCommitsTable.setIntercellSpacing(JBUI.emptySize());

    final JComboBox editorComboBox = new ComboBox();
    for (Object option : GitRebaseEntry.Action.values()) {
      editorComboBox.addItem(option);
    }
    TableColumn actionColumn = myCommitsTable.getColumnModel().getColumn(MyTableModel.ACTION_COLUMN);
    actionColumn.setCellEditor(new DefaultCellEditor(editorComboBox));
    actionColumn.setCellRenderer(ComboBoxTableCellRenderer.INSTANCE);

    myCommitsTable.setDefaultRenderer(String.class, new ColoredTableCellRenderer() {
      @Override
      protected void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
        if (value != null) {
          append(value.toString());
          SpeedSearchUtil.applySpeedSearchHighlighting(myCommitsTable, this, true, selected);
        }
      }
    });

    myTableModel.addTableModelListener(new TableModelListener() {
      public void tableChanged(final TableModelEvent e) {
        validateFields();
      }
    });

    installSpeedSearch();
    myCopyProvider = new MyCopyProvider();

    adjustColumnWidth(0);
    adjustColumnWidth(1);
    init();
  }

  private void installSpeedSearch() {
    new TableSpeedSearch(myCommitsTable, new PairFunction<Object, Cell, String>() {
      @Nullable
      @Override
      public String fun(Object o, Cell cell) {
        return cell.column == 0 ? null : String.valueOf(o);
      }
    });
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myCommitsTable;
  }

  private void adjustColumnWidth(int columnIndex) {
    int contentWidth = myCommitsTable.getExpandedColumnWidth(columnIndex) + UIUtil.DEFAULT_HGAP;
    TableColumn column = myCommitsTable.getColumnModel().getColumn(columnIndex);
    column.setMaxWidth(contentWidth);
    column.setPreferredWidth(contentWidth);
  }

  private void validateFields() {
    final List<GitRebaseEntry> entries = myTableModel.myEntries;
    if (entries.size() == 0) {
      setErrorText(GitBundle.getString("rebase.editor.invalid.entryset"));
      setOKActionEnabled(false);
      return;
    }
    int i = 0;
    while (i < entries.size() && entries.get(i).getAction() == GitRebaseEntry.Action.skip) {
      i++;
    }
    if (i < entries.size()) {
      GitRebaseEntry.Action action = entries.get(i).getAction();
      if (action == GitRebaseEntry.Action.squash || action == GitRebaseEntry.Action.fixup) {
        setErrorText(GitBundle.message("rebase.editor.invalid.squash", StringUtil.toLowerCase(action.name())));
        setOKActionEnabled(false);
        return;
      }
    }
    setErrorText(null);
    setOKActionEnabled(true);
  }

  protected JComponent createCenterPanel() {
    return ToolbarDecorator.createDecorator(myCommitsTable)
      .disableAddAction()
      .disableRemoveAction()
      .addExtraAction(new MyDiffAction())
      .setMoveUpAction(new MoveUpDownActionListener(MoveDirection.UP))
      .setMoveDownAction(new MoveUpDownActionListener(MoveDirection.DOWN))
      .createPanel();
  }

  @Override
  protected String getDimensionServiceKey() {
    return getClass().getName();
  }

  @Override
  protected String getHelpId() {
    return "reference.VersionControl.Git.RebaseCommits";
  }

  @NotNull
  public List<GitRebaseEntry> getEntries() {
    return myTableModel.myEntries;
  }

  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
    if (PlatformDataKeys.COPY_PROVIDER.is(dataId)) {
      return myCopyProvider;
    }
    return null;
  }

  private class MyTableModel extends AbstractTableModel implements EditableModel {
    private static final int ACTION_COLUMN = 0;
    private static final int HASH_COLUMN = 1;
    private static final int SUBJECT_COLUMN = 2;

    @NotNull private final List<GitRebaseEntry> myEntries;
    private int[] myLastEditableSelectedRows = new int[]{};

    MyTableModel(@NotNull List<GitRebaseEntry> entries) {
      myEntries = entries;
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
      return columnIndex == ACTION_COLUMN ? ListWithSelection.class : String.class;
    }

    @Override
    public String getColumnName(int column) {
      switch (column) {
        case ACTION_COLUMN:
          return GitBundle.getString("rebase.editor.action.column");
        case HASH_COLUMN:
          return GitBundle.getString("rebase.editor.commit.column");
        case SUBJECT_COLUMN:
          return GitBundle.getString("rebase.editor.comment.column");
        default:
          throw new IllegalArgumentException("Unsupported column index: " + column);
      }
    }

    public int getRowCount() {
      return myEntries.size();
    }

    public int getColumnCount() {
      return SUBJECT_COLUMN + 1;
    }

    public Object getValueAt(int rowIndex, int columnIndex) {
      GitRebaseEntry e = myEntries.get(rowIndex);
      switch (columnIndex) {
        case ACTION_COLUMN:
          return new ListWithSelection<>(Arrays.asList(GitRebaseEntry.Action.values()), e.getAction());
        case HASH_COLUMN:
          return e.getCommit();
        case SUBJECT_COLUMN:
          return e.getSubject();
        default:
          throw new IllegalArgumentException("Unsupported column index: " + columnIndex);
      }
    }

    @Override
    public void setValueAt(final Object aValue, final int rowIndex, final int columnIndex) {
      assert columnIndex == ACTION_COLUMN;

      if (ArrayUtil.indexOf(myLastEditableSelectedRows, rowIndex) > -1) {
        ContiguousIntIntervalTracker intervalBuilder = new ContiguousIntIntervalTracker();
        for (int lastEditableSelectedRow : myLastEditableSelectedRows) {
          intervalBuilder.track(lastEditableSelectedRow);
          setRowAction(aValue, lastEditableSelectedRow, columnIndex);
        }
        setSelection(intervalBuilder);
      }
      else {
        setRowAction(aValue, rowIndex, columnIndex);
      }
    }

    @Override
    public void addRow() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void exchangeRows(int oldIndex, int newIndex) {
      GitRebaseEntry movingElement = myEntries.remove(oldIndex);
      myEntries.add(newIndex, movingElement);
      fireTableRowsUpdated(Math.min(oldIndex, newIndex), Math.max(oldIndex, newIndex));
    }

    @Override
    public boolean canExchangeRows(int oldIndex, int newIndex) {
      return true;
    }

    @Override
    public void removeRow(int idx) {
      throw new UnsupportedOperationException();
    }

    @Nullable
    public String getStringToCopy(int row) {
      if (row < 0 || row >= myEntries.size()) {
        return null;
      }
      GitRebaseEntry e = myEntries.get(row);
      return e.getCommit() + " " + e.getSubject();
    }

    private void setSelection(@NotNull ContiguousIntIntervalTracker intervalBuilder) {
      myCommitsTable.getSelectionModel().setSelectionInterval(intervalBuilder.getMin(), intervalBuilder.getMax());
    }

    private void setRowAction(@NotNull Object aValue, int rowIndex, int columnIndex) {
      GitRebaseEntry e = myEntries.get(rowIndex);
      e.setAction((GitRebaseEntry.Action)aValue);
      fireTableCellUpdated(rowIndex, columnIndex);
    }

    @Override
    public boolean isCellEditable(final int rowIndex, final int columnIndex) {
      myLastEditableSelectedRows = myCommitsTable.getSelectedRows();
      return columnIndex == ACTION_COLUMN;
    }

    public void moveRows(@NotNull int[] rows, @NotNull MoveDirection direction) {
      myCommitsTable.removeEditor();

      final ContiguousIntIntervalTracker selectionInterval = new ContiguousIntIntervalTracker();
      final ContiguousIntIntervalTracker rowsUpdatedInterval = new ContiguousIntIntervalTracker();

      for (int row : direction.preprocessRowIndexes(rows)) {
        final int targetIndex = row + direction.offset();
        assertIndexInRange(row, targetIndex);

        Collections.swap(myEntries, row, targetIndex);

        rowsUpdatedInterval.track(targetIndex, row);
        selectionInterval.track(targetIndex);
      }

      if (selectionInterval.hasValues()) {
        setSelection(selectionInterval);
        fireTableRowsUpdated(rowsUpdatedInterval.getMin(), rowsUpdatedInterval.getMax());
      }
    }

    private void assertIndexInRange(int... rowIndexes) {
      for (int rowIndex : rowIndexes) {
        assert rowIndex >= 0;
        assert rowIndex < myEntries.size();
      }
    }
  }

  private static class ContiguousIntIntervalTracker {
    private Integer myMin = null;
    private Integer myMax = null;
    private static final int UNSET_VALUE = -1;

    public Integer getMin() {
      return myMin == null ? UNSET_VALUE : myMin;
    }

    public Integer getMax() {
      return myMax == null ? UNSET_VALUE : myMax;
    }

    public void track( int... entries ) {
      for (int entry : entries) {
        checkMax( entry );
        checkMin( entry );
      }
    }

    private void checkMax(int entry) {
      if ( null == myMax || entry > myMax ) {
        myMax = entry;
      }
    }

    private void checkMin(int entry) {
      if ( null == myMin || entry < myMin ) {
        myMin = entry;
      }
    }

    public boolean hasValues() {
      return ( null != myMin && null != myMax);
    }
  }

  private enum MoveDirection {
    UP,
    DOWN;

    public int offset() {
      return this == UP ? -1 : +1;
    }

    public int[] preprocessRowIndexes(int[] selection) {
      int[] copy = selection.clone();
      Arrays.sort(copy);
      return this == UP ? copy : ArrayUtil.reverseArray(copy);
    }
  }

  private class MyDiffAction extends ToolbarDecorator.ElementActionButton implements DumbAware {
    MyDiffAction() {
      super("View", "View commit contents", AllIcons.Actions.Diff);
      registerCustomShortcutSet(CommonShortcuts.getDiff(), myCommitsTable);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      int row = myCommitsTable.getSelectedRow();
      assert row >= 0 && row < myTableModel.getRowCount();
      GitRebaseEntry entry = myTableModel.myEntries.get(row);
      GitUtil.showSubmittedFiles(myProject, entry.getCommit(), myRoot, false, false);
    }

    @Override
    public boolean isEnabled() {
      return super.isEnabled() && myCommitsTable.getSelectedRowCount() == 1;
    }
  }

  private class MoveUpDownActionListener implements AnActionButtonRunnable {
    private final MoveDirection direction;

    public MoveUpDownActionListener(@NotNull MoveDirection direction) {
      this.direction = direction;
    }

    @Override
    public void run(AnActionButton button) {
      myTableModel.moveRows(myCommitsTable.getSelectedRows(), direction);
    }
  }

  private class MyCopyProvider extends TextCopyProvider {
    @Nullable
    @Override
    public Collection<String> getTextLinesToCopy() {
      if (myCommitsTable.getSelectedRowCount() > 0) {
        List<String> lines = ContainerUtil.newArrayList();
        for (int row : myCommitsTable.getSelectedRows()) {
          lines.add(myTableModel.getStringToCopy(row));
        }
        return lines;
      }
      return null;
    }
  }
}
