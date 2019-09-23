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
package git4idea.branch;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.vcs.log.VcsLogRangeFilter;
import com.intellij.vcs.log.VcsLogRootFilter;
import com.intellij.vcs.log.impl.VcsLogContentUtil;
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject;
import git4idea.GitVcs;
import git4idea.commands.Git;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

class GitBrancherImpl implements GitBrancher {

  @NotNull private final Project myProject;
  @NotNull private final Git myGit;

  GitBrancherImpl(@NotNull Project project, @NotNull Git git) {
    myProject = project;
    myGit = git;
  }

  @Override
  public void checkoutNewBranch(@NotNull String name, @NotNull List<? extends GitRepository> repositories) {
    new CommonBackgroundTask(myProject, "Checking out new branch " + name, null) {
      @Override
      public void execute(@NotNull ProgressIndicator indicator) {
        newWorker(indicator).checkoutNewBranch(name, repositories);
      }
    }.runInBackground();
  }

  @NotNull
  private GitBranchWorker newWorker(@NotNull ProgressIndicator indicator) {
    return new GitBranchWorker(myProject, myGit, new GitBranchUiHandlerImpl(myProject, myGit, indicator));
  }

  @Override
  public void createBranch(@NotNull String name, @NotNull Map<GitRepository, String> startPoints) {
    new CommonBackgroundTask(myProject, "Creating branch " + name, null) {
      @Override
      public void execute(@NotNull ProgressIndicator indicator) {
        newWorker(indicator).createBranch(name, startPoints);
      }
    }.runInBackground();
  }

  @Override
  public void createNewTag(@NotNull String name, @NotNull String reference,
                            @NotNull List<? extends GitRepository> repositories,
                            @Nullable Runnable callInAwtLater) {
    new CommonBackgroundTask(myProject, "Checking out new branch " + name, callInAwtLater) {
      @Override
      public void execute(@NotNull ProgressIndicator indicator) {
        newWorker(indicator).createNewTag(name, reference, repositories);
      }
    }.runInBackground();
  }

  @Override
  public void checkout(@NotNull String reference,
                       boolean detach,
                       @NotNull List<? extends GitRepository> repositories,
                       @Nullable Runnable callInAwtLater) {
    new CommonBackgroundTask(myProject, "Checking out " + reference, callInAwtLater) {
      @Override
      public void execute(@NotNull ProgressIndicator indicator) {
        newWorker(indicator).checkout(reference, detach, repositories);
      }
    }.runInBackground();
  }

  @Override
  public void checkoutNewBranchStartingFrom(@NotNull String newBranchName,
                                            @NotNull String startPoint,
                                            @NotNull List<? extends GitRepository> repositories,
                                            @Nullable Runnable callInAwtLater) {
    new CommonBackgroundTask(myProject, String.format("Checking out %s from %s", newBranchName, startPoint), callInAwtLater) {
      @Override
      public void execute(@NotNull ProgressIndicator indicator) {
        newWorker(indicator).checkoutNewBranchStartingFrom(newBranchName, startPoint, repositories);
      }
    }.runInBackground();
  }

  @Override
  public void deleteBranch(@NotNull String branchName, @NotNull List<? extends GitRepository> repositories) {
    new CommonBackgroundTask(myProject, "Deleting " + branchName, null) {
      @Override
      public void execute(@NotNull ProgressIndicator indicator) {
        newWorker(indicator).deleteBranch(branchName, repositories);
      }
    }.runInBackground();
  }

  @Override
  public void deleteRemoteBranch(@NotNull String branchName, @NotNull List<? extends GitRepository> repositories) {
    new CommonBackgroundTask(myProject, "Deleting " + branchName, null) {
      @Override
      public void execute(@NotNull ProgressIndicator indicator) {
        newWorker(indicator).deleteRemoteBranch(branchName, repositories);
      }
    }.runInBackground();
  }

