/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.github.pullrequests.details;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesTreeBrowser;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowser;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.ScrollingUtil;
import com.intellij.ui.table.TableView;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.api.GithubFullPath;
import org.jetbrains.plugins.github.api.data.GithubCommit;
import org.jetbrains.plugins.github.api.data.GithubCommitComment;
import org.jetbrains.plugins.github.api.data.GithubIssueComment;
import org.jetbrains.plugins.github.pullrequests.GithubToolWindow;
import org.jetbrains.plugins.github.pullrequests.details.GithubPullRequestDetailsLoader.GithubCommitDetails;
import org.jetbrains.plugins.github.util.GithubUrlUtil;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GithubPullRequestDetailsPanel extends JPanel implements Disposable {
  @NotNull private final GithubToolWindow myToolWindow;
  @NotNull private final Project myProject;
  @NotNull private final GithubFullPath myRepository;
  private final long myNumber;

  @NotNull private final GithubPullRequestDetailsLoader myDetailsLoader;

  @NotNull private final AsyncProcessIcon myProgressIcon = new AsyncProcessIcon("GithubPullRequestDetailsPanel");

  @NotNull private final MyChangesBrowser myChangesBrowser;
  @NotNull private final MyDiscussionsPanel myDiscussionsPanel;
  @NotNull private final TableView<GithubCommitDetails> myTable;
  @NotNull private final GithubCommitTableModel myTableModel;

  public GithubPullRequestDetailsPanel(@NotNull GithubToolWindow toolWindow, long number) {
    super(new BorderLayout());

    myToolWindow = toolWindow;
    myProject = myToolWindow.getProject();
    myRepository = myToolWindow.getFullPath();
    myNumber = number;

    myChangesBrowser = new MyChangesBrowser(myProject);
    myChangesBrowser.getDiffAction().registerCustomShortcutSet(this, null);

    myDiscussionsPanel = new MyDiscussionsPanel();
    myDetailsLoader = new MyGithubPullRequestDetailsLoader(myToolWindow, myNumber);

    myTableModel = new GithubCommitTableModel();
    myTable = new TableView<>(myTableModel);
    myTable.setCellSelectionEnabled(false);
    myTable.setRowSelectionAllowed(true);
    myTable.setShowHorizontalLines(false);
    myTable.setShowVerticalLines(false);
    ScrollingUtil.installActions(myTable, false);

    myTable.setDefaultRenderer(String.class, new ColoredTableCellRenderer() {
      @Override
      protected void customizeCellRenderer(JTable table, @Nullable Object value, boolean selected, boolean hasFocus, int row, int column) {
        if (value != null) {
          append(value.toString());
        }
      }
    });
    myTable.getSelectionModel().addListSelectionListener((e) -> {
      myChangesBrowser.update();
    });


    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, createToolbarActions(), true);
    toolbar.setTargetComponent(this);

    JScrollPane tableScrollPane = ScrollPaneFactory.createScrollPane(myTable,
                                                                     ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                                                     ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    JPanel topPanel = JBUI.Panels.simplePanel(toolbar.getComponent()).addToRight(myProgressIcon);
    JPanel tablePanel = JBUI.Panels.simplePanel(tableScrollPane).addToTop(topPanel);

    JScrollPane discussionsScrollPane = ScrollPaneFactory.createScrollPane(myDiscussionsPanel);
    Splitter discussionSplitter = new JBSplitter(false, "github.pullrequest.details.discussion.splitter", 0.5f);
    discussionSplitter.setFirstComponent(myChangesBrowser);
    discussionSplitter.setSecondComponent(discussionsScrollPane);

    Splitter tableSplitter = new JBSplitter(false, "github.pullrequest.details.table.splitter", 0.7f);
    tableSplitter.setFirstComponent(tablePanel);
    tableSplitter.setSecondComponent(discussionSplitter);

    add(tableSplitter, BorderLayout.CENTER);

    init();
  }

  public long getNumber() {
    return myNumber;
  }

  @Nullable
  public JComponent getPreferredFocusedComponent() {
    return myTable;
  }

  private void init() {
    myProgressIcon.resume();
    myDetailsLoader.load();
  }

  @Override
  public void dispose() {
    Disposer.dispose(myDetailsLoader);
  }

  @NotNull
  private ActionGroup createToolbarActions() {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new OpenInBrowserAction());
    return group;
  }


  private class MyChangesBrowser extends ChangesBrowser {
    public MyChangesBrowser(@NotNull Project project) {
      super(project, null, Collections.emptyList(), null, false, false, null, MyUseCase.COMMITTED_CHANGES, null);
    }

    public void update() {
      List<Change> allChanges = new ArrayList<Change>();

      for (int row : myTable.getSelectedRows()) {
        GithubCommitDetails commitDetails = myTableModel.getRowValue(row);
        List<Change> commitChanges = commitDetails.getChanges();
        // FIXME: show notification: null -> some error happened, "some changes are not shown -> <fetch>", etc
        if (commitChanges != null) allChanges.addAll(commitChanges);
      }

      allChanges = CommittedChangesTreeBrowser.zipChanges(allChanges);
      myChangesBrowser.setChangesToDisplay(allChanges);
    }
  }

  private class MyDiscussionsPanel extends JPanel {
    public MyDiscussionsPanel() {
      setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    }

    public void update() {
      removeAll();

      List<GithubIssueComment> issueComments = myDetailsLoader.getIssueComments();
      if (issueComments != null) {
        for (GithubIssueComment comment : issueComments) {
          add(new MyIssueCommentPanel(comment));
        }
      }

      List<GithubCommitComment> commitComments = myDetailsLoader.getCommitComments();
      if (commitComments != null) {
        for (GithubCommitComment comment : commitComments) {
          add(new MyCommitCommentPanel(comment));
        }
      }
    }
  }

  private static class MyCommitCommentPanel extends JPanel {
    public MyCommitCommentPanel(@NotNull GithubCommitComment comment) {
      setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
      add(new JLabel("User: " + comment.getUser().getLogin()));
      add(new JLabel(comment.getBodyHtml()));
    }
  }

  private static class MyIssueCommentPanel extends JPanel {
    public MyIssueCommentPanel(@NotNull GithubIssueComment comment) {
      setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
      add(new JLabel("User: " + comment.getUser().getLogin()));
      add(new JLabel(comment.getBodyHtml()));
    }
  }

  private class MyGithubPullRequestDetailsLoader extends GithubPullRequestDetailsLoader {
    public MyGithubPullRequestDetailsLoader(GithubToolWindow toolWindow, long number) {
      super(toolWindow, number);
    }

    @Override
    protected void onRequestAndCommitsLoaded() {
      List<GithubCommitDetails> commits = myDetailsLoader.getCommits();
      myTableModel.setItems(ContainerUtil.notNullize(commits));
      myTable.selectAll();
    }

    @Override
    protected void onCommitCommentsLoaded() {
      myDiscussionsPanel.update();
    }

    @Override
    protected void onIssueCommentsLoaded() {
      myDiscussionsPanel.update();
    }

    @Override
    protected void onCommitsChangesLoaded(boolean loadingComplete) {
      if (loadingComplete) myProgressIcon.suspend();
      myChangesBrowser.update();
    }
  }


  private class OpenInBrowserAction extends DumbAwareAction {
    public OpenInBrowserAction() {
      ActionUtil.copyFrom(this, "Github.Open.In.Browser");
    }

    @Override
    public void update(AnActionEvent e) {
      int[] rows = myTable.getSelectedRows();
      e.getPresentation().setEnabled(rows.length > 0);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      for (int row : myTable.getSelectedRows()) {
        GithubCommitDetails commitDetails = myTableModel.getRowValue(row);
        String githubUrl = GithubUrlUtil.getGithubHost() + '/' + myRepository.getUser() + '/'
                           + myRepository.getRepository() + "/commit/" + commitDetails.getCommit().getSha();
        BrowserUtil.browse(githubUrl);
      }
    }
  }

  private static class PullRequestData {
    @NotNull private final List<GithubCommit> commits;
    @NotNull private final List<GithubCommitComment> comments;

    public PullRequestData(@NotNull List<GithubCommit> commits,
                           @NotNull List<GithubCommitComment> comments) {
      this.commits = commits;
      this.comments = comments;
    }
  }
}
