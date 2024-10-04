// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.history.*;
import com.intellij.openapi.vcs.impl.VcsBackgroundableActions;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.ui.dualView.TreeTableView;
import com.intellij.ui.table.TableView;
import com.intellij.ui.treeStructure.treetable.ListTreeTableModelOnColumns;
import com.intellij.util.Consumer;
import com.intellij.util.TreeItem;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.ListTableModel;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.util.Date;
import java.util.List;

import static java.util.Objects.requireNonNull;

@ApiStatus.Internal
public final class CompareWithSelectedRevisionAction extends DumbAwareAction {
  private static class Holder {
    private static final ColumnInfo<TreeNodeAdapter, String> BRANCH_COLUMN =
      new ColumnInfo<>(VcsBundle.message("column.name.revisions.list.branch")) {
        @Override
        public String valueOf(final TreeNodeAdapter object) {
          return object.getRevision().getBranchName();
        }
      };

    private static final ColumnInfo<TreeNodeAdapter, String> REVISION_COLUMN =
      new ColumnInfo<>(VcsBundle.message("column.name.revision.list.revision")) {
        @Override
        public String valueOf(final TreeNodeAdapter object) {
          return object.getRevision().getRevisionNumber().asString();
        }
      };

    private static final ColumnInfo<TreeNodeAdapter, String> DATE_COLUMN =
      new ColumnInfo<>(VcsBundle.message("column.name.revisions.list.filter")) {
        @Override
        public String valueOf(final TreeNodeAdapter object) {
          return DateFormatUtil.formatPrettyDateTime(object.getRevision().getRevisionDate());
        }
      };

    private static final ColumnInfo<TreeNodeAdapter, String> AUTHOR_COLUMN =
      new ColumnInfo<>(VcsBundle.message("column.name.revision.list.author")) {
        @Override
        public String valueOf(final TreeNodeAdapter object) {
          return object.getRevision().getAuthor();
        }
      };

    private static final ColumnInfo<VcsFileRevision, String> REVISION_TABLE_COLUMN =
      new ColumnInfo<>(VcsBundle.message("column.name.revision.list.revision")) {
        @Override
        public String valueOf(final VcsFileRevision vcsFileRevision) {
          return vcsFileRevision.getRevisionNumber().asString();
        }
      };

    private static final ColumnInfo<VcsFileRevision, String> DATE_TABLE_COLUMN =
      new ColumnInfo<>(VcsBundle.message("column.name.revision.list.revision")) {
        @Override
        public String valueOf(final VcsFileRevision vcsFileRevision) {
          final Date date = vcsFileRevision.getRevisionDate();
          return date == null ? "" : DateFormatUtil.formatPrettyDateTime(date);
        }
      };

    private static final ColumnInfo<VcsFileRevision, String> AUTHOR_TABLE_COLUMN =
      new ColumnInfo<>(VcsBundle.message("column.name.revision.list.author")) {
        @Override
        public String valueOf(final VcsFileRevision vcsFileRevision) {
          return vcsFileRevision.getAuthor();
        }
      };

    private static final ColumnInfo<VcsFileRevision, String> BRANCH_TABLE_COLUMN =
      new ColumnInfo<>(VcsBundle.message("column.name.revisions.list.branch")) {
        @Override
        public String valueOf(final VcsFileRevision vcsFileRevision) {
          return vcsFileRevision.getBranchName();
        }
      };
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    final VirtualFile file = VcsContextUtil.selectedFile(e.getDataContext());
    if (file != null && file.isDirectory()) {
      Project project = e.getProject();
      e.getPresentation().setVisible(isVisibleForDirectory(project));
      e.getPresentation().setEnabled(isEnabledForDirectory(project, file));
    }
    else {
      AbstractShowDiffAction.updateDiffAction(e.getPresentation(), e.getDataContext());
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final VirtualFile file = requireNonNull(VcsContextUtil.selectedFile(e.getDataContext()));
    final Project project = requireNonNull(e.getProject());
    final AbstractVcs vcs = requireNonNull(ProjectLevelVcsManager.getInstance(project).getVcsFor(file));

    VcsCachingHistory.collectInBackground(vcs, VcsUtil.getFilePath(file), VcsBackgroundableActions.COMPARE_WITH, session -> {
      if (session == null) return;
      final List<VcsFileRevision> revisions = session.getRevisionList();
      final HistoryAsTreeProvider treeHistoryProvider = session.getHistoryAsTreeProvider();
      if (treeHistoryProvider != null) {
        showTreePopup(treeHistoryProvider.createTreeOn(revisions), project,
                      selected -> showSelectedRevision(selected.getRevisionNumber(), vcs, file, project));
      }
      else {
        showListPopup(revisions, project,
                      selected -> showSelectedRevision(selected.getRevisionNumber(), vcs, file, project),
                      true);
      }
    });
  }

