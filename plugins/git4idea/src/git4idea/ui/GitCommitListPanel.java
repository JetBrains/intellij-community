/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package git4idea.ui;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.ColumnInfo;
import git4idea.history.browser.GitCommit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * A table with the list of commits.
 *
 * @author Kirill Likhodedov
 */
public class GitCommitListPanel extends JPanel implements TypeSafeDataProvider {

  private static final List<ColumnInfo<GitCommit, Object>> COLUMNS_INFO = initColumnsInfo();

  private final Project myProject;
  private final List<GitCommit> myCommits;
  private final JBTable myTable;

  public GitCommitListPanel(@NotNull Project project, @NotNull List<GitCommit> commits) {
    myProject = project;
    myCommits = commits;

    TableModel tableModel = new GitCommitListTableModel(myCommits);

    myTable = new JBTable(tableModel);
    myTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myTable.setStriped(true);

    setLayout(new BorderLayout());
    add(ScrollPaneFactory.createScrollPane(myTable));
  }

  /**
   * Adds a listener that would be called once user selects a commit in the table.
   */
  public void addListSelectionListener(final @NotNull Consumer<GitCommit> listener) {
    myTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(final ListSelectionEvent e) {
        ListSelectionModel lsm = (ListSelectionModel)e.getSource();
        int i = lsm.getMaxSelectionIndex();
        if (i >= 0) {
          listener.consume(myCommits.get(i));
        }
      }
    });
  }

  /**
   * Registers the diff action which will be called when the diff shortcut is pressed in the table.
   */
  public void registerDiffAction(@NotNull AnAction diffAction) {
    diffAction.registerCustomShortcutSet(CommonShortcuts.getDiff(), myTable);
  }

  // Make changes available for diff action
  @Override
  public void calcData(DataKey key, DataSink sink) {
    if (VcsDataKeys.CHANGES.equals(key)) {
      int[] rows = myTable.getSelectedRows();
      if (rows.length != 1) return;
      int row = rows[0];

      GitCommit gitCommit = myCommits.get(row);
      sink.put(key, ArrayUtil.toObjectArray(gitCommit.getChanges(), Change.class));
    }
  }

  @NotNull
  public JComponent getPreferredFocusComponent() {
    return myTable;
  }

  public void clearSelection() {
    myTable.clearSelection();
  }

  public void setCommits(@NotNull List<GitCommit> commits) {
    myCommits.clear();
    myCommits.addAll(commits);
    myTable.setModel(new GitCommitListTableModel(myCommits));
    myTable.repaint();
  }

  private static class GitCommitListTableModel extends AbstractTableModel {

    private final List<GitCommit> myCommits;

    GitCommitListTableModel(List<GitCommit> commits) {
      myCommits = commits;
    }

    @Override
    public int getRowCount() {
      return myCommits.size();
    }

    @Override
    public int getColumnCount() {
      return COLUMNS_INFO.size();
    }

    @Override
    public String getColumnName(int column) {
      return COLUMNS_INFO.get(column).getName();
    }

    @Nullable
    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      GitCommit commit = myCommits.get(rowIndex);
      return COLUMNS_INFO.get(columnIndex).valueOf(commit);
    }
  }

  private static List<ColumnInfo<GitCommit, Object>> initColumnsInfo() {
    List<ColumnInfo<GitCommit, Object>> infos = new ArrayList<ColumnInfo<GitCommit, Object>>();
    infos.add(new ColumnInfo<GitCommit, Object>("Hash") {
      @Override
      public Object valueOf(GitCommit commit) {
        return commit.getShortHash();
      }
    });
    infos.add(new ColumnInfo<GitCommit, Object>("Subject") {
      @Override
      public Object valueOf(GitCommit commit) {
        return commit.getSubject();
      }
    });
    infos.add(new ColumnInfo<GitCommit, Object>("Author") {
      @Override
      public Object valueOf(GitCommit commit) {
        return commit.getAuthor();
      }
    });
    infos.add(new ColumnInfo<GitCommit, Object>("Author time") {
      @Override
      public Object valueOf(GitCommit commit) {
        return DateFormatUtil.formatPrettyDateTime(commit.getAuthorTime());
      }
    });
    return infos;
  }

}
