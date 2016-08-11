/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
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
import com.intellij.util.ui.ListTableModel;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.util.Date;
import java.util.List;

public class CompareWithSelectedRevisionAction extends AbstractVcsAction {

  private static final ColumnInfo<TreeNodeAdapter,String> BRANCH_COLUMN = new ColumnInfo<TreeNodeAdapter, String>(VcsBundle.message("column.name.revisions.list.branch")){
    @Override
    public String valueOf(final TreeNodeAdapter object) {
      return object.getRevision().getBranchName();
    }
  };

  private static final ColumnInfo<TreeNodeAdapter,String> REVISION_COLUMN = new ColumnInfo<TreeNodeAdapter, String>(VcsBundle.message("column.name.revision.list.revision")){
    @Override
    public String valueOf(final TreeNodeAdapter object) {
      return object.getRevision().getRevisionNumber().asString();
    }
  };

  private static final ColumnInfo<TreeNodeAdapter,String> DATE_COLUMN = new ColumnInfo<TreeNodeAdapter, String>(VcsBundle.message("column.name.revisions.list.filter")){
    @Override
    public String valueOf(final TreeNodeAdapter object) {
      return DateFormatUtil.formatPrettyDateTime(object.getRevision().getRevisionDate());
    }
  };

  private static final ColumnInfo<TreeNodeAdapter,String> AUTHOR_COLUMN = new ColumnInfo<TreeNodeAdapter, String>(VcsBundle.message("column.name.revision.list.author")){
    @Override
    public String valueOf(final TreeNodeAdapter object) {
      return object.getRevision().getAuthor();
    }
  };

  private static final ColumnInfo<VcsFileRevision, String> REVISION_TABLE_COLUMN = new ColumnInfo<VcsFileRevision, String>(VcsBundle.message("column.name.revision.list.revision")) {
    @Override
    public String valueOf(final VcsFileRevision vcsFileRevision) {
      return vcsFileRevision.getRevisionNumber().asString();
    }
  };

  private static final ColumnInfo<VcsFileRevision, String> DATE_TABLE_COLUMN = new ColumnInfo<VcsFileRevision, String>(VcsBundle.message("column.name.revision.list.revision")) {
    @Override
    public String valueOf(final VcsFileRevision vcsFileRevision) {
      final Date date = vcsFileRevision.getRevisionDate();
      return date == null ? "" : DateFormatUtil.formatPrettyDateTime(date);
    }
  };

  private static final ColumnInfo<VcsFileRevision,String> AUTHOR_TABLE_COLUMN = new ColumnInfo<VcsFileRevision, String>(VcsBundle.message("column.name.revision.list.author")){
    @Override
    public String valueOf(final VcsFileRevision vcsFileRevision) {
      return vcsFileRevision.getAuthor();
    }
  };

  private static final ColumnInfo<VcsFileRevision,String> BRANCH_TABLE_COLUMN = new ColumnInfo<VcsFileRevision, String>(VcsBundle.message("column.name.revisions.list.branch")){
    @Override
    public String valueOf(final VcsFileRevision vcsFileRevision) {
      return vcsFileRevision.getBranchName();
    }
  };

  @Override
  public void update(VcsContext e, Presentation presentation) {
    AbstractShowDiffAction.updateDiffAction(presentation, e, VcsBackgroundableActions.COMPARE_WITH);
  }


  @Override
  protected boolean forceSyncUpdate(final AnActionEvent e) {
    return true;
  }

  @Override
  protected void actionPerformed(@NotNull VcsContext vcsContext) {
    final VirtualFile file = vcsContext.getSelectedFiles()[0];
    final Project project = vcsContext.getProject();
    final AbstractVcs vcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(file);
    final VcsHistoryProvider vcsHistoryProvider = vcs.getVcsHistoryProvider();

    new VcsHistoryProviderBackgroundableProxy(vcs, vcsHistoryProvider, vcs.getDiffProvider()).
      createSessionFor(vcs.getKeyInstanceMethod(), VcsUtil.getFilePath(file),
        new Consumer<VcsHistorySession>() {
          @Override
          public void consume(VcsHistorySession session) {
            if (session == null) return;
            final List<VcsFileRevision> revisions = session.getRevisionList();
            final HistoryAsTreeProvider treeHistoryProvider = session.getHistoryAsTreeProvider();
            if (treeHistoryProvider != null) {
              showTreePopup(treeHistoryProvider.createTreeOn(revisions), file, project, vcs.getDiffProvider());
            }
            else {
              showListPopup(revisions, project, new Consumer<VcsFileRevision>() {
                @Override
                public void consume(final VcsFileRevision revision) {
                  DiffActionExecutor.showDiff(vcs.getDiffProvider(), revision.getRevisionNumber(), file, project,
                                              VcsBackgroundableActions.COMPARE_WITH);
                }
              }, true);
            }
          }
        }, VcsBackgroundableActions.COMPARE_WITH, false, null);
  }