  @Override
  public void compare(@NotNull String branchName, @NotNull List<? extends GitRepository> repositories,
                      @NotNull GitRepository selectedRepository) {
    new GitCompareBranchesUi(myProject, repositories, branchName).create();
  }

  @Override
  public void showDiffWithLocal(@NotNull String branchName, @NotNull List<? extends GitRepository> repositories) {
    new ShowDiffWithBranchDialog(myProject, branchName, repositories, GitBranchUtil.getCurrentBranchOrRev(repositories)).show();
  }

  @Override
  public void merge(@NotNull String branchName, @NotNull DeleteOnMergeOption deleteOnMerge, @NotNull List<? extends GitRepository> repositories) {
    new CommonBackgroundTask(myProject, "Merging " + branchName, null) {
      @Override
      public void execute(@NotNull ProgressIndicator indicator) {
        newWorker(indicator).merge(branchName, deleteOnMerge, repositories);
      }
    }.runInBackground();
  }

  @Override
  public void rebase(@NotNull List<? extends GitRepository> repositories, @NotNull String branchName) {
    new CommonBackgroundTask(myProject, "Rebasing onto " + branchName, null) {
      @Override
      void execute(@NotNull ProgressIndicator indicator) {
        newWorker(indicator).rebase(repositories, branchName);
      }
    }.runInBackground();
  }

  @Override
  public void rebaseOnCurrent(@NotNull List<? extends GitRepository> repositories, @NotNull String branchName) {
    new CommonBackgroundTask(myProject, "Rebasing " + branchName + "...", null) {
      @Override
      void execute(@NotNull ProgressIndicator indicator) {
        newWorker(indicator).rebaseOnCurrent(repositories, branchName);
      }
    }.runInBackground();
  }

  @Override
  public void renameBranch(@NotNull String currentName, @NotNull String newName, @NotNull List<? extends GitRepository> repositories) {
    new CommonBackgroundTask(myProject, "Renaming " + currentName + " to " + newName + "...", null) {
      @Override
      void execute(@NotNull ProgressIndicator indicator) {
        newWorker(indicator).renameBranch(currentName, newName, repositories);
      }
    }.runInBackground();
  }

  @Override
  public void deleteTag(@NotNull String name, @NotNull List<? extends GitRepository> repositories) {
    new CommonBackgroundTask(myProject, "Deleting tag " + name, null) {
      @Override
      public void execute(@NotNull ProgressIndicator indicator) {
        newWorker(indicator).deleteTag(name, repositories);
      }
    }.runInBackground();
  }

  @Override
  public void deleteRemoteTag(@NotNull String name, @NotNull Map<GitRepository, String> repositories) {
    new CommonBackgroundTask(myProject, "Deleting tag " + name + " on remote", null) {
      @Override
      public void execute(@NotNull ProgressIndicator indicator) {
        newWorker(indicator).deleteRemoteTag(name, repositories);
      }
    }.runInBackground();
  }

  /**
   * Executes common operations before/after executing the actual branch operation.
   */
  private static abstract class CommonBackgroundTask extends Task.Backgroundable {

    @Nullable private final Runnable myCallInAwtAfterExecution;

    private CommonBackgroundTask(@Nullable Project project, @NotNull String title, @Nullable Runnable callInAwtAfterExecution) {
      super(project, title);
      myCallInAwtAfterExecution = callInAwtAfterExecution;
    }

    @Override
    public final void run(@NotNull ProgressIndicator indicator) {
      execute(indicator);
      if (myCallInAwtAfterExecution != null) {
        Application application = ApplicationManager.getApplication();
        if (application.isUnitTestMode()) {
          myCallInAwtAfterExecution.run();
        }
        else {
          application.invokeLater(myCallInAwtAfterExecution, application.getDefaultModalityState());
        }
      }
    }

    abstract void execute(@NotNull ProgressIndicator indicator);

    void runInBackground() {
      GitVcs.runInBackground(this);
    }
  }
}
