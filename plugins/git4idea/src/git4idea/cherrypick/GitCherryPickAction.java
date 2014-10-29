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
package git4idea.cherrypick;

import com.intellij.dvcs.repo.RepositoryManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.*;
import git4idea.GitLocalBranch;
import git4idea.GitPlatformFacade;
import git4idea.GitVcs;
import git4idea.commands.Git;
import git4idea.config.GitVcsSettings;
import git4idea.repo.GitRepository;
import icons.Git4ideaIcons;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Kirill Likhodedov
 */
public class GitCherryPickAction extends DumbAwareAction {

  private static final String NAME = "Cherry-Pick";
  private static final Logger LOG = Logger.getInstance(GitCherryPickAction.class);

  @NotNull private final GitPlatformFacade myPlatformFacade;
  @NotNull private final Git myGit;
  @NotNull private final Set<Hash> myIdsInProgress;

  public GitCherryPickAction() {
    super(NAME, null, Git4ideaIcons.CherryPick);
    myGit = ServiceManager.getService(Git.class);
    myPlatformFacade = ServiceManager.getService(GitPlatformFacade.class);
    myIdsInProgress = ContainerUtil.newHashSet();
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    VcsLog log = e.getRequiredData(VcsLogDataKeys.VCS_LOG);
    final List<? extends VcsFullCommitDetails> commits = log.getSelectedDetails();

    for (VcsFullCommitDetails commit : commits) {
      myIdsInProgress.add(commit.getId());
    }

    FileDocumentManager.getInstance().saveAllDocuments();
    myPlatformFacade.getChangeListManager(project).blockModalNotifications();

    new Task.Backgroundable(project, "Cherry-picking", false) {
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          Map<GitRepository, List<VcsFullCommitDetails>> commitsInRoots = sortCommits(groupCommitsByRoots(project, commits));
          new GitCherryPicker(project, myGit, myPlatformFacade, isAutoCommit(project)).cherryPick(commitsInRoots);
        }
        finally {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              myPlatformFacade.getChangeListManager(project).unblockModalNotifications();
              for (VcsFullCommitDetails commit : commits) {
                myIdsInProgress.remove(commit.getId());
              }
            }
          });
        }
      }
    }.queue();
  }

  /**
   * Sort commits so that earliest ones come first: they need to be cherry-picked first.
   */
  @NotNull
  private static Map<GitRepository, List<VcsFullCommitDetails>> sortCommits(Map<GitRepository, List<VcsFullCommitDetails>> groupedCommits) {
    for (List<VcsFullCommitDetails> gitCommits : groupedCommits.values()) {
      Collections.reverse(gitCommits);
    }
    return groupedCommits;
  }

  @NotNull
  private Map<GitRepository, List<VcsFullCommitDetails>> groupCommitsByRoots(@NotNull Project project,
                                                                       @NotNull List<? extends VcsFullCommitDetails> commits) {
    Map<GitRepository, List<VcsFullCommitDetails>> groupedCommits = ContainerUtil.newHashMap();
    for (VcsFullCommitDetails commit : commits) {
      GitRepository repository = myPlatformFacade.getRepositoryManager(project).getRepositoryForRoot(commit.getRoot());
      if (repository == null) {
        LOG.info("No repository found for commit " + commit);
        continue;
      }
      List<VcsFullCommitDetails> commitsInRoot = groupedCommits.get(repository);
      if (commitsInRoot == null) {
        commitsInRoot = ContainerUtil.newArrayList();
        groupedCommits.put(repository, commitsInRoot);
      }
      commitsInRoot.add(commit);
    }
    return groupedCommits;
  }

  private static boolean isAutoCommit(@NotNull Project project) {
    return GitVcsSettings.getInstance(project).isAutoCommitOnCherryPick();
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    e.getPresentation().setVisible(true);
    VcsLog log = e.getData(VcsLogDataKeys.VCS_LOG);
    Project project = getEventProject(e);
    if (project == null || log == null || !logHasGitRoots(log)) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    List<VcsFullCommitDetails> details = log.getSelectedDetails();
    if (notFromGitAndProject(project, details)) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    e.getPresentation().setEnabled(enabled(project, log, details));
    e.getPresentation().setText(isAutoCommit(project) ? NAME : NAME + "...");
  }

  public static boolean logHasGitRoots(@NotNull VcsLog log) {
    return ContainerUtil.find(log.getLogProviders(), new Condition<VcsLogProvider>() {
      @Override
      public boolean value(VcsLogProvider logProvider) {
        return logProvider.getSupportedVcs().equals(GitVcs.getKey());
      }
    }) != null;
  }

  private boolean notFromGitAndProject(@NotNull Project project, @NotNull List<VcsFullCommitDetails> details) {
    final RepositoryManager<GitRepository> manager = myPlatformFacade.getRepositoryManager(project);
    return ContainerUtil.and(details, new Condition<VcsFullCommitDetails>() {
      @Override
      public boolean value(VcsFullCommitDetails commit) {
        GitRepository repository = manager.getRepositoryForRoot(commit.getRoot());
        return repository != null && manager.isExternal(repository);
      }
    });
  }

  private boolean enabled(@NotNull Project project, @NotNull VcsLog log, @NotNull List<VcsFullCommitDetails> commits) {
    if (commits.isEmpty()) {
      return false;
    }

    for (VcsFullCommitDetails commit : commits) {
      if (myIdsInProgress.contains(commit.getId())) {
        return false;
      }
      GitRepository repository = myPlatformFacade.getRepositoryManager(project).getRepositoryForRoot(commit.getRoot());
      if (repository == null) {
        //it may be a non-git repository
        return false;
      }
      GitLocalBranch currentBranch = repository.getCurrentBranch();
      Collection<String> containingBranches = log.getContainingBranches(commit.getId());
      if (currentBranch != null &&  containingBranches != null && containingBranches.contains(currentBranch.getName())) {
        // already is contained in the current branch
        return false;
      }
    }
    return true;
  }

}
