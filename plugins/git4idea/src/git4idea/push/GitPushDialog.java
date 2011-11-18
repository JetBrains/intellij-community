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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.util.Consumer;
import com.intellij.util.ui.UIUtil;
import git4idea.GitBranch;
import git4idea.GitUtil;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Kirill Likhodedov
 */
public class GitPushDialog extends DialogWrapper {

  private static final Logger LOG = Logger.getInstance(GitPushDialog.class);

  private JComponent myRootPanel;
  private Project myProject;
  private final GitPusher myPusher;
  private final GitPushLog myListPanel;
  private GitCommitsByRepoAndBranch myGitCommitsToPush;
  private Map<GitRepository, GitPushSpec> myPushSpecs;
  private final Collection<GitRepository> myRepositories;
  private final JBLoadingPanel myLoadingPanel;
  private final JCheckBox myPushAllCheckbox;
  private final Object COMMITS_LOADING_LOCK = new Object();
  private final GitManualPushToBranch myRefspecPanel;
  private final AtomicReference<String> myDestBranchInfoOnRefresh = new AtomicReference<String>();

  public GitPushDialog(@NotNull Project project) {
    super(project);
    myProject = project;
    myPusher = new GitPusher(myProject, new EmptyProgressIndicator());

    myRepositories = GitRepositoryManager.getInstance(myProject).getRepositories();

    myLoadingPanel = new JBLoadingPanel(new BorderLayout(), this.getDisposable());
    myPushAllCheckbox = new JCheckBox("Push all branches", false);
    myPushAllCheckbox.setMnemonic('p');
    myPushAllCheckbox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        loadCommitsInBackground();
      }
    });
    /* hidden: it may confuse users, the target is not clear, hidden until really needed,
       not removed completely because it is default behavior for 'git push' in command line. */
    myPushAllCheckbox.setVisible(false);

    myListPanel = new GitPushLog(myProject, myRepositories, new RepositoryCheckboxListener());
    myRefspecPanel = new GitManualPushToBranch(myRepositories, new RefreshButtonListener());
    
    init();
    setOKButtonText("Push");
    setTitle("Git Push");
  }

  @Override
  protected JComponent createCenterPanel() {
    JPanel optionsPanel = new JPanel(new BorderLayout());
    optionsPanel.add(myPushAllCheckbox, BorderLayout.NORTH);
    optionsPanel.add(myRefspecPanel);

    myRootPanel = new JPanel(new BorderLayout(0, 15));
    myRootPanel.add(createCommitListPanel(), BorderLayout.CENTER);
    myRootPanel.add(optionsPanel, BorderLayout.SOUTH);
    return myRootPanel;
  }


  private JComponent createCommitListPanel() {
    myLoadingPanel.add(myListPanel, BorderLayout.CENTER);
    loadCommitsInBackground();

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
        
        UIUtil.invokeLaterIfNeeded(new Runnable() {
          @Override
          public void run() {
            if (error.get() != null) {
              myListPanel.displayError(error.get());
            } else {
              myListPanel.setCommits(myGitCommitsToPush);
            }
            myRefspecPanel.setBranchToPushIfNotSet(getTrackedCurrentBranchName());
            myLoadingPanel.stopLoading();
          }
        });
      }
    });
  }

  @NotNull
  private String getTrackedCurrentBranchName() {
    if (myGitCommitsToPush != null) {
      Collection<GitRepository> repositories = myGitCommitsToPush.getRepositories();
      if (!repositories.isEmpty()) {
        GitRepository repository = repositories.iterator().next();
        GitBranch currentBranch = repository.getCurrentBranch();
        assert currentBranch != null;
        if (myGitCommitsToPush.get(repository).get(currentBranch).getDestBranch() == GitPusher.NO_TARGET_BRANCH) { // push to branch with the same name
          return currentBranch.getName();
        }
        return getNameWithoutRemote(myGitCommitsToPush.get(repository).get(currentBranch).getDestBranch());
      }
    }
    return "";
  }

  @NotNull
  private String getNameWithoutRemote(@NotNull GitBranch remoteBranch) {
    String remoteName = myRefspecPanel.getSelectedRemote().getName() + "/";
    String branchName = remoteBranch.getName();
    if (branchName.startsWith(remoteName)) {
      return branchName.substring(remoteName.length());
    }
    else {
      // we are taking the current branch of the first repository
      // it is possible (though unlikely), that this branch has other remote than the common remote selected in the refspec panel
      // then we return the full branch name.
      // the push won't work absolutely correct, if the remote doesn't have this branch, but it is not our problem in the case of 
      // several repositories with different remotes sets and different branches.
      return remoteBranch.getFullName();
    }
  }

  @Nullable
  private String collectInfoToPush() {
    try {
      boolean pushAll = myPushAllCheckbox.isSelected();
      myPushSpecs = pushAll ? pushSpecsForPushAll() : pushSpecsForCurrentOrEnteredBranches();
      myGitCommitsToPush = myPusher.collectCommitsToPush(myPushSpecs);
      return null;
    }
    catch (VcsException e) {
      myGitCommitsToPush = GitCommitsByRepoAndBranch.empty();
      LOG.error("Couldn't collect commits to push. Push spec: " + myPushSpecs, e);
      return e.getMessage();
    }
  }
  
  private Map<GitRepository, GitPushSpec> pushSpecsForCurrentOrEnteredBranches() throws VcsException {
    Map<GitRepository, GitPushSpec> defaultSpecs = new HashMap<GitRepository, GitPushSpec>();
    for (GitRepository repository : myRepositories) {
      GitBranch currentBranch = repository.getCurrentBranch();
      if (currentBranch == null) {
        continue;
      }
      String remoteName = currentBranch.getTrackedRemoteName(repository.getProject(), repository.getRoot());
      String trackedBranchName = currentBranch.getTrackedBranchName(repository.getProject(), repository.getRoot());
      GitRemote remote = GitUtil.findRemoteByName(repository, remoteName);
      GitBranch tracked = findRemoteBranchByName(repository, remote, trackedBranchName);
      if (remote == null || tracked == null) {
        Pair<GitRemote,GitBranch> remoteAndBranch = GitUtil.findMatchingRemoteBranch(repository, currentBranch);
        if (remoteAndBranch == null) {
          remote = myRefspecPanel.getSelectedRemote();
          tracked = GitPusher.NO_TARGET_BRANCH;
        } else {
          remote = remoteAndBranch.getFirst();
          tracked = remoteAndBranch.getSecond();
        }
      }

      if (myRefspecPanel.turnedOn()) {
        String manualBranchName = myRefspecPanel.getBranchToPush();
        GitBranch manualBranch = findRemoteBranchByName(repository, remote, manualBranchName);
        if (manualBranch == null) {
          if (!manualBranchName.startsWith("refs/remotes/")) {
            manualBranchName = myRefspecPanel.getSelectedRemote().getName() + "/" + manualBranchName;
          }
          manualBranch = new GitBranch(manualBranchName, false, true);
        }
        tracked = manualBranch;
      }
      
      GitPushSpec pushSpec = new GitPushSpec(remote, currentBranch, tracked);
      defaultSpecs.put(repository, pushSpec);
    }
    return defaultSpecs;
  }

  @Nullable
  private static GitBranch findRemoteBranchByName(@NotNull GitRepository repository, @Nullable GitRemote remote, @Nullable String name) {
    if (name == null || remote == null) {
      return null;
    }
    final String BRANCH_PREFIX = "refs/heads/";
    if (name.startsWith(BRANCH_PREFIX)) {
      name = name.substring(BRANCH_PREFIX.length());
    }

    for (GitBranch branch : repository.getBranches().getRemoteBranches()) {
      if (branch.getName().equals(remote.getName() + "/" + name)) {
        return branch;
      }
    }
    return null;
  }

  private Map<GitRepository, GitPushSpec> pushSpecsForPushAll() {
    Map<GitRepository, GitPushSpec> specs = new HashMap<GitRepository, GitPushSpec>();
    for (GitRepository repository : myRepositories) {
      specs.put(repository, GitPushSpec.pushAllSpec());
    }
    return specs;
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
    synchronized (COMMITS_LOADING_LOCK) {
      GitCommitsByRepoAndBranch selectedCommits;
      if (myGitCommitsToPush == null) {
        collectInfoToPush();
        selectedCommits = myGitCommitsToPush;
      }
      else {
        if (refreshNeeded()) {
          collectInfoToPush();
        }
        Collection<GitRepository> selectedRepositories = myListPanel.getSelectedRepositories();
        selectedCommits = myGitCommitsToPush.retainAll(selectedRepositories);
      }
      return new GitPushInfo(selectedCommits, myPushSpecs);
    }
  }

  private boolean refreshNeeded() {
    String currentDestBranchValue = myRefspecPanel.turnedOn() ? myRefspecPanel.getBranchToPush(): null;
    String savedValue = myDestBranchInfoOnRefresh.get();
    if (savedValue == null) {
      return currentDestBranchValue != null;
    }
    return !savedValue.equals(myDestBranchInfoOnRefresh.get());
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
