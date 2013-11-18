/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.*;
import git4idea.GitLocalBranch;
import git4idea.GitPlatformFacade;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.commands.Git;
import git4idea.config.GitVcsSettings;
import git4idea.history.browser.GitHeavyCommit;
import git4idea.history.wholeTree.AbstractHash;
import git4idea.history.wholeTree.GitCommitDetailsProvider;
import git4idea.log.GitContentRevisionFactory;
import git4idea.repo.GitRepository;
import icons.Git4ideaIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Kirill Likhodedov
 */
public class GitCherryPickAction extends DumbAwareAction {
  private static final Logger LOG = Logger.getInstance(GitCherryPickAction.class);

  @NotNull private final GitPlatformFacade myPlatformFacade;
  @NotNull private final Git myGit;
  @NotNull private final Set<Hash> myIdsInProgress;

  public GitCherryPickAction() {
    super("Cherry-pick", "Cherry-pick", Git4ideaIcons.CherryPick);
    myGit = ServiceManager.getService(Git.class);
    myPlatformFacade = ServiceManager.getService(GitPlatformFacade.class);
    myIdsInProgress = ContainerUtil.newHashSet();
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getProject();
    final List<? extends VcsFullCommitDetails> commits = getSelectedCommits(e);
    if (project == null || commits == null || commits.isEmpty()) {
      LOG.info(String.format("Cherry-pick action should be disabled. Project: %s, commits: %s", project, commits));
      return;
    }

    for (VcsFullCommitDetails commit : commits) {
      myIdsInProgress.add(commit.getHash());
    }

    FileDocumentManager.getInstance().saveAllDocuments();
    myPlatformFacade.getChangeListManager(project).blockModalNotifications();

    new Task.Backgroundable(project, "Cherry-picking", false) {
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          boolean autoCommit = GitVcsSettings.getInstance(myProject).isAutoCommitOnCherryPick();
          Map<GitRepository, List<VcsFullCommitDetails>> commitsInRoots = sortCommits(groupCommitsByRoots(project, commits));
          new GitCherryPicker(myProject, myGit, myPlatformFacade, autoCommit).cherryPick(commitsInRoots);
        }
        finally {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              myPlatformFacade.getChangeListManager(project).unblockModalNotifications();
              for (VcsFullCommitDetails commit : commits) {
                myIdsInProgress.remove(commit.getHash());
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

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    final VcsLog log = getVcsLog(e);
    if (log != null && !GitUtil.logHasGitRoot(log)) {
      e.getPresentation().setEnabledAndVisible(false);
    }
    else {
      e.getPresentation().setEnabled(enabled(e));
    }
  }

  private boolean enabled(AnActionEvent e) {
    final List<? extends VcsFullCommitDetails> commits = getSelectedCommits(e);
    final Project project = e.getProject();

    if (commits == null || commits.isEmpty() || project == null) {
      return false;
    }

    for (VcsFullCommitDetails commit : commits) {
      if (myIdsInProgress.contains(commit.getHash())) {
        return false;
      }
      GitRepository repository = myPlatformFacade.getRepositoryManager(project).getRepositoryForRoot(commit.getRoot());
      if (repository == null) {
        return false;
      }
      GitLocalBranch currentBranch = repository.getCurrentBranch();
      Collection<String> containingBranches = getContainingBranches(e, commit, repository);
      if (currentBranch != null &&  containingBranches != null && containingBranches.contains(currentBranch.getName())) {
        // already is contained in the current branch
        return false;
      }
    }
    return true;
  }

  // TODO remove after removing the old Vcs Log implementation
  @Nullable
  private List<? extends VcsFullCommitDetails> getSelectedCommits(AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null) {
      return null;
    }
    List<GitHeavyCommit> commits = e.getData(GitVcs.SELECTED_COMMITS);
    if (commits != null) {
      return convertHeavyCommitToFullDetails(commits, project);
    }
    final VcsLog log = getVcsLog(e);
    if (log == null) {
      return null;
    }

    List<Hash> selectedCommits = log.getSelectedCommits();
    List<VcsFullCommitDetails> selectedDetails = ContainerUtil.newArrayList();
    for (Hash commit : selectedCommits) {
      VcsFullCommitDetails details = log.getDetailsIfAvailable(commit);
      if (details == null) { // let the action be unavailable until all details are loaded
        return null;
      }
      GitRepository root = myPlatformFacade.getRepositoryManager(project).getRepositoryForRoot(details.getRoot());
      // don't allow to cherry-pick if a non-Git commit was selected
      // we could cherry-pick just Git commits filtered from the list, but it might provide confusion
      if (root == null) {
        return null;
      }
      selectedDetails.add(details);
    }
    return selectedDetails;
  }

  private static List<? extends VcsFullCommitDetails> convertHeavyCommitToFullDetails(List<GitHeavyCommit> commits, final Project project) {
    return ContainerUtil.map(commits, new Function<GitHeavyCommit, VcsFullCommitDetails>() {
      @Override
      public VcsFullCommitDetails fun(GitHeavyCommit commit) {
        final VcsLogObjectsFactory factory = ServiceManager.getService(project, VcsLogObjectsFactory.class);
        List<Hash> parents = ContainerUtil.map(commit.getParentsHashes(), new Function<String, Hash>() {
          @Override
          public Hash fun(String hashValue) {
            return factory.createHash(hashValue);
          }
        });
        return factory.createFullDetails(
          factory.createHash(commit.getHash().getValue()), parents, commit.getAuthorTime(), commit.getRoot(), commit.getSubject(),
          commit.getAuthor(), commit.getAuthorEmail(), commit.getDescription(), commit.getCommitter(), commit.getCommitterEmail(),
          commit.getDate().getTime(), commit.getChanges(), GitContentRevisionFactory.getInstance(project)
        );
      }
    });
  }

  private static VcsLog getVcsLog(@NotNull AnActionEvent event) {
    return event.getData(VcsLogDataKeys.VSC_LOG);
  }

  // TODO remove after removing the old Vcs Log implementation
  @Nullable
  private static Collection<String> getContainingBranches(AnActionEvent event, VcsFullCommitDetails commit, GitRepository repository) {
    GitCommitDetailsProvider detailsProvider = event.getData(GitVcs.COMMIT_DETAILS_PROVIDER);
    if (detailsProvider != null) {
      return detailsProvider.getContainingBranches(repository.getRoot(), AbstractHash.create(commit.getHash().toShortString()));
    }
    if (event.getProject() == null) {
      return null;
    }
    VcsLog log = getVcsLog(event);
    if (log == null) {
      return null;
    }
    return log.getContainingBranches(commit.getHash());
  }

}
