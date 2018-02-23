// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.zmlx.hg4idea.ui;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.TypeSafeDataProvider;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesTreeBrowser;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.table.TableView;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.util.VcsUserUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.log.HgCommit;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

public class HgCommitListPanel extends JPanel implements TypeSafeDataProvider {

  private final List<HgCommit> myCommits;
  private final TableView<HgCommit> myTable;

  public HgCommitListPanel(@NotNull List<HgCommit> commits, @Nullable String emptyText) {
    myCommits = commits;

    myTable = new TableView<>();
    updateModel();
    myTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    myTable.setStriped(true);
    if (emptyText != null) {
      myTable.getEmptyText().setText(emptyText);
    }

    setLayout(new BorderLayout());
    add(ScrollPaneFactory.createScrollPane(myTable));
  }

  /**
   * Adds a listener that would be called once user selects a commit in the table.
   */
  public void addListSelectionListener(final @NotNull Consumer<HgCommit> listener) {
    myTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(final ListSelectionEvent e) {
        ListSelectionModel lsm = (ListSelectionModel)e.getSource();
        int i = lsm.getMaxSelectionIndex();
        int j = lsm.getMinSelectionIndex();
        if (i >= 0 && i == j) {
          listener.consume(myCommits.get(i));
        }
      }
    });
  }

  public void addListMultipleSelectionListener(final @NotNull Consumer<List<Change>> listener) {
    myTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(final ListSelectionEvent e) {
        List<HgCommit> commits = myTable.getSelectedObjects();

        final List<Change> changes = new ArrayList<>();
        // We need changes in asc order for zipChanges, and they are in desc order in Table
        ListIterator<HgCommit> iterator = commits.listIterator(commits.size());
        while (iterator.hasPrevious()) {
          changes.addAll(iterator.previous().getChanges());
        }

        listener.consume(CommittedChangesTreeBrowser.zipChanges(changes));
      }
    });
  }

  /**
   * Registers the diff action which will be called when the diff shortcut is pressed in the table.
   */
  public void registerDiffAction(@NotNull AnAction diffAction) {
    diffAction.registerCustomShortcutSet(diffAction.getShortcutSet(), myTable);
  }

  // Make changes available for diff action
  @Override
  public void calcData(DataKey key, DataSink sink) {
    if (VcsDataKeys.CHANGES.equals(key)) {
      int[] rows = myTable.getSelectedRows();
      if (rows.length != 1) return;
      int row = rows[0];

      HgCommit HgCommit = myCommits.get(row);
      // suppressing: inherited API
      //noinspection unchecked
      sink.put(key, ArrayUtil.toObjectArray(HgCommit.getChanges(), Change.class));
    }
  }

  @NotNull
  public JComponent getPreferredFocusComponent() {
    return myTable;
  }

  public void clearSelection() {
    myTable.clearSelection();
  }

  public void setCommits(@NotNull List<HgCommit> commits) {
    myCommits.clear();
    myCommits.addAll(commits);
    updateModel();
    myTable.repaint();
  }

  private void updateModel() {
    myTable.setModelAndUpdateColumns(new ListTableModel<>(generateColumnsInfo(myCommits), myCommits, 0));
  }

  @NotNull
  private ColumnInfo[] generateColumnsInfo(@NotNull List<HgCommit> commits) {
    ItemAndWidth hash = new ItemAndWidth("", 0);
    ItemAndWidth author = new ItemAndWidth("", 0);
    ItemAndWidth time = new ItemAndWidth("", 0);
    for (HgCommit commit : commits) {
      hash = getMax(hash, getHash(commit));
      author = getMax(author, getAuthor(commit));
      time = getMax(time, getTime(commit));
    }

    return new ColumnInfo[] {
      new HgCommitColumnInfo("Hash", hash.myItem) {
        @Override
        public String valueOf(HgCommit commit) {
          return getHash(commit);
        }
      },
      new ColumnInfo<HgCommit, String>("Subject") {
        @Override
        public String valueOf(HgCommit commit) {
          return commit.getSubject();
        }
      },
      new HgCommitColumnInfo("Author", author.myItem) {
        @Override
        public String valueOf(HgCommit commit) {
          return getAuthor(commit);
        }
      },
      new HgCommitColumnInfo("Author time", time.myItem) {
        @Override
        public String valueOf(HgCommit commit) {
          return getTime(commit);
        }
      }
    };
  }

  private ItemAndWidth getMax(ItemAndWidth current, String candidate) {
    int width = myTable.getFontMetrics(myTable.getFont()).stringWidth(candidate);
    if (width > current.myWidth) {
      return new ItemAndWidth(candidate, width);
    }
    return current;
  }

  private static class ItemAndWidth {
    private final String myItem;
    private final int myWidth;

    private ItemAndWidth(String item, int width) {
      myItem = item;
      myWidth = width;
    }
  }

  private static String getHash(HgCommit commit) {
    return DvcsUtil.getShortHash(commit.getId().toString());
  }

  private static String getAuthor(HgCommit commit) {
    return VcsUserUtil.getShortPresentation(commit.getAuthor());
  }

  private static String getTime(HgCommit commit) {
    return DateFormatUtil.formatPrettyDateTime(commit.getAuthorTime());
  }

  private abstract static class HgCommitColumnInfo extends ColumnInfo<HgCommit, String> {

    @NotNull private final String myMaxString;

    public HgCommitColumnInfo(@NotNull String name, @NotNull String maxString) {
      super(name);
      myMaxString = maxString;
    }

    @Override
    public String getMaxStringValue() {
      return myMaxString;
    }

    @Override
    public int getAdditionalWidth() {
      return UIUtil.DEFAULT_HGAP;
    }
  }

}
