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
package git4idea.push;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.util.Consumer;
import com.intellij.util.ui.UIUtil;
import git4idea.*;
import git4idea.branch.GitBranchUtil;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Kirill Likhodedov
 */
public class GitPushDialog extends DialogWrapper {

  private static final Logger LOG = Logger.getInstance(GitPushDialog.class);
  private static final String DEFAULT_REMOTE = "origin";

  private Project myProject;
  private final GitRepositoryManager myRepositoryManager;
  private final GitPusher myPusher;
  private final GitPushLog myListPanel;
  private GitCommitsByRepoAndBranch myGitCommitsToPush;
  private Map<GitRepository, GitPushSpec> myPushSpecs;
  private final Collection<GitRepository> myRepositories;
  private final JBLoadingPanel myLoadingPanel;
  private final Object COMMITS_LOADING_LOCK = new Object();
  private final GitManualPushToBranch myRefspecPanel;
  private final AtomicReference<String> myDestBranchInfoOnRefresh = new AtomicReference<String>();

  private final boolean myPushPossible;

  public GitPushDialog(@NotNull Project project) {
    super(project);
    myProject = project;
    myPusher = new GitPusher(myProject, ServiceManager.getService(project, GitPlatformFacade.class), new EmptyProgressIndicator());
    myRepositoryManager = GitUtil.getRepositoryManager(myProject);

    myRepositories = getRepositoriesWithRemotes();

    myLoadingPanel = new JBLoadingPanel(new BorderLayout(), this.getDisposable());

    myListPanel = new GitPushLog(myProject, myRepositories, new RepositoryCheckboxListener());
    myRefspecPanel = new GitManualPushToBranch(myRepositories, new RefreshButtonListener());

    if (GitManualPushToBranch.getRemotesWithCommonNames(myRepositories).isEmpty()) {
      myRefspecPanel.setVisible(false);
      setErrorText("Can't push, because no remotes are defined");
      setOKActionEnabled(false);
      myPushPossible = false;
    } else {
      myPushPossible = true;
    }

    init();
    setOKButtonText("Push");
    setOKButtonMnemonic('P');
    setTitle("Git Push");
  }

  @NotNull
  private List<GitRepository> getRepositoriesWithRemotes() {
    List<GitRepository> repositories = new ArrayList<GitRepository>();
    for (GitRepository repository : myRepositoryManager.getRepositories()) {
      if (!repository.getRemotes().isEmpty()) {
        repositories.add(repository);
      }
    }
    return repositories;
  }

  @Override
  protected JComponent createCenterPanel() {
    JPanel optionsPanel = new JPanel(new BorderLayout());
    optionsPanel.add(myRefspecPanel);

    JComponent rootPanel = new JPanel(new BorderLayout(0, 15));
    rootPanel.add(createCommitListPanel(), BorderLayout.CENTER);
    rootPanel.add(optionsPanel, BorderLayout.SOUTH);
    return rootPanel;
  }

  @Override
  protected String getHelpId() {
    return "reference.VersionControl.Git.PushDialog";
  }

  private JComponent createCommitListPanel() {
    myLoadingPanel.add(myListPanel, BorderLayout.CENTER);
    if (myPushPossible) {
      loadCommitsInBackground();
    } else {
      myLoadingPanel.startLoading();
      myLoadingPanel.stopLoading();
    }

    JPanel commitListPanel = new JPanel(new BorderLayout());
    commitListPanel.add(myLoadingPanel, BorderLayout.CENTER);
    return commitListPanel;
  }