  private static void showSelectedRevision(@NotNull VcsRevisionNumber selected, @NotNull AbstractVcs vcs,
                                           @NotNull VirtualFile file, @NotNull Project project) {
    if (file.isDirectory()) {
      final DiffProvider diffProvider = requireNonNull(vcs.getDiffProvider());
      VcsDiffUtil.showChangesWithWorkingDirLater(
        project,
        file,
        selected,
        diffProvider
      );
    }
    else {
      DiffActionExecutor.showDiff(vcs.getDiffProvider(), selected, file, project);
    }
  }

  private static void showTreePopup(final List<TreeItem<VcsFileRevision>> roots, final Project project,
                                    final Consumer<? super VcsFileRevision> selectedRevisionConsumer) {

    final TreeTableView treeTable = new TreeTableView(new ListTreeTableModelOnColumns(new TreeNodeAdapter(null, null, roots),
                                                                                      new ColumnInfo[]{Holder.BRANCH_COLUMN, Holder.REVISION_COLUMN,
                                                                                        Holder.DATE_COLUMN, Holder.AUTHOR_COLUMN}));
    Runnable runnable = () -> {
      int index = treeTable.getSelectionModel().getMinSelectionIndex();
      if (index == -1) {
        return;
      }
      VcsFileRevision revision = getRevisionAt(treeTable, index);
      if (revision != null) {
        selectedRevisionConsumer.consume(revision);
      }
    };

    treeTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    new PopupChooserBuilder(treeTable).
      setTitle(VcsBundle.message("lookup.title.vcs.file.revisions")).
      setItemChosenCallback(runnable).
      setSouthComponent(createCommentsPanel(treeTable)).
      setResizable(true).
      setDimensionServiceKey("Vcs.CompareWithSelectedRevision.Popup").
      createPopup().
      showCenteredInCurrentWindow(project);

    final int lastRow = treeTable.getRowCount() - 1;
    if (lastRow < 0) return;
    treeTable.getSelectionModel().addSelectionInterval(lastRow, lastRow);
    treeTable.scrollRectToVisible(treeTable.getCellRect(lastRow, 0, true));
  }


  @Nullable private static VcsFileRevision getRevisionAt(final TreeTableView treeTable, final int index) {
    final List items = treeTable.getItems();
    if (items.size() <= index) {
      return null;
    } else {
      return ((TreeNodeAdapter)items.get(index)).getRevision();
    }

  }

