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
import com.intellij.openapi.util.text.StringUtil;
import git4idea.GitVcs;
import git4idea.commands.Git;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

class GitBrancherImpl implements GitBrancher {

  @NotNull private final Project myProject;

  GitBrancherImpl(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public void checkoutNewBranch(@NotNull String name, @NotNull List<? extends GitRepository> repositories) {
    new CommonBackgroundTask(myProject, GitBundle.message("branch.checking.out.new.branch.process", name), null) {
      @Override
      public void execute(@NotNull ProgressIndicator indicator) {
        newWorker(indicator).checkoutNewBranch(name, repositories);
      }
    }.runInBackground();
  }

  @NotNull
  private GitBranchWorker newWorker(@NotNull ProgressIndicator indicator) {
    return new GitBranchWorker(myProject, Git.getInstance(), new GitBranchUiHandlerImpl(myProject, indicator));
  }

  @Override
  public void createBranch(@NotNull String name, @NotNull Map<GitRepository, String> startPoints) {
    createBranch(name, startPoints, false);
  }

  @Override
  public void createBranch(@NotNull String name, @NotNull Map<GitRepository, String> startPoints, boolean force) {
    new CommonBackgroundTask(myProject, GitBundle.message("branch.creating.branch.process", name), null) {
      @Override
      public void execute(@NotNull ProgressIndicator indicator) {
        newWorker(indicator).createBranch(name, startPoints, force);
      }
    }.runInBackground();
  }

  @Override
  public void createNewTag(@NotNull String name, @NotNull String reference,
                            @NotNull List<? extends GitRepository> repositories,
                            @Nullable Runnable callInAwtLater) {
    new CommonBackgroundTask(myProject, GitBundle.message("branch.checking.out.new.branch.process", name), callInAwtLater) {
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
    new CommonBackgroundTask(myProject, GitBundle.message("branch.checking.out.process", reference), callInAwtLater) {
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
    checkoutNewBranchStartingFrom(newBranchName, startPoint, false, repositories, callInAwtLater);
  }

  @Override
  public void checkoutNewBranchStartingFrom(@NotNull String newBranchName,
                                            @NotNull String startPoint, boolean overwriteIfNeeded,
                                            @NotNull List<? extends GitRepository> repositories,
                                            @Nullable Runnable callInAwtLater) {
    new CommonBackgroundTask(myProject, GitBundle.message("branch.checking.out.branch.from.process", newBranchName, startPoint), callInAwtLater) {
      @Override
      public void execute(@NotNull ProgressIndicator indicator) {
        newWorker(indicator).checkoutNewBranchStartingFrom(newBranchName, startPoint, overwriteIfNeeded, repositories);
      }
    }.runInBackground();
  }

  @Override
  public void deleteBranch(@NotNull String branchName, @NotNull List<? extends GitRepository> repositories) {
    deleteBranches(Collections.singletonMap(branchName, repositories), null);
  }

  @Override
  public void deleteBranches(@NotNull Map<String, List<? extends GitRepository>> branchesToContainingRepositories,
                             @Nullable Runnable callInAwtAfterExecution) {
    if (branchesToContainingRepositories.isEmpty()) return;
    Set<String> branchNames = branchesToContainingRepositories.keySet();
    String branchMsg = branchNames.size() == 1 ? branchNames.iterator().next() : StringUtil.join(branchNames, ", ");
    new CommonBackgroundTask(myProject, GitBundle.message("branch.deleting.branch.process", branchMsg), callInAwtAfterExecution) {
      @Override
      public void execute(@NotNull ProgressIndicator indicator) {
        GitBranchWorker worker = newWorker(indicator);
        for (String branchName : branchNames) {
          worker.deleteBranch(branchName, branchesToContainingRepositories.getOrDefault(branchName, Collections.emptyList()));
        }
      }
    }.runInBackground();
  }

  @Override
  public void deleteRemoteBranch(@NotNull String branchName, @NotNull List<? extends GitRepository> repositories) {
    deleteRemoteBranches(Collections.singletonList(branchName), repositories);
  }

  @Override
  public void deleteRemoteBranches(@NotNull List<String> branchNames, @NotNull List<? extends GitRepository> repositories) {
    if (branchNames.isEmpty()) return;
    String branchMsg = branchNames.size() == 1 ? branchNames.iterator().next() : StringUtil.join(branchNames, ", ");
    new CommonBackgroundTask(myProject, GitBundle.message("branch.deleting.remote.branch", branchMsg), null) {
      @Override
      public void execute(@NotNull ProgressIndicator indicator) {
        newWorker(indicator).deleteRemoteBranches(branchNames, repositories);
      }
    }.runInBackground();
  }

  @Override
  public void compare(@NotNull String branchName, @NotNull List<? extends GitRepository> repositories) {
    new GitCompareBranchesUi(myProject, repositories, branchName).open();
  }

  @Override
  public void compareAny(@NotNull String branchName, @NotNull String otherBranchName, @NotNull List<? extends GitRepository> repositories) {
    new GitCompareBranchesUi(myProject, repositories, branchName, otherBranchName).open();
  }

  @Override
  public void showDiffWithLocal(@NotNull String branchName, @NotNull List<? extends GitRepository> repositories) {
    new GitShowDiffWithBranchPanel(myProject, branchName, repositories, GitBranchUtil.getCurrentBranchOrRev(repositories)).showAsTab();
  }

  @Override
  public void merge(@NotNull String branchName, @NotNull DeleteOnMergeOption deleteOnMerge, @NotNull List<? extends GitRepository> repositories) {
    new CommonBackgroundTask(myProject, GitBundle.message("branch.merging.process", branchName), null) {
      @Override
      public void execute(@NotNull ProgressIndicator indicator) {
        newWorker(indicator).merge(branchName, deleteOnMerge, repositories);
      }
    }.runInBackground();
  }

  @Override
  public void rebase(@NotNull List<? extends GitRepository> repositories, @NotNull String branchName) {
    new CommonBackgroundTask(myProject, GitBundle.message("branch.rebasing.onto.process", branchName), null) {
      @Override
      void execute(@NotNull ProgressIndicator indicator) {
        newWorker(indicator).rebase(repositories, branchName);
      }
    }.runInBackground();
  }

  @Override
  public void rebaseOnCurrent(@NotNull List<? extends GitRepository> repositories, @NotNull String branchName) {
    rebase(repositories, "HEAD", branchName); //NON-NLS
  }

  @Override
  public void rebase(@NotNull List<? extends GitRepository> repositories, @NotNull String upstream, @NotNull String branchName) {
    new CommonBackgroundTask(myProject, GitBundle.message("branch.rebasing.process", branchName), null) {
      @Override
      void execute(@NotNull ProgressIndicator indicator) {
        newWorker(indicator).rebase(repositories, upstream, branchName);
      }
    }.runInBackground();
  }


  @Override
  public void renameBranch(@NotNull String currentName, @NotNull String newName, @NotNull List<? extends GitRepository> repositories) {
    new CommonBackgroundTask(myProject, GitBundle.message("branch.renaming.branch.process", currentName, newName), null) {
      @Override
      void execute(@NotNull ProgressIndicator indicator) {
        newWorker(indicator).renameBranch(currentName, newName, repositories);
      }
    }.runInBackground();
  }

  @Override
  public void deleteTag(@NotNull String name, @NotNull List<? extends GitRepository> repositories) {
    new CommonBackgroundTask(myProject, GitBundle.message("branch.deleting.tag.process", name), null) {
      @Override
      public void execute(@NotNull ProgressIndicator indicator) {
        newWorker(indicator).deleteTag(name, repositories);
      }
    }.runInBackground();
  }

  @Override
  public void deleteRemoteTag(@NotNull String name, @NotNull Map<GitRepository, String> repositories) {
    new CommonBackgroundTask(myProject, GitBundle.message("branch.deleting.tag.on.remote.process", name), null) {
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

    private CommonBackgroundTask(@Nullable Project project, @Nls @NotNull String title, @Nullable Runnable callInAwtAfterExecution) {
      super(project, title);
      myCallInAwtAfterExecution = callInAwtAfterExecution;
    }

    @Override
    public final void run(@NotNull ProgressIndicator indicator) {
      execute(indicator);
      if (myCallInAwtAfterExecution != null) {
        Application application = ApplicationManager.getApplication();
        application.invokeAndWait(myCallInAwtAfterExecution, application.getDefaultModalityState());
      }
    }

    abstract void execute(@NotNull ProgressIndicator indicator);

    void runInBackground() {
      GitVcs.runInBackground(this);
    }
  }
}
