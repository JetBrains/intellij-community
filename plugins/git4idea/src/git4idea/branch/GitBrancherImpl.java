// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.branch;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.CompareWithLocalDialog;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitReference;
import git4idea.GitVcs;
import git4idea.changes.GitChangeUtils;
import git4idea.commands.Git;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

class GitBrancherImpl implements GitBrancher {

  private final @NotNull Project myProject;

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

  @Override
  public void createBranch(@NotNull String name, @NotNull Map<GitRepository, String> startPoints) {
    createBranch(name, startPoints, null);
  }

  private @NotNull GitBranchWorker newWorker(@NotNull ProgressIndicator indicator) {
    return new GitBranchWorker(myProject, Git.getInstance(), new GitBranchUiHandlerImpl(myProject, indicator));
  }

  private @NotNull GitBranchWorker newWorkerWithoutRollback(@NotNull ProgressIndicator indicator) {
    return new GitBranchWorker(myProject, Git.getInstance(), new GitBranchUiHandlerImpl(myProject, indicator) {
      @Override
      public boolean showUnmergedFilesMessageWithRollback(@NotNull String operationName, @NotNull String rollbackProposal) {
        return false;
      }
    });
  }
  @Override
  public void createBranch(@NotNull String name, @NotNull Map<GitRepository, String> startPoints, @Nullable Runnable callInAwtLater) {
    createBranch(name, startPoints, false, callInAwtLater);
  }

  @Override
  public void createBranch(@NotNull String name, @NotNull Map<GitRepository, String> startPoints, boolean force) {
    createBranch(name, startPoints, force, null);
  }

  @Override
  public void createBranch(@NotNull String name, @NotNull Map<GitRepository, String> startPoints, boolean force,
                           @Nullable Runnable callInAwtLater) {
    new CommonBackgroundTask(myProject, GitBundle.message("branch.creating.branch.process", name), callInAwtLater) {
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
    new CommonBackgroundTask(myProject, getRefsDeletionProgressMessage(branchNames), callInAwtAfterExecution) {
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
    new CommonBackgroundTask(myProject, getRefsDeletionProgressMessage(branchNames), null) {
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
  public void showDiff(@NotNull String branchName, @NotNull String otherBranchName, @NotNull List<? extends GitRepository> repositories) {
    String dialogTitle = GitBundle.message("git.log.diff.handler.changes.between.revisions.title",
                                           branchName, otherBranchName);
    CompareWithLocalDialog.showChanges(myProject, dialogTitle, CompareWithLocalDialog.LocalContent.NONE, () -> {
      List<Change> changes = new ArrayList<>();
      for (GitRepository repository : repositories) {
        VirtualFile root = repository.getRoot();
        changes.addAll(GitChangeUtils.getDiff(myProject, root, branchName, otherBranchName,
                                              Collections.singleton(VcsUtil.getFilePath(root))));
      }
      return changes;
    });
  }

  @Override
  public void merge(@NotNull GitReference reference,
                    @NotNull DeleteOnMergeOption deleteOnMerge,
                    @NotNull List<? extends @NotNull GitRepository> repositories) {
    merge(reference, deleteOnMerge, repositories, true);
  }

  @Override
  public void merge(@NotNull GitReference reference,
                    @NotNull DeleteOnMergeOption deleteOnMerge,
                    @NotNull List<? extends @NotNull GitRepository> repositories,
                    boolean allowRollback) {
    new CommonBackgroundTask(myProject, GitBundle.message("branch.merging.process", reference.getName()), null) {
      @Override
      public void execute(@NotNull ProgressIndicator indicator) {
        GitBranchWorker worker = allowRollback ? newWorker(indicator) : newWorkerWithoutRollback(indicator);
        worker.merge(reference, deleteOnMerge, repositories);
      }
    }.runInBackground();
  }

  @Override
  public void merge(@NotNull String branchName,
                    @NotNull DeleteOnMergeOption deleteOnMerge,
                    @NotNull List<? extends GitRepository> repositories) {
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
    deleteTags(Collections.singletonMap(name, repositories));
  }

  @Override
  public void deleteTags(@NotNull Map<String, List<? extends GitRepository>> tagsToContainingRepositories) {
    if (tagsToContainingRepositories.isEmpty()) return;
    new CommonBackgroundTask(myProject, getRefsDeletionProgressMessage(tagsToContainingRepositories.keySet()), null) {
      @Override
      public void execute(@NotNull ProgressIndicator indicator) {
        GitBranchWorker worker = newWorker(indicator);
        tagsToContainingRepositories.forEach(worker::deleteTag);
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

  private static @NotNull @Nls String getRefsDeletionProgressMessage(Collection<String> refsNames) {
    String names = refsNames.size() == 1 ? refsNames.iterator().next() : StringUtil.join(refsNames, ", ");
    return GitBundle.message("branch.deleting.branch.process", names);
  }

  /**
   * Executes common operations before/after executing the actual branch operation.
   */
  private abstract static class CommonBackgroundTask extends Task.Backgroundable {

    private final @Nullable Runnable myCallInAwtAfterExecution;

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
