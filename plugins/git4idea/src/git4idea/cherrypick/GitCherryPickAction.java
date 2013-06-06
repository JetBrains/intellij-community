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
import git4idea.GitLocalBranch;
import git4idea.GitPlatformFacade;
import git4idea.GitVcs;
import git4idea.commands.Git;
import git4idea.config.GitVcsSettings;
import git4idea.history.browser.GitHeavyCommit;
import git4idea.history.wholeTree.AbstractHash;
import git4idea.history.wholeTree.GitCommitDetailsProvider;
import git4idea.repo.GitRepository;
import icons.Git4ideaIcons;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Kirill Likhodedov
 */
public class GitCherryPickAction extends DumbAwareAction {
  private static final Logger LOG = Logger.getInstance(GitCherryPickAction.class);

  @NotNull private final GitPlatformFacade myPlatformFacade;
  @NotNull private final Git myGit;
  @NotNull private final Set<AbstractHash> myIdsInProgress;

  public GitCherryPickAction() {
    super("Cherry-pick", "Cherry-pick", Git4ideaIcons.CherryPick);
    myGit = ServiceManager.getService(Git.class);
    myPlatformFacade = ServiceManager.getService(GitPlatformFacade.class);
    myIdsInProgress = new HashSet<AbstractHash>();
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getProject();
    final List<GitHeavyCommit> commits = e.getData(GitVcs.SELECTED_COMMITS);
    if (project == null || commits == null || commits.isEmpty()) {
      LOG.info(String.format("Cherry-pick action should be disabled. Project: %s, commits: %s", project, commits));
      return;
    }

    for (GitHeavyCommit commit : commits) {
      myIdsInProgress.add(commit.getShortHash());
    }

    FileDocumentManager.getInstance().saveAllDocuments();
    myPlatformFacade.getChangeListManager(project).blockModalNotifications();

    new Task.Backgroundable(project, "Cherry-picking", false) {
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          boolean autoCommit = GitVcsSettings.getInstance(myProject).isAutoCommitOnCherryPick();
          Map<GitRepository, List<GitHeavyCommit>> commitsInRoots = sortCommits(groupCommitsByRoots(project, commits));
          new GitCherryPicker(myProject, myGit, myPlatformFacade, autoCommit).cherryPick(commitsInRoots);
        }
        finally {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              myPlatformFacade.getChangeListManager(project).unblockModalNotifications();
              for (GitHeavyCommit commit : commits) {
                myIdsInProgress.remove(commit.getShortHash());
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
  private static Map<GitRepository, List<GitHeavyCommit>> sortCommits(@NotNull Map<GitRepository, List<GitHeavyCommit>> groupedCommits) {
    for (List<GitHeavyCommit> gitCommits : groupedCommits.values()) {
      Collections.reverse(gitCommits);
    }
    return groupedCommits;
  }

  @NotNull
  private Map<GitRepository, List<GitHeavyCommit>> groupCommitsByRoots(@NotNull Project project, @NotNull List<GitHeavyCommit> commits) {
    Map<GitRepository, List<GitHeavyCommit>> groupedCommits = new HashMap<GitRepository, List<GitHeavyCommit>>();
    for (GitHeavyCommit commit : commits) {
      GitRepository repository = myPlatformFacade.getRepositoryManager(project).getRepositoryForRoot(commit.getRoot());
      if (repository == null) {
        LOG.info("No repository found for commit " + commit);
        continue;
      }
      List<GitHeavyCommit> commitsInRoot = groupedCommits.get(repository);
      if (commitsInRoot == null) {
        commitsInRoot = new ArrayList<GitHeavyCommit>();
        groupedCommits.put(repository, commitsInRoot);
      }
      commitsInRoot.add(commit);
    }
    return groupedCommits;
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    e.getPresentation().setEnabled(enabled(e));
  }

  private boolean enabled(AnActionEvent e) {
    final List<GitHeavyCommit> commits = e.getData(GitVcs.SELECTED_COMMITS);
    final Project project = e.getProject();

    if (commits == null || commits.isEmpty() || project == null) {
      return false;
    }

    for (GitHeavyCommit commit : commits) {
      if (myIdsInProgress.contains(commit.getShortHash())) {
        return false;
      }
      GitRepository repository = myPlatformFacade.getRepositoryManager(project).getRepositoryForRoot(commit.getRoot());
      if (repository == null) {
        return false;
      }
      GitLocalBranch currentBranch = repository.getCurrentBranch();
      GitCommitDetailsProvider detailsProvider = e.getData(GitVcs.COMMIT_DETAILS_PROVIDER);
      if (currentBranch != null && detailsProvider != null &&
          detailsProvider.getContainingBranches(repository.getRoot(), commit.getShortHash()).contains(currentBranch.getName())) {
        // already is contained in the current branch
        return false;
      }
    }
    return true;
  }
}
