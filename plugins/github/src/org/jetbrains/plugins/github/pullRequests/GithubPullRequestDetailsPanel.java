/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.github.pullRequests;

import com.google.common.collect.Lists;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.committed.RepositoryChangesBrowser;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowser;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.util.ui.UIUtil;
import git4idea.GitUtil;
import git4idea.changes.GitChangeUtils;
import git4idea.commands.Git;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.github.api.GithubApiUtil;
import org.jetbrains.plugins.github.api.data.GithubCommit;
import org.jetbrains.plugins.github.api.data.GithubCommitSha;
import org.jetbrains.plugins.github.api.data.GithubPullRequest;
import org.jetbrains.plugins.github.api.data.GithubRepo;
import org.jetbrains.plugins.github.util.GithubAuthDataHolder;
import org.jetbrains.plugins.github.util.GithubUtil;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GithubPullRequestDetailsPanel extends JPanel {
  private static final Logger LOG = Logger.getInstance(GithubPullRequestDetailsPanel.class);
  private final Project myProject;
  private final GithubAuthDataHolder myAuthDataHolder;

  public GithubPullRequestDetailsPanel(@NotNull final Project project, @NotNull final GithubPullRequest pullRequest,
                                       @NotNull final GithubAuthDataHolder authHolder) {
    super(new BorderLayout());
    myProject = project;
    myAuthDataHolder = authHolder;

    final Splitter pullRequestComponent = new Splitter(false, 0.5f);
    final JBLoadingPanel changesPanel = new JBLoadingPanel(new BorderLayout(), myProject);
    final ChangesBrowser changesBrowser = new RepositoryChangesBrowser(myProject, Collections.emptyList());
    changesPanel.add(changesBrowser);

    GithubUtil.computeValueInModal(myProject, "Fetching repository...", indicator -> {
      final GitRepository repository = GithubUtil.getGitRepository(myProject, myProject.getBaseDir());
      if (repository == null) return null;
      Git git = Git.getInstance();
      GitRemote remote = GitUtil.findRemoteByName(repository, "origin");
      final String branchName = "pull/" + pullRequest.getNumber() + "/head:" +
                                pullRequest.getHead().getUser().getLogin() + "-pullRequest" + pullRequest.getNumber();
      if (remote == null) return null;
      return git.fetch(repository, remote, Collections.emptyList(), branchName);
    });

    final JBList<GithubCommit> commitsComponent = createCommitsComponent(changesPanel, changesBrowser);

    new Task.Backgroundable(myProject, "Loading Commits List...") {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          List<GithubCommit> commits = GithubUtil.runTask(myProject, myAuthDataHolder, indicator, connection -> {
            final GithubRepo repo = pullRequest.getBase().getRepo();
            final String name = repo != null ? repo.getName() : "";  //TODO: wrong to get empty name
            return GithubApiUtil
              .getPullRequestCommits(connection, pullRequest.getBase().getUser().getLogin(), name, pullRequest.getNumber());
          });
          if (commits == null) {
            commits = Lists.newArrayList();
          }
          commitsComponent.setListData(commits.toArray(new GithubCommit[commits.size()]));
        }
        catch (IOException e) {
          LOG.error("Failed to get commits: " + e.getMessage());
        }
      }
    }.queue();

    final JPanel requestDetails = new JPanel(new VerticalFlowLayout());
    final JLabel details = new JLabel(wrapInHtml(pullRequest));
    requestDetails.add(commitsComponent);
    requestDetails.add(new JSeparator());
    requestDetails.add(details);

    //TODO: show comments here in requestDetails panel

    pullRequestComponent.setFirstComponent(requestDetails);
    pullRequestComponent.setSecondComponent(changesPanel);

    add(pullRequestComponent, BorderLayout.CENTER);
  }

  @NotNull
  private JBList<GithubCommit> createCommitsComponent(@NotNull final JBLoadingPanel changesPanel,
                                                      @NotNull final ChangesBrowser changesBrowser) {
    final JBList<GithubCommit> commitsComponent = new JBList<>();
    commitsComponent.setCellRenderer(new ColoredListCellRenderer<GithubCommit>() {
      @Override
      protected void customizeCellRenderer(@NotNull JList<? extends GithubCommit> list, GithubCommit value,
                                           int index, boolean selected, boolean hasFocus) {
        final GithubCommit.GitCommit commit = value.getCommit();
        append(commit.getMessage());
        if (!selected && index % 2 == 0) {
          setBackground(UIUtil.getDecoratedRowColor());
        }
      }
    });
    commitsComponent.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        if (!e.getValueIsAdjusting()) {
          changesPanel.startLoading();
          updateChangesView(changesBrowser, commitsComponent.getSelectedValuesList());
          changesPanel.stopLoading();
        }
      }
    });
    return commitsComponent;
  }

  @NotNull
  private static String wrapInHtml(@NotNull final GithubPullRequest pullRequest) {
    return "<html>" + pullRequest.getBodyHtml() + "</html>";
  }

  private void updateChangesView(@NotNull final ChangesBrowser changesBrowser, @NotNull final List<GithubCommit> githubCommits) {
    final ArrayList<Change> changes = new ArrayList<>();
    try {
      for (GithubCommit commit : githubCommits) {
        final List<GithubCommitSha> parents = commit.getParents();
        for (GithubCommitSha parent : parents) {
          changes.addAll(GitChangeUtils.getDiff(myProject, myProject.getBaseDir(), parent.getSha(), commit.getSha(), null));
        }
      }
    }
    catch (VcsException e) {
      LOG.error("Failed to get diff " + e.getMessage());
    }
    changesBrowser.setChangesToDisplay(Lists.newArrayList(changes));
  }
}