  private static JPanel createCommentsPanel(final TreeTableView treeTable) {
    JPanel panel = new JPanel(new BorderLayout());
    final JTextArea textArea = createTextArea();
    treeTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(@NotNull ListSelectionEvent e) {
        final int index = treeTable.getSelectionModel().getMinSelectionIndex();
        if (index == -1) {
          textArea.setText("");
        } else {
          final VcsFileRevision revision = getRevisionAt(treeTable, index);
          if (revision != null) {
            textArea.setText(revision.getCommitMessage());
          } else {
            textArea.setText("");
          }
        }
      }
    });
    final JScrollPane textScrollPane = ScrollPaneFactory.createScrollPane(textArea);
    panel.add(textScrollPane, BorderLayout.CENTER);
    textScrollPane.setBorder(IdeBorderFactory.createTitledBorder(VcsBundle.message("border.selected.revision.commit.message"), false
    ));
    return panel;
  }

  private static JTextArea createTextArea() {
    final JTextArea textArea = new JTextArea();
    textArea.setRows(5);
    textArea.setEditable(false);
    textArea.setWrapStyleWord(true);
    textArea.setLineWrap(true);
    return textArea;
  }

  public static void showListPopup(final List<VcsFileRevision> revisions, final Project project, final Consumer<? super VcsFileRevision> selectedRevisionConsumer,
                                   final boolean showComments) {
    ColumnInfo[] columns = new ColumnInfo[] { Holder.REVISION_TABLE_COLUMN, Holder.DATE_TABLE_COLUMN, Holder.AUTHOR_TABLE_COLUMN };
    for(VcsFileRevision revision: revisions) {
      if (revision.getBranchName() != null) {
        columns = new ColumnInfo[] { Holder.REVISION_TABLE_COLUMN, Holder.BRANCH_TABLE_COLUMN, Holder.DATE_TABLE_COLUMN, Holder.AUTHOR_TABLE_COLUMN };
        break;
      }
    }
    final TableView<VcsFileRevision> table = new TableView<>(new ListTableModel<>(columns, revisions, 0));
    table.setShowHorizontalLines(false);
    table.setTableHeader(null);
    Runnable runnable = () -> {
      VcsFileRevision revision = table.getSelectedObject();
      if (revision != null) {
        selectedRevisionConsumer.consume(revision);
      }
    };

    if (table.getModel().getRowCount() == 0) {
      table.clearSelection();
    }

    SpeedSearchBase<TableView> search = new SpeedSearchBase<>(table, null) {
      @Override
      protected int getSelectedIndex() {
        return table.getSelectedRow();
      }

      @Override
      protected int getElementCount() {
        return revisions.size();
      }

      @Override
      protected Object getElementAt(int viewIndex) {
        return revisions.get(table.convertRowIndexToModel(viewIndex));
      }

      @Override
      protected String getElementText(Object element) {
        VcsFileRevision revision = (VcsFileRevision)element;
        return revision.getRevisionNumber().asString() + " " + revision.getBranchName() + " " + revision.getAuthor();
      }

      @Override
      protected void selectElement(Object element, String selectedText) {
        VcsFileRevision revision = (VcsFileRevision)element;
        TableUtil.selectRows(myComponent, new int[]{myComponent.convertRowIndexToView(revisions.indexOf(revision))});
        TableUtil.scrollSelectionToVisible(myComponent);
      }
    };
    search.setupListeners();

    table.setMinimumSize(new JBDimension(300, 50));
    final PopupChooserBuilder builder = new PopupChooserBuilder(table);

    if (showComments) {
      builder.setSouthComponent(createCommentsPanel(table));
    }

    builder.setTitle(VcsBundle.message("lookup.title.vcs.file.revisions")).
        setItemChosenCallback(runnable).
        setResizable(true).
        setMovable(true).
        setDimensionServiceKey("Vcs.CompareWithSelectedRevision.Popup").setMinSize(new JBDimension(300, 300));
    final JBPopup popup = builder.createPopup();

    popup.showCenteredInCurrentWindow(project);
  }

  private static JPanel createCommentsPanel(final TableView<VcsFileRevision> table) {
    final JTextArea textArea = createTextArea();
    table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(@NotNull ListSelectionEvent e) {
        final VcsFileRevision revision = table.getSelectedObject();
        if (revision == null) {
          textArea.setText("");
        } else {
          textArea.setText(revision.getCommitMessage());
          textArea.select(0, 0);
        }
      }
    });

    JPanel jPanel = new JPanel(new BorderLayout());
    final JScrollPane textScrollPane = ScrollPaneFactory.createScrollPane(textArea);
    // text on title border has some problems if text font size is bigger than expected.
    final JLabel commentLabel = new JLabel(VcsBundle.message("border.selected.revision.commit.message"));
    jPanel.add(commentLabel, BorderLayout.NORTH);
    commentLabel.setBorder(IdeBorderFactory.createBorder(SideBorder.TOP | SideBorder.LEFT | SideBorder.BOTTOM));
    textScrollPane.setBorder(null);
    jPanel.add(textScrollPane, BorderLayout.CENTER);
    jPanel.setPreferredSize(new JBDimension(300, 100));
    return jPanel;
  }

  private static class TreeNodeAdapter extends DefaultMutableTreeNode {
    private final TreeItem<VcsFileRevision> myRevision;

    TreeNodeAdapter(TreeNodeAdapter parent, TreeItem<VcsFileRevision> revision, List<TreeItem<VcsFileRevision>> children) {
      if (parent != null) {
        parent.add(this);
      }
      myRevision = revision;
      for (TreeItem<VcsFileRevision> treeItem : children) {
        new TreeNodeAdapter(this, treeItem, treeItem.getChildren());
      }
    }

    public VcsFileRevision getRevision() {
      return myRevision.getData();
    }
  }

  //////////////////////////////////////////////////
  // Implementation for directories

  private static boolean isVisibleForDirectory(@Nullable Project project) {
    return project != null;
  }

  private static boolean isEnabledForDirectory(@Nullable Project project, @NotNull VirtualFile file) {
    if (project == null) return false;
    final AbstractVcs vcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(file);
    DiffProvider diffProvider = vcs != null ? vcs.getDiffProvider() : null;
    return diffProvider != null && diffProvider.canCompareWithWorkingDir();
  }
}
