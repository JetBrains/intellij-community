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
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.util.Consumer;
import git4idea.*;
import git4idea.branch.GitBranchPair;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * @author Kirill Likhodedov
 */
public class GitPushDialog extends DialogWrapper {

  private static final Logger LOG = GitLogger.PUSH_LOG;
  private static final String DEFAULT_REMOTE = "origin";

  @NotNull private final GitPushSpecs myInitialPushSpecs;
  @NotNull private final GitOutgoingCommitsCollector myOutgoingCommitsCollector;

  @NotNull private final GitPushLog myListPanel;
  @NotNull private final JBLoadingPanel myLoadingPanel;
  @NotNull private final GitManualPushToBranch myRefspecPanel;

  public GitPushDialog(@NotNull Project project, @NotNull GitPushSpecs pushSpecs) {
    super(project);
    myInitialPushSpecs = pushSpecs;
    GitRepositoryManager manager = GitUtil.getRepositoryManager(project);

    Collection<GitRepository> repositories = myInitialPushSpecs.getRepositories();

    myOutgoingCommitsCollector = GitOutgoingCommitsCollector.getInstance(project);

    myLoadingPanel = new JBLoadingPanel(new BorderLayout(), this.getDisposable());

    myListPanel = new GitPushLog(project, manager.getRepositories(), new RepositoryCheckboxListener());
    myRefspecPanel = new GitManualPushToBranch(repositories.size() > 1, new RefreshButtonListener());

    init();
    setOKButtonText("Push");
    setOKButtonMnemonic('P');
    setTitle("Git Push");
    update();
  }

  private void update() {
    Collection<GitRepository> repositories = myListPanel.getSelectedRepositories();
    Collection<GitRemote> commonRemotes = getRemotesWithCommonNames(repositories);
    myRefspecPanel.setRemotes(commonRemotes, getDefaultRemote(repositories));

    String commonTargetBranch = getCommonTargetBranch(repositories);

    setOKActionEnabled(false);
    if (repositories.isEmpty()) {
      setErrorText("No repositories were selected");
    }
    else if (commonRemotes.isEmpty()) {
      setErrorText(repositories.size() > 1 ? "No common remotes are defined in the selected repositories" : "No remotes are defined");
    }
    else if (commonTargetBranch == null) {
      setErrorText(repositories.size() > 1 ? "No common target branch" : "No target branch");
    }
    else {
      myRefspecPanel.setTargetBranch(commonTargetBranch);
      setErrorText(null);
      setOKActionEnabled(true);
    }
  }

  @Nullable
  private static String getCommonTargetBranch(Collection<GitRepository> repositories) {
    String commonName = null;
    for (GitRepository repository : repositories) {
      GitLocalBranch currentBranch = repository.getCurrentBranch();
      if (currentBranch == null) {
        return null;
      }
      GitRemoteBranch trackedBranch = currentBranch.findTrackedBranch(repository);
      if (trackedBranch == null) {
        return null;
      }

      String name = trackedBranch.getNameForRemoteOperations();
      if (commonName == null) {
        commonName = name;
      }
      else if (!name.equals(commonName)) {
        return null;
      }
    }
    return commonName;
  }

  private static String getDefaultRemote(@NotNull Collection<GitRepository> repositories) {
    String commonName = null;
    for (GitRepository repository : repositories) {
      GitLocalBranch currentBranch = repository.getCurrentBranch();
      if (currentBranch == null) {
        return DEFAULT_REMOTE;
      }
      GitRemoteBranch trackedBranch = currentBranch.findTrackedBranch(repository);
      if (trackedBranch == null) {
        return DEFAULT_REMOTE;
      }
      String name = trackedBranch.getRemote().getName();
      if (commonName == null) {
        commonName = name;
      }
      else if (!name.equals(commonName)) {
        return DEFAULT_REMOTE;
      }
    }
    return commonName == null ? DEFAULT_REMOTE : commonName;
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

    JPanel commitListPanel = new JPanel(new BorderLayout());
    commitListPanel.add(myLoadingPanel, BorderLayout.CENTER);
    return commitListPanel;
  }

  private void loadCommitsInBackground(final GitPushSpecs pushSpecs) {
    final ModalityState modalityState = ModalityState.stateForComponent(getRootPane());
    myLoadingPanel.startLoading();
    myOutgoingCommitsCollector.collect(new GitOutgoingCommitsCollector.ResultHandler() {
      @Override
      public void onSuccess(final GitCommitsByRepoAndBranch commits) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            myListPanel.setCommits(pushSpecs.getRepositories(), commits);
            myLoadingPanel.stopLoading();
          }
        }, modalityState);
      }

      @Override
      public void onError(final String error) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            myListPanel.displayError(error);
          }
        }, modalityState);
      }
    });
  }

  @NotNull
  public GitPushSpecs getPushSpecs() {
    Collection<GitRepository> selectedRepositories = myListPanel.getSelectedRepositories();
    Map<GitRepository, GitBranchPair> specs = new HashMap<GitRepository, GitBranchPair>();
    for (GitRepository repository : selectedRepositories) {
      GitBranchPair spec = new GitBranchPair(repository.getCurrentBranch(), getTargetBranch());      // TODO what to do with detached head
      specs.put(repository, spec);
    }
    return new GitPushSpecs(specs);
  }

  @NotNull
  private GitRemoteBranch getTargetBranch() {
    return new GitStandardRemoteBranch(myRefspecPanel.getSelectedRemote(), myRefspecPanel.getBranchToPush(), GitBranch.DUMMY_HASH);
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
  private static Collection<GitRemote> getRemotesWithCommonNames(@NotNull Collection<GitRepository> repositories) {
    if (repositories.isEmpty()) {
      return Collections.emptyList();
    }
    Iterator<GitRepository> iterator = repositories.iterator();
    List<GitRemote> commonRemotes = new ArrayList<GitRemote>(iterator.next().getRemotes());
    while (iterator.hasNext()) {
      GitRepository repository = iterator.next();
      Collection<String> remoteNames = getRemoteNames(repository);
      for (Iterator<GitRemote> commonIter = commonRemotes.iterator(); commonIter.hasNext(); ) {
        GitRemote remote = commonIter.next();
        if (!remoteNames.contains(remote.getName())) {
          commonIter.remove();
        }
      }
    }
    return commonRemotes;
  }

  @NotNull
  private static Collection<String> getRemoteNames(@NotNull GitRepository repository) {
    Collection<String> names = new ArrayList<String>(repository.getRemotes().size());
    for (GitRemote remote : repository.getRemotes()) {
      names.add(remote.getName());
    }
    return names;
  }

  private class RepositoryCheckboxListener implements Consumer<Boolean> {
    @Override public void consume(Boolean checked) {
      update();
    }
  }

  private class RefreshButtonListener implements Runnable {
    @Override
    public void run() {
      loadCommitsInBackground(getPushSpecs());
    }
  }

}