  private static void showTreePopup(final List<TreeItem<VcsFileRevision>> roots, final VirtualFile file, final Project project, final DiffProvider diffProvider) {
    final TreeTableView treeTable = new TreeTableView(new ListTreeTableModelOnColumns(new TreeNodeAdapter(null, null, roots),
                                                                                      new ColumnInfo[]{BRANCH_COLUMN, REVISION_COLUMN,
                                                                                      DATE_COLUMN, AUTHOR_COLUMN}));
    Runnable runnable = new Runnable() {
      @Override
      public void run() {
        int index = treeTable.getSelectionModel().getMinSelectionIndex();
        if (index == -1) {
          return;
        }
        VcsFileRevision revision = getRevisionAt(treeTable, index);
        if (revision != null) {
          DiffActionExecutor.showDiff(diffProvider, revision.getRevisionNumber(), file, project, VcsBackgroundableActions.COMPARE_WITH);
        }
      }
    };

    treeTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    new PopupChooserBuilder(treeTable).
      setTitle(VcsBundle.message("lookup.title.vcs.file.revisions")).
      setItemChoosenCallback(runnable).
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

  public static void showListPopup(final List<VcsFileRevision> revisions, final Project project, final Consumer<VcsFileRevision> selectedRevisionConsumer,
                                   final boolean showComments) {
    ColumnInfo[] columns = new ColumnInfo[] { REVISION_TABLE_COLUMN, DATE_TABLE_COLUMN, AUTHOR_TABLE_COLUMN };
    for(VcsFileRevision revision: revisions) {
      if (revision.getBranchName() != null) {
        columns = new ColumnInfo[] { REVISION_TABLE_COLUMN, BRANCH_TABLE_COLUMN, DATE_TABLE_COLUMN, AUTHOR_TABLE_COLUMN };
        break;
      }
    }
    final TableView<VcsFileRevision> table = new TableView<>(new ListTableModel<>(columns, revisions, 0));
    table.setShowHorizontalLines(false);
    table.setTableHeader(null);
    Runnable runnable = new Runnable() {
      @Override
      public void run() {
        VcsFileRevision revision = table.getSelectedObject();
        if (revision != null) {
          selectedRevisionConsumer.consume(revision);
        }
      }
    };

    if (table.getModel().getRowCount() == 0) {
      table.clearSelection();
    }

    new SpeedSearchBase<TableView>(table) {
      @Override
      protected int getSelectedIndex() {
        return table.getSelectedRow();
      }

      @Override
      protected int convertIndexToModel(int viewIndex) {
        return table.convertRowIndexToModel(viewIndex);
      }

      @Override
      protected Object[] getAllElements() {
        return revisions.toArray();
      }

      @Override
      protected String getElementText(Object element) {
        VcsFileRevision revision = (VcsFileRevision) element;
        return revision.getRevisionNumber().asString() + " " + revision.getBranchName() + " " + revision.getAuthor();
      }

      @Override
      protected void selectElement(Object element, String selectedText) {
        VcsFileRevision revision = (VcsFileRevision) element;
        TableUtil.selectRows(myComponent, new int[] {myComponent.convertRowIndexToView(revisions.indexOf(revision))});
        TableUtil.scrollSelectionToVisible(myComponent);
      }
    };

    table.setMinimumSize(new Dimension(300, 50));
    final PopupChooserBuilder builder = new PopupChooserBuilder(table);

    if (showComments) {
      builder.setSouthComponent(createCommentsPanel(table));
    }

    builder.setTitle(VcsBundle.message("lookup.title.vcs.file.revisions")).
        setItemChoosenCallback(runnable).
        setResizable(true).
        setDimensionServiceKey("Vcs.CompareWithSelectedRevision.Popup").setMinSize(new Dimension(300, 300));
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
    jPanel.setPreferredSize(new Dimension(300, 100));
    return jPanel;
  }

  private static class TreeNodeAdapter extends DefaultMutableTreeNode {
    private final TreeItem<VcsFileRevision> myRevision;

    public TreeNodeAdapter(TreeNodeAdapter parent, TreeItem<VcsFileRevision> revision, List<TreeItem<VcsFileRevision>> children) {
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
}
