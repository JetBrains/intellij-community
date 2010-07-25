/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ListWithSelection;
import com.intellij.util.ui.ComboBoxTableCellEditor;
import com.intellij.util.ui.ComboBoxTableCellRenderer;
import git4idea.actions.GitShowAllSubmittedFilesAction;
import git4idea.commands.StringScanner;
import git4idea.config.GitConfigUtil;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Editor for rebase entries. It allows reordering of
 * the entries and changing commit status.
 */
public class GitRebaseEditor extends DialogWrapper {
  /**
   * The table that lists all commits
   */
  private JTable myCommitsTable;
  /**
   * The move up button
   */
  private JButton myMoveUpButton;
  /**
   * The move down button
   */
  private JButton myMoveDownButton;
  /**
   * The view commit button
   */
  private JButton myViewButton;
  /**
   * The root panel
   */
  private JPanel myPanel;
  /**
   * Table model
   */
  private final MyTableModel myTableModel;
  /**
   * The file name
   */
  private final String myFile;
  /**
   * The project
   */
  private final Project myProject;
  /**
   * The git root
   */
  private final VirtualFile myGitRoot;
  /**
   * The cygwin drive prefix
   */
  @NonNls private static final String CYGDRIVE_PREFIX = "/cygdrive/";

  /**
   * The constructor
   *
   * @param project the project
   * @param gitRoot the git root
   * @param file    the file to edit
   * @throws IOException if file could not be loaded
   */
  protected GitRebaseEditor(final Project project, final VirtualFile gitRoot, String file) throws IOException {
    super(project, true);
    myProject = project;
    myGitRoot = gitRoot;
    setTitle(GitBundle.getString("rebase.editor.title"));
    setOKButtonText(GitBundle.getString("rebase.editor.button"));
    if (SystemInfo.isWindows && file.startsWith(CYGDRIVE_PREFIX)) {
      final int prefixSize = CYGDRIVE_PREFIX.length();
      file = file.substring(prefixSize, prefixSize + 1) + ":" + file.substring(prefixSize + 1);
    }
    myFile = file;
    myTableModel = new MyTableModel();
    myTableModel.load(file);
    myCommitsTable.setModel(myTableModel);
    myCommitsTable.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
    TableColumn actionColumn = myCommitsTable.getColumnModel().getColumn(MyTableModel.ACTION);
    actionColumn.setCellEditor(ComboBoxTableCellEditor.INSTANCE);
    actionColumn.setCellRenderer(ComboBoxTableCellRenderer.INSTANCE);
    myCommitsTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(final ListSelectionEvent e) {
        myViewButton.setEnabled(myCommitsTable.getSelectedRowCount() == 1);
        final ListSelectionModel selectionModel = myCommitsTable.getSelectionModel();
        myMoveUpButton.setEnabled( selectionModel.getMinSelectionIndex() > 0);
        myMoveDownButton.setEnabled( selectionModel.getMaxSelectionIndex() != -1 &&
                                     selectionModel.getMaxSelectionIndex() < myTableModel.myEntries.size() - 1);
      }
    });
    myViewButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        int row = myCommitsTable.getSelectedRow();
        if (row < 0) {
          return;
        }
        GitRebaseEntry entry = myTableModel.myEntries.get(row);
        GitShowAllSubmittedFilesAction.showSubmittedFiles(project, entry.getCommit(), gitRoot);
      }
    });
    myMoveUpButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        final IntIntervalBuilder intervalBuilder = new IntIntervalBuilder();
        for (int rowNo : myCommitsTable.getSelectedRows()) {
          myTableModel.moveUp( rowNo );
          intervalBuilder.register( rowNo - 1 );
        }
        myCommitsTable.getSelectionModel().setSelectionInterval( intervalBuilder.getMin() , intervalBuilder.getMax());
      }
    });
    myMoveDownButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        final IntIntervalBuilder intervalBuilder = new IntIntervalBuilder();
        for (int rowNo : ArrayUtil.reverseArray( myCommitsTable.getSelectedRows() )) {
          myTableModel.moveDown( rowNo );
          intervalBuilder.register( rowNo + 1 );
        }
        myCommitsTable.getSelectionModel().setSelectionInterval( intervalBuilder.getMin() , intervalBuilder.getMax());
      }
    });
    myTableModel.addTableModelListener(new TableModelListener() {
      public void tableChanged(final TableModelEvent e) {
        validateFields();
      }
    });
    init();
  }
  /**
   * Validate fields
   */
  private void validateFields() {
    final ArrayList<GitRebaseEntry> entries = myTableModel.myEntries;
    if (entries.size() == 0) {
      setErrorText(GitBundle.getString("rebase.editor.invalid.entryset"));
      setOKActionEnabled(false);
      return;
    }
    int i = 0;
    while (i < entries.size() && entries.get(i).getAction() == GitRebaseEntry.Action.skip) {
      i++;
    }
    if (i < entries.size() && entries.get(i).getAction() == GitRebaseEntry.Action.squash) {
      setErrorText(GitBundle.getString("rebase.editor.invalid.squash"));
      setOKActionEnabled(false);
      return;
    }
    setErrorText(null);
    setOKActionEnabled(true);
  }

  /**
   * Save entries back to the file
   *
   * @throws IOException if there is IO problem with saving
   */
  public void save() throws IOException {
    myTableModel.save(myFile);
  }

  /**
   * {@inheritDoc}
   */
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected String getDimensionServiceKey() {
    return getClass().getName();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected String getHelpId() {
    return "reference.VersionControl.Git.RebaseCommits";
  }

  /**
   * Cancel rebase
   *
   * @throws IOException if file cannot be reset to empty one
   */
  public void cancel() throws IOException {
    myTableModel.cancel(myFile);
  }


  /**
   * The table model for the commits
   */
  private class MyTableModel extends AbstractTableModel {
    /**
     * The action column
     */
    private static final int ACTION = 0;
    /**
     * The commit hash column
     */
    private static final int COMMIT = 1;
    /**
     * The subject column
     */
    private static final int SUBJECT = 2;

    /**
     * The entries
     */
    final ArrayList<GitRebaseEntry> myEntries = new ArrayList<GitRebaseEntry>();

    /**
     * {@inheritDoc}
     */
    @Override
    public Class<?> getColumnClass(final int columnIndex) {
      return columnIndex == ACTION ? ListWithSelection.class : String.class;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getColumnName(final int column) {
      switch (column) {
        case ACTION:
          return GitBundle.getString("rebase.editor.action.column");
        case COMMIT:
          return GitBundle.getString("rebase.editor.commit.column");
        case SUBJECT:
          return GitBundle.getString("rebase.editor.comment.column");
        default:
          throw new IllegalArgumentException("Unsupported column index: " + column);
      }
    }

    /**
     * {@inheritDoc}
     */
    public int getRowCount() {
      return myEntries.size();
    }

    /**
     * {@inheritDoc}
     */
    public int getColumnCount() {
      return SUBJECT + 1;
    }

    /**
     * {@inheritDoc}
     */
    public Object getValueAt(final int rowIndex, final int columnIndex) {
      GitRebaseEntry e = myEntries.get(rowIndex);
      switch (columnIndex) {
        case ACTION:
          return new ListWithSelection<GitRebaseEntry.Action>(Arrays.asList(GitRebaseEntry.Action.values()), e.getAction());
        case COMMIT:
          return e.getCommit();
        case SUBJECT:
          return e.getSubject();
        default:
          throw new IllegalArgumentException("Unsupported column index: " + columnIndex);
      }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings({"unchecked"})
    public void setValueAt(final Object aValue, final int rowIndex, final int columnIndex) {
      assert columnIndex == ACTION;
      GitRebaseEntry e = myEntries.get(rowIndex);
      e.setAction((GitRebaseEntry.Action)aValue);
      fireTableCellUpdated(rowIndex, columnIndex);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isCellEditable(final int rowIndex, final int columnIndex) {
      return columnIndex == ACTION;
    }

    /**
     * Load data from the file
     *
     * @param file the file to load
     * @throws IOException if file could not be loaded
     */
    public void load(final String file) throws IOException {
      String encoding = GitConfigUtil.getLogEncoding(myProject, myGitRoot);
      final StringScanner s = new StringScanner(new String(FileUtil.loadFileText(new File(file), encoding)));
      while (s.hasMoreData()) {
        if (s.isEol() || s.startsWith('#') || s.startsWith("noop")) {
          s.nextLine();
          continue;
        }
        String action = s.spaceToken();
        assert "pick".equals(action) : "Initial action should be pick: " + action;
        String hash = s.spaceToken();
        String comment = s.line();
        myEntries.add(new GitRebaseEntry(hash, comment));
      }
    }

    /**
     * Save text to the file
     *
     * @param file the file to save to
     * @throws IOException if there is IO problem
     */
    public void save(final String file) throws IOException {
      String encoding = GitConfigUtil.getLogEncoding(myProject, myGitRoot);
      PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), encoding));
      try {
        for (GitRebaseEntry e : myEntries) {
          if (e.getAction() != GitRebaseEntry.Action.skip) {
            out.println(e.getAction().toString() + " " + e.getCommit() + " " + e.getSubject());
          }
        }
      }
      finally {
        out.close();
      }
    }

    /**
     * Save text to the file
     *
     * @param file the file to save to
     * @throws IOException if there is IO problem
     */
    public void cancel(final String file) throws IOException {
      PrintWriter out = new PrintWriter(new FileWriter(file));
      try {
        //noinspection HardCodedStringLiteral
        out.println("# rebase is cancelled");
      }
      finally {
        out.close();
      }
    }


    /**
     * Move selected row up. If row cannot be moved up, do nothing and return false.
     *
     * @param row a row to move
     * @return true if row was moved
     */
    public boolean moveUp(final int row) {
      if (row < 1 || row >= myEntries.size()) {
        return false;
      }
      myCommitsTable.removeEditor();
      GitRebaseEntry e = myEntries.get(row);
      myEntries.set(row, myEntries.get(row - 1));
      myEntries.set(row - 1, e);
      fireTableRowsUpdated(row - 1, row);
      return true;
    }

    /**
     * Move selected row down. If row cannot be moved down, do nothing and return false.
     *
     * @param row a row to move
     * @return true if row was moved
     */
    public boolean moveDown(final int row) {
      if (row < 0 || row >= myEntries.size() - 1) {
        return false;
      }
      myCommitsTable.removeEditor();
      GitRebaseEntry e = myEntries.get(row);
      myEntries.set(row, myEntries.get(row + 1));
      myEntries.set(row + 1, e);
      fireTableRowsUpdated(row, row + 1);
      return true;
    }
  }

  private static class IntIntervalBuilder {
    private Integer myMin = null;
    private Integer myMax = null;

    public Integer getMin() {
      return myMin;
    }

    public Integer getMax() {
      return myMax;
    }

    public void register( int entry ) {
      checkMax( entry );
      checkMin( entry );
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

  }

}
