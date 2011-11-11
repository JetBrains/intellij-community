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

  public GitPushDialog(@NotNull Project project) {
    super(project);
    myProject = project;
    myPusher = new GitPusher(myProject, new EmptyProgressIndicator());

    myRepositories = GitRepositoryManager.getInstance(myProject).getRepositories();

    myLoadingPanel = new JBLoadingPanel(new BorderLayout(), this.getDisposable());
    myListPanel = new GitPushLog(myProject, myRepositories, new Consumer<Boolean>() {
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
    });

    init();
    setOKButtonText("Push");
    setTitle("Git Push");
  }

  @Override
  protected JComponent createCenterPanel() {
    myRootPanel = new JPanel(new BorderLayout());
    myRootPanel.add(createCommitListPanel(), BorderLayout.CENTER);
    myRootPanel.add(createOptionsPanel(), BorderLayout.SOUTH);
    return myRootPanel;
  }
  
  private JComponent createCommitListPanel() {
    JPanel commitListPanel = new JPanel(new BorderLayout());

    myLoadingPanel.add(myListPanel, BorderLayout.CENTER);
    loadCommitsInBackground(false);

    commitListPanel.add(myLoadingPanel, BorderLayout.CENTER);
    return commitListPanel;
  }

  private void loadCommitsInBackground(final boolean pushAll) {
    myLoadingPanel.startLoading();
    
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      public void run() {
        final AtomicReference<String> error = new AtomicReference<String>();
        try {
          myPushSpecs = pushAll ? pushSpecsForPushAll() : pushSpecsForCurrentBranches();
          myGitCommitsToPush = myPusher.collectCommitsToPush(myPushSpecs);
        }
        catch (VcsException e) {
          myGitCommitsToPush = GitCommitsByRepoAndBranch.empty();
          error.set(e.getMessage());
          LOG.error("Couldn't collect commits to push. Push spec: " + myPushSpecs, e);
        }
        
        UIUtil.invokeLaterIfNeeded(new Runnable() {
          @Override
          public void run() {
            if (error.get() != null) {
              myListPanel.displayError(error.get());
            } else {
              myListPanel.setCommits(myGitCommitsToPush);
            }
            myLoadingPanel.stopLoading();
          }
        });
      }
    });
  }
  
  private Map<GitRepository, GitPushSpec> pushSpecsForCurrentBranches() throws VcsException {
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
          continue;
        }
        remote = remoteAndBranch.getFirst();
        tracked = remoteAndBranch.getSecond();
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

  private JComponent createOptionsPanel() {
    final JCheckBox pushAll = new JCheckBox("Push all branches");
    pushAll.setMnemonic('p');
    pushAll.addActionListener(new ActionListener() {
      @Override public void actionPerformed(ActionEvent e) {
        loadCommitsInBackground(pushAll.isSelected());
      }
    });
    
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(pushAll);
    return panel;
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
    Collection<GitRepository> selectedRepositories = myListPanel.getSelectedRepositories();
    GitCommitsByRepoAndBranch selectedCommits = myGitCommitsToPush.retainAll(selectedRepositories);
    return new GitPushInfo(selectedCommits, myPushSpecs);
  }
}
