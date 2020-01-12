// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase;

import com.intellij.ide.CopyProvider;
import com.intellij.ide.TextCopyProvider;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBoxTableRenderer;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesTreeBrowser;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EditableModel;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.intellij.vcs.log.VcsCommitMetadata;
import com.intellij.vcs.log.graph.DefaultColorGenerator;
import com.intellij.vcs.log.graph.EdgePrintElement;
import com.intellij.vcs.log.graph.NodePrintElement;
import com.intellij.vcs.log.graph.PrintElement;
import com.intellij.vcs.log.paint.SimpleGraphCellPainter;
import com.intellij.vcs.log.ui.details.FullCommitDetailsListPanel;
import git4idea.history.GitCommitRequirements;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.*;

import static git4idea.history.GitLogUtil.readFullDetailsForHashes;

/**
 * Interactive rebase editor. It allows reordering of the entries and changing commit status.
 */
public class GitRebaseEditor extends DialogWrapper implements DataProvider {

  @NotNull private static final String DETAILS_PROPORTION = "Git.Interactive.Rebase.Details.Proportion";
  @NotNull private static final String DIMENSION_KEY = "Git.Interactive.Rebase.Dialog";
  private static final int DIALOG_HEIGHT = 450;
  private static final int DIALOG_WIDTH = 800;

  @NotNull private final MyTableModel myTableModel;
  @NotNull private final JBTable myCommitsTable;
  @NotNull private final CopyProvider myCopyProvider;
  @NotNull private final FullCommitDetailsListPanel myFullCommitDetailsListPanel;

  private boolean myModified;

  protected GitRebaseEditor(@NotNull Project project, @NotNull VirtualFile gitRoot, @NotNull List<GitRebaseEntryWithDetails> entries) {
    super(project, true);
    setTitle(GitBundle.getString("rebase.editor.title"));
    setOKButtonText(GitBundle.getString("rebase.editor.button"));

    myTableModel = new MyTableModel(entries);
    myTableModel.addTableModelListener(e -> validateFields());
    myTableModel.addTableModelListener(e -> myModified = true);

    myFullCommitDetailsListPanel = new FullCommitDetailsListPanel(project, getDisposable(), ModalityState.stateForComponent(getWindow())) {
      @NotNull
      @Override
      protected List<Change> loadChanges(@NotNull List<? extends VcsCommitMetadata> commits) throws VcsException {
        List<Change> changes = new ArrayList<>();
        readFullDetailsForHashes(
          project,
          gitRoot,
          ContainerUtil.map(commits, commit -> commit.getId().asString()),
          GitCommitRequirements.DEFAULT,
          gitCommit -> changes.addAll(gitCommit.getChanges())
        );
        return CommittedChangesTreeBrowser.zipChanges(changes);
      }
    };

    myCommitsTable = new JBTable(myTableModel);
    myCommitsTable.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
    myCommitsTable.setIntercellSpacing(JBUI.emptySize());
    myCommitsTable.setDefaultRenderer(String.class, new ColoredTableCellRenderer() {
      @Override
      protected void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
        if (value != null) {
          setBorder(null);
          append(value.toString());
          SpeedSearchUtil.applySpeedSearchHighlighting(myCommitsTable, this, true, selected);
        }
      }
    });
    myCommitsTable.getSelectionModel().addListSelectionListener(e -> {
      if (e.getValueIsAdjusting()) {
        return;
      }
      List<VcsCommitMetadata> selectedCommits = new ArrayList<>();
      int[] selectedEntries = myCommitsTable.getSelectedRows();
      for (int selectedEntry : selectedEntries) {
        selectedCommits.add(myTableModel.myEntries.get(selectedEntry).getCommitDetails());
      }
      myFullCommitDetailsListPanel.commitsSelected(selectedCommits);
    });
    myCommitsTable.setTableHeader(null);

    TableColumn actionColumn = myCommitsTable.getColumnModel().getColumn(MyTableModel.ACTION_COLUMN);
    actionColumn.setCellEditor(new ComboBoxTableRenderer<>(GitRebaseEntry.Action.getKnownActionsArray()).withClickCount(1));
    actionColumn.setCellRenderer(new ComboBoxTableRenderer<>(GitRebaseEntry.Action.getKnownActionsArray()));

    List<AnAction> actions = generateSelectRebaseActionActions();
    for (AnAction action : actions) {
      action.registerCustomShortcutSet(myCommitsTable, null);
    }
    PopupHandler.installRowSelectionTablePopup(myCommitsTable,
                                               new DefaultActionGroup(actions),
                                               ActionPlaces.EDITOR_POPUP,
                                               ActionManager.getInstance());

    installSpeedSearch();
    myCopyProvider = new MyCopyProvider();

