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
import git4idea.GitVcs;
import git4idea.commands.Git;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * @author Kirill Likhodedov
 */
class GitBrancherImpl implements GitBrancher {


  @NotNull private final Project myProject;
  @NotNull private final Git myGit;

  GitBrancherImpl(@NotNull Project project, @NotNull Git git) {
    myProject = project;
    myGit = git;
  }

  @Override
  public void checkoutNewBranch(@NotNull final String name, @NotNull final List<GitRepository> repositories) {
    new CommonBackgroundTask(myProject, "Checking out new branch " + name, null) {
      @Override public void execute(@NotNull ProgressIndicator indicator) {
        newWorker(indicator).checkoutNewBranch(name, repositories);
      }
    }.runInBackground();
  }

  private GitBranchWorker newWorker(ProgressIndicator indicator) {
    return new GitBranchWorker(myProject, myGit, new GitBranchUiHandlerImpl(myProject, myGit, indicator));
  }

  @Override
  public void createBranch(@NotNull String name, @NotNull Map<GitRepository, String> startPoints) {
    new CommonBackgroundTask(myProject, "Creating branch " + name, null) {
      @Override public void execute(@NotNull ProgressIndicator indicator) {
        newWorker(indicator).createBranch(name, startPoints);
      }
    }.runInBackground();
  }

  @Override
  public void createNewTag(@NotNull final String name, @NotNull final String reference, @NotNull final List<GitRepository> repositories,
                           @Nullable Runnable callInAwtLater) {
    new CommonBackgroundTask(myProject, "Checking out new branch " + name, callInAwtLater) {
      @Override public void execute(@NotNull ProgressIndicator indicator) {
        newWorker(indicator).createNewTag(name, reference, repositories);
      }
    }.runInBackground();
  }

  @Override
  public void checkout(@NotNull final String reference,
                       final boolean detach,
                       @NotNull final List<GitRepository> repositories,
                       @Nullable Runnable callInAwtLater) {
    new CommonBackgroundTask(myProject, "Checking out " + reference, callInAwtLater) {
      @Override public void execute(@NotNull ProgressIndicator indicator) {
        newWorker(indicator).checkout(reference, detach, repositories);
      }
    }.runInBackground();
  }

  @Override
  public void checkoutNewBranchStartingFrom(@NotNull final String newBranchName, @NotNull final String startPoint,
                                            @NotNull final List<GitRepository> repositories, @Nullable Runnable callInAwtLater) {
    new CommonBackgroundTask(myProject, String.format("Checking out %s from %s", newBranchName, startPoint), callInAwtLater) {
      @Override
      public void execute(@NotNull ProgressIndicator indicator) {
        newWorker(indicator).checkoutNewBranchStartingFrom(newBranchName, startPoint, repositories);
      }
    }.runInBackground();
  }

  @Override
  public void deleteBranch(@NotNull final String branchName, @NotNull final List<GitRepository> repositories) {
    new CommonBackgroundTask(myProject, "Deleting " + branchName, null) {
      @Override public void execute(@NotNull ProgressIndicator indicator) {
        newWorker(indicator).deleteBranch(branchName, repositories);
      }
    }.runInBackground();
  }

  @Override
  public void deleteRemoteBranch(@NotNull final String branchName, @NotNull final List<GitRepository> repositories) {
    new CommonBackgroundTask(myProject, "Deleting " + branchName, null) {
      @Override public void execute(@NotNull ProgressIndicator indicator) {
        newWorker(indicator).deleteRemoteBranch(branchName, repositories);
      }
    }.runInBackground();
  }

  @Override
  public void compare(@NotNull final String branchName, @NotNull final List<GitRepository> repositories,
                      @NotNull final GitRepository selectedRepository) {
    new CommonBackgroundTask(myProject, "Comparing with " + branchName, null) {
      @Override
      public void execute(@NotNull ProgressIndicator indicator) {
        newWorker(indicator).compare(branchName, repositories, selectedRepository);
      }
    }.runInBackground();

  }

  @Override
  public void merge(@NotNull final String branchName, @NotNull final DeleteOnMergeOption deleteOnMerge,
                    @NotNull final List<GitRepository> repositories) {
    new CommonBackgroundTask(myProject, "Merging " + branchName, null) {
      @Override public void execute(@NotNull ProgressIndicator indicator) {
        newWorker(indicator).merge(branchName, deleteOnMerge, repositories);
      }
    }.runInBackground();
  }

  @Override
  public void rebase(@NotNull final List<GitRepository> repositories, @NotNull final String branchName) {
    new CommonBackgroundTask(myProject, "Rebasing onto " + branchName, null) {
      @Override
      void execute(@NotNull ProgressIndicator indicator) {
        newWorker(indicator).rebase(repositories, branchName);
      }
    }.runInBackground();
  }

  @Override
  public void rebaseOnCurrent(@NotNull final List<GitRepository> repositories, @NotNull final String branchName) {
    new CommonBackgroundTask(myProject, "Rebasing " + branchName + "...", null) {
      @Override
      void execute(@NotNull ProgressIndicator indicator) {
        newWorker(indicator).rebaseOnCurrent(repositories, branchName);
      }
    }.runInBackground();
  }

  @Override
  public void renameBranch(@NotNull final String currentName, @NotNull final String newName, @NotNull final List<GitRepository> repositories) {
    new CommonBackgroundTask(myProject, "Renaming " + currentName + " to " + newName + "...", null) {
      @Override
      void execute(@NotNull ProgressIndicator indicator) {
        newWorker(indicator).renameBranch(currentName, newName, repositories);
      }
    }.runInBackground();
  }

  @Override
  public void deleteTag(@NotNull String name, @NotNull List<GitRepository> repositories) {
    new CommonBackgroundTask(myProject, "Deleting " + name, null) {
      @Override
      public void execute(@NotNull ProgressIndicator indicator) {
        newWorker(indicator).deleteTag(name, repositories);
      }
    }.runInBackground();
  }

  /**
   * Executes common operations before/after executing the actual branch operation.
   */
  private static abstract class CommonBackgroundTask extends Task.Backgroundable {

    @Nullable private final Runnable myCallInAwtAfterExecution;

    private CommonBackgroundTask(@Nullable final Project project, @NotNull final String title, @Nullable Runnable callInAwtAfterExecution) {
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