  private void loadCommitsInBackground() {
    myLoadingPanel.startLoading();

    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      public void run() {
        final AtomicReference<String> error = new AtomicReference<String>();
        synchronized (COMMITS_LOADING_LOCK) {
          error.set(collectInfoToPush());
        }

        final Pair<String, String> remoteAndBranch = getRemoteAndTrackedBranchForCurrentBranch();
        UIUtil.invokeLaterIfNeeded(new Runnable() {
          @Override
          public void run() {
            if (error.get() != null) {
              myListPanel.displayError(error.get());
            } else {
              myListPanel.setCommits(myGitCommitsToPush);
            }
            if (!myRefspecPanel.turnedOn()) {
              myRefspecPanel.selectRemote(remoteAndBranch.getFirst());
              myRefspecPanel.setBranchToPushIfNotSet(remoteAndBranch.getSecond());
            }
            myLoadingPanel.stopLoading();
          }
        });
      }
    });
  }

  @NotNull
  private Pair<String, String> getRemoteAndTrackedBranchForCurrentBranch() {
    if (myGitCommitsToPush != null) {
      Collection<GitRepository> repositories = myGitCommitsToPush.getRepositories();
      if (!repositories.isEmpty()) {
        GitRepository repository = repositories.iterator().next();
        GitBranch currentBranch = repository.getCurrentBranch();
        assert currentBranch != null;
        if (myGitCommitsToPush.get(repository).get(currentBranch).getDestBranch() == GitPusher.NO_TARGET_BRANCH) { // push to branch with the same name
          return Pair.create(DEFAULT_REMOTE, currentBranch.getName());
        }
        String remoteName;
        try {
          remoteName = GitBranchUtil.getTrackedRemoteName(myProject, repository.getRoot(), currentBranch.getName());
          if (remoteName == null) {
            remoteName = DEFAULT_REMOTE;
          }
        }
        catch (VcsException e) {
          LOG.info("Couldn't retrieve tracked branch for current branch " + currentBranch, e);
          remoteName = DEFAULT_REMOTE;
        }
        String targetBranch = myGitCommitsToPush.get(repository).get(currentBranch).getDestBranch().getNameForRemoteOperations();
        return Pair.create(remoteName, targetBranch);
      }
    }
    return Pair.create(DEFAULT_REMOTE, "");
  }

  @Nullable
  private String collectInfoToPush() {
    try {
      LOG.info("collectInfoToPush...");
      myPushSpecs = pushSpecsForCurrentOrEnteredBranches();
      myGitCommitsToPush = myPusher.collectCommitsToPush(myPushSpecs);
      LOG.info(String.format("collectInfoToPush | Collected commits to push. Push spec: %s, commits: %s",
                             myPushSpecs, logMessageForCommits(myGitCommitsToPush)));
      return null;
    }
    catch (VcsException e) {
      myGitCommitsToPush = GitCommitsByRepoAndBranch.empty();
      LOG.error("collectInfoToPush | Couldn't collect commits to push. Push spec: " + myPushSpecs, e);
      return e.getMessage();
    }
  }

  private static String logMessageForCommits(GitCommitsByRepoAndBranch commitsToPush) {
    StringBuilder logMessage = new StringBuilder();
    for (GitCommit commit : commitsToPush.getAllCommits()) {
      logMessage.append(GitUtil.getShortHash(commit.getHash().toString()));
    }
    return logMessage.toString();
  }

  private Map<GitRepository, GitPushSpec> pushSpecsForCurrentOrEnteredBranches() throws VcsException {
    Map<GitRepository, GitPushSpec> defaultSpecs = new HashMap<GitRepository, GitPushSpec>();
    for (GitRepository repository : myRepositories) {
      GitLocalBranch currentBranch = repository.getCurrentBranch();
      if (currentBranch == null) {
        continue;
      }
      String remoteName = GitBranchUtil.getTrackedRemoteName(repository.getProject(), repository.getRoot(), currentBranch.getName());
      String trackedBranchName = GitBranchUtil.getTrackedBranchName(repository.getProject(), repository.getRoot(), currentBranch.getName());
      GitRemote remote = GitUtil.findRemoteByName(repository, remoteName);
      GitRemoteBranch targetBranch;
      if (remote != null && trackedBranchName != null) {
        targetBranch = GitBranchUtil.findRemoteBranchByName(trackedBranchName, remote.getName(),
                                                            repository.getBranches().getRemoteBranches());
      }
      else {
        Pair<GitRemote, GitRemoteBranch> remoteAndBranch = GitUtil.findMatchingRemoteBranch(repository, currentBranch);
        if (remoteAndBranch == null) {
          targetBranch = GitPusher.NO_TARGET_BRANCH;
        } else {
          targetBranch = remoteAndBranch.getSecond();
        }
      }

      if (myRefspecPanel.turnedOn()) {
        String manualBranchName = myRefspecPanel.getBranchToPush();
        remote = myRefspecPanel.getSelectedRemote();
        GitRemoteBranch manualBranch = GitBranchUtil.findRemoteBranchByName(manualBranchName, remote.getName(),
                                                                            repository.getBranches().getRemoteBranches());
        if (manualBranch == null) {
          manualBranch = new GitStandardRemoteBranch(remote, manualBranchName, GitBranch.DUMMY_HASH);
        }
        targetBranch = manualBranch;
      }

      GitPushSpec pushSpec = new GitPushSpec(currentBranch, targetBranch == null ? GitPusher.NO_TARGET_BRANCH : targetBranch);
      defaultSpecs.put(repository, pushSpec);
    }
    return defaultSpecs;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myListPanel.getPreferredFocusComponent();
  }

  @Override
  protected String getDimensionServiceKey() {
    return GitPushDialog.class.getName();
  }

  @NotNull
  public GitPushInfo getPushInfo() {
    // waiting for commit list loading, because this information is needed to correctly handle rejected push situation and correctly
    // notify about pushed commits
    // TODO optimize: don't refresh: information about pushed commits can be achieved from the successful push output
    LOG.info("getPushInfo start");
    synchronized (COMMITS_LOADING_LOCK) {
      GitCommitsByRepoAndBranch selectedCommits;
      if (myGitCommitsToPush == null) {
        LOG.info("getPushInfo | myGitCommitsToPush == null. collecting...");
        collectInfoToPush();
        selectedCommits = myGitCommitsToPush;
      }
      else {
        if (refreshNeeded()) {
          LOG.info("getPushInfo | refresh is needed, collecting...");
          collectInfoToPush();
        }
        Collection<GitRepository> selectedRepositories = myListPanel.getSelectedRepositories();
        selectedCommits = myGitCommitsToPush.retainAll(selectedRepositories);
      }
      LOG.info("getPushInfo | selectedCommits: " + logMessageForCommits(selectedCommits));
      return new GitPushInfo(selectedCommits, myPushSpecs);
    }
  }

  private boolean refreshNeeded() {
    String currentDestBranchValue = myRefspecPanel.turnedOn() ? myRefspecPanel.getBranchToPush(): null;
    String savedValue = myDestBranchInfoOnRefresh.get();
    if (savedValue == null) {
      return currentDestBranchValue != null;
    }
    return !savedValue.equals(currentDestBranchValue);
  }

  private class RepositoryCheckboxListener implements Consumer<Boolean> {
    @Override public void consume(Boolean checked) {
      if (checked) {
        setOKActionEnabled(true);
      } else {
        Collection<GitRepository> repositories = myListPanel.getSelectedRepositories();
        if (repositories.isEmpty()) {
          setOKActionEnabled(false);
        } else {
          setOKActionEnabled(true);
        }
      }
    }
  }

  private class RefreshButtonListener implements Runnable {
    @Override
    public void run() {
      myDestBranchInfoOnRefresh.set(myRefspecPanel.turnedOn() ? myRefspecPanel.getBranchToPush(): null);
      loadCommitsInBackground();
    }
  }

}