    TableColumn commitIconColor = myCommitsTable.getColumnModel().getColumn(MyTableModel.COMMIT_ICON_COLUMN);
    MyCommitIconRenderer renderer = new MyCommitIconRenderer();
    commitIconColor.setCellRenderer(new TableCellRenderer() {
      @Override
      public Component getTableCellRendererComponent(JTable table,
                                                     Object value,
                                                     boolean isSelected,
                                                     boolean hasFocus,
                                                     int row,
                                                     int column) {
        renderer.update(table, isSelected, hasFocus, row, column, row == table.getRowCount() - 1);
        return renderer;
      }
    });

    adjustColumnWidth(MyTableModel.COMMIT_ICON_COLUMN);
    adjustColumnWidth(MyTableModel.ACTION_COLUMN);
    init();
  }

  private void installSpeedSearch() {
    new TableSpeedSearch(myCommitsTable, (o, cell) -> cell.column == 0 ? null : String.valueOf(o));
  }

  @Override
  public void doCancelAction() {
    if (myModified) {
      int ans = Messages.showDialog(getRootPane(), GitBundle.getString("rebase.editor.discard.modifications.message"),
                                    "Cancel Rebase", new String[]{"Discard", "Continue Rebasing"}, 0, Messages.getQuestionIcon());
      if (ans != Messages.YES) return;
    }
    super.doCancelAction();
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myCommitsTable;
  }

  private void adjustColumnWidth(int columnId) {
    int contentWidth = myCommitsTable.getExpandedColumnWidth(columnId) + UIUtil.DEFAULT_HGAP;
    TableColumn column = myCommitsTable.getColumnModel().getColumn(columnId);
    column.setMaxWidth(contentWidth);
    column.setPreferredWidth(contentWidth);
  }

  private void validateFields() {
    final List<GitRebaseEntryWithDetails> entries = myTableModel.myEntries;
    if (entries.size() == 0) {
      setErrorText(GitBundle.getString("rebase.editor.invalid.entryset"), myCommitsTable);
      setOKActionEnabled(false);
      return;
    }
    int i = 0;
    while (i < entries.size() && entries.get(i).getAction() == GitRebaseEntry.Action.DROP.INSTANCE) {
      i++;
    }
    if (i < entries.size()) {
      GitRebaseEntry.Action action = entries.get(i).getAction();
      if (action == GitRebaseEntry.Action.SQUASH.INSTANCE || action == GitRebaseEntry.Action.FIXUP.INSTANCE) {
        setErrorText(GitBundle.message("rebase.editor.invalid.squash", StringUtil.toLowerCase(action.getName())), myCommitsTable);
        setOKActionEnabled(false);
        return;
      }
    }
    setErrorText(null);
    setOKActionEnabled(true);
  }

  @Override
  protected JComponent createCenterPanel() {
    JBSplitter detailsSplitter = new OnePixelSplitter(DETAILS_PROPORTION, 0.5f);
    JPanel tablePanel = ToolbarDecorator.createDecorator(myCommitsTable)
      .disableAddAction()
      .disableRemoveAction()
      .setMoveUpAction(new MoveUpDownActionListener(MoveDirection.UP))
      .setMoveDownAction(new MoveUpDownActionListener(MoveDirection.DOWN))
      .createPanel();
    tablePanel.setBorder(JBUI.Borders.empty());
    detailsSplitter.setFirstComponent(tablePanel);
    detailsSplitter.setSecondComponent(myFullCommitDetailsListPanel);
    BorderLayoutPanel centerPanel = new BorderLayoutPanel().addToCenter(detailsSplitter);
    centerPanel.setPreferredSize(new JBDimension(DIALOG_WIDTH, DIALOG_HEIGHT));
    return centerPanel;
  }

  @NotNull
  @Override
  protected DialogStyle getStyle() {
    return DialogStyle.COMPACT;
  }

  @NotNull
  private List<AnAction> generateSelectRebaseActionActions() {
    return ContainerUtil.map(GitRebaseEntry.Action.getKnownActions(), SetActionAction::new);
  }

  @Override
  protected String getDimensionServiceKey() {
    return DIMENSION_KEY;
  }

  @Override
  protected String getHelpId() {
    return "reference.VersionControl.Git.RebaseCommits";
  }

  @NotNull
  public List<? extends GitRebaseEntry> getEntries() {
    return myTableModel.myEntries;
  }

  @Nullable
  @Override
  public Object getData(@NotNull @NonNls String dataId) {
    if (PlatformDataKeys.COPY_PROVIDER.is(dataId)) {
      return myCopyProvider;
    }
    return null;
  }

  private static class MyCommitIcon {
    @NotNull static MyCommitIcon INSTANCE = new MyCommitIcon();
  }

  private class MyTableModel extends AbstractTableModel implements EditableModel {
    private static final int COMMIT_ICON_COLUMN = 0;
    private static final int ACTION_COLUMN = 1;
    private static final int SUBJECT_COLUMN = 2;

    @NotNull private final List<GitRebaseEntryWithDetails> myEntries;
    private int[] myLastEditableSelectedRows = new int[]{};

    MyTableModel(@NotNull List<GitRebaseEntryWithDetails> entries) {
      myEntries = entries;
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
      switch (columnIndex) {
        case COMMIT_ICON_COLUMN:
          return MyCommitIcon.class;
        case ACTION_COLUMN:
          return GitRebaseEntry.Action.class;
        case SUBJECT_COLUMN:
          return String.class;
        default:
          throw new IllegalArgumentException("Unsupported column index: " + columnIndex);
      }
    }

    @Override
    public int getRowCount() {
      return myEntries.size();
    }

    @Override
    public int getColumnCount() {
      return SUBJECT_COLUMN + 1;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      GitRebaseEntry e = myEntries.get(rowIndex);
      switch (columnIndex) {
        case COMMIT_ICON_COLUMN:
          return MyCommitIcon.INSTANCE;
        case ACTION_COLUMN:
          return e.getAction();
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
      GitRebaseEntryWithDetails movingElement = myEntries.remove(oldIndex);
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
      return e.getSubject();
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

    public void moveRows(int @NotNull [] rows, @NotNull MoveDirection direction) {
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

    public void track(int... entries) {
      for (int entry : entries) {
        checkMax(entry);
        checkMin(entry);
      }
    }

    private void checkMax(int entry) {
      if (null == myMax || entry > myMax) {
        myMax = entry;
      }
    }

    private void checkMin(int entry) {
      if (null == myMin || entry < myMin) {
        myMin = entry;
      }
    }

    public boolean hasValues() {
      return (null != myMin && null != myMax);
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

  private class SetActionAction extends DumbAwareAction {
    private final GitRebaseEntry.Action myAction;

    SetActionAction(GitRebaseEntry.Action action) {
      super(action.toString());
      myAction = action;
      KeyStroke keyStroke = KeyStroke.getKeyStroke(KeyEvent.getExtendedKeyCodeForChar(action.getMnemonic()), InputEvent.ALT_MASK);
      setShortcutSet(new CustomShortcutSet(new KeyboardShortcut(keyStroke, null)));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      int[] selectedRows = myCommitsTable.getSelectedRows();
      for (int i : selectedRows) {
        myTableModel.setValueAt(myAction, i, MyTableModel.ACTION_COLUMN);
      }
    }
  }

  private class MoveUpDownActionListener implements AnActionButtonRunnable {
    private final MoveDirection direction;

    MoveUpDownActionListener(@NotNull MoveDirection direction) {
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
        List<String> lines = new ArrayList<>();
        for (int row : myCommitsTable.getSelectedRows()) {
          lines.add(myTableModel.getStringToCopy(row));
        }
        return lines;
      }
      return null;
    }
  }

  private static class MyCommitIconRenderer extends SimpleColoredRenderer {
    @NotNull static final EdgePrintElement UP_EDGE = getEdge(EdgePrintElement.Type.UP);
    @NotNull static final EdgePrintElement DOWN_EDGE = getEdge(EdgePrintElement.Type.DOWN);
    @NotNull static final NodePrintElement NODE = getNode();
    @NotNull final SimpleGraphCellPainter myPainter;

    private boolean myIsHead = false;

    MyCommitIconRenderer() {
      JBColor nodeColor = new DefaultColorGenerator().getColor(1);
      myPainter = new SimpleGraphCellPainter(colorId -> nodeColor);
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      drawCommitIcon((Graphics2D)g);
    }

    void update(JTable table, boolean isSelected, boolean hasFocus, int row, int column, boolean isHead) {
      clear();
      setPaintFocusBorder(false);
      acquireState(table, isSelected, hasFocus, row, column);
      getCellState().updateRenderer(this);
      setBorder(null);
      myIsHead = isHead;
    }

    private void drawCommitIcon(Graphics2D g2) {
      List<PrintElement> elements = new ArrayList<>();
      elements.add(UP_EDGE);
      elements.add(NODE);
      if (!myIsHead) {
        elements.add(DOWN_EDGE);
      }
      myPainter.draw(g2, elements);
    }

    @NotNull
    private static EdgePrintElement getEdge(EdgePrintElement.Type type) {
      return new EdgePrintElement() {
        @Override
        public int getPositionInOtherRow() {
          return 0;
        }

        @NotNull
        @Override
        public Type getType() {
          return type;
        }

        @NotNull
        @Override
        public LineStyle getLineStyle() {
          return LineStyle.SOLID;
        }

        @Override
        public boolean hasArrow() {
          return false;
        }

        @Override
        public int getRowIndex() {
          return 0;
        }

        @Override
        public int getPositionInCurrentRow() {
          return 0;
        }

        @Override
        public int getColorId() { return 0; }

        @Override
        public boolean isSelected() {
          return false;
        }
      };
    }

    @NotNull
    private static NodePrintElement getNode() {
      return new NodePrintElement() {
        @Override
        public int getRowIndex() {
          return 0;
        }

        @Override
        public int getPositionInCurrentRow() {
          return 0;
        }

        @Override
        public int getColorId() {
          return 0;
        }

        @Override
        public boolean isSelected() {
          return false;
        }
      };
    }
  }
}
