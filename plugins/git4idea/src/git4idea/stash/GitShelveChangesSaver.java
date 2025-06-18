// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.stash;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vcs.changes.VcsShelveChangesSaver;
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangeList;
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangesViewManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcsUtil.VcsFileUtil;
import git4idea.GitUtil;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitLineHandler;
import git4idea.config.GitSaveChangesPolicy;
import git4idea.config.GitVersionSpecialty;
import git4idea.index.GitFileStatus;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.util.GitFileUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class GitShelveChangesSaver extends GitChangesSaver {
  private static final Logger LOG = Logger.getInstance(GitShelveChangesSaver.class);
  private final VcsShelveChangesSaver myVcsShelveChangesSaver;

  public GitShelveChangesSaver(@NotNull Project project,
                               @NotNull Git git,
                               @NotNull ProgressIndicator indicator,
                               @NotNull @Nls String stashMessage) {
    super(project, git, indicator, GitSaveChangesPolicy.SHELVE, stashMessage);
    myVcsShelveChangesSaver = new VcsShelveChangesSaver(project, indicator, stashMessage) {
      @Override
      protected void doRollback(@NotNull Collection<? extends VirtualFile> rootsToSave,
                                @NotNull Collection<Change> shelvedChanges) {
        rollbackChanges(rootsToSave, shelvedChanges);
      }
    };
  }

  @Override
  protected void save(@NotNull Collection<? extends VirtualFile> rootsToSave) throws VcsException {
    myVcsShelveChangesSaver.save(rootsToSave);
  }

  @Override
  public void load() {
    myVcsShelveChangesSaver.load();
  }

  @Override
  public boolean wereChangesSaved() {
    List<ShelvedChangeList> shelvedLists = myVcsShelveChangesSaver.getShelvedLists();
    return !shelvedLists.isEmpty();
  }

  @Override
  public void showSavedChanges() {
    List<ShelvedChangeList> shelvedLists = myVcsShelveChangesSaver.getShelvedLists();
    if (!shelvedLists.isEmpty()) {
      Comparator<ShelvedChangeList> nameComparator = Comparator.comparing(it -> it.getDisplayName(), String.CASE_INSENSITIVE_ORDER);
      List<ShelvedChangeList> sorted = ContainerUtil.sorted(shelvedLists, nameComparator);
      ShelvedChangesViewManager.getInstance(myProject).activateView(sorted.get(0));
    }
  }

  private void rollbackChanges(@NotNull Collection<? extends VirtualFile> rootsToSave,
                               @NotNull Collection<Change> shelvedChanges) {
    if (GitVersionSpecialty.RESTORE_SUPPORTED.existsIn(myProject)) {
      List<FilePath> filePaths = ChangesUtil.getPaths(shelvedChanges);
      Map<VirtualFile, List<FilePath>> filesByRoot = GitUtil.sortFilePathsByGitRootIgnoringMissing(myProject, filePaths);

      for (VirtualFile root : rootsToSave) {
        List<FilePath> rootPaths = ContainerUtil.notNullize(filesByRoot.get(root));

        GitRepository repository = GitRepositoryManager.getInstance(myProject).getRepositoryForRoot(root);
        if (repository == null || repository.getCurrentRevision() == null) {
          resetHardLocal(myProject, root);
        }
        else {
          Set<FilePath> rootPathsSet = new HashSet<>(rootPaths);

          // Workaround changes hidden by git4idea.status.GitChangesCollector.collectStagedUnstagedModifications,
          // that will not be in 'shelvedChanges'
          List<FilePath> pathsToUnstage = new ArrayList<>();
          for (GitFileStatus record : repository.getStagingAreaHolder().getAllRecords()) {
            if (record.isTracked() && record.getStagedStatus() != null && !rootPathsSet.contains(record.getPath())) {
              pathsToUnstage.add(record.getPath());
            }
          }

          restoreStagedAndWorktree(myProject, root, rootPaths);
          restoreStaged(myProject, root, pathsToUnstage);
        }
      }
    }
    else {
      for (VirtualFile root : rootsToSave) {
        resetHardLocal(myProject, root);
      }
    }

    for (VirtualFile root : rootsToSave) {
      VcsDirtyScopeManager.getInstance(myProject).rootDirty(root);
    }
  }

  private static void resetHardLocal(@NotNull Project project, @NotNull VirtualFile root) {
    GitLineHandler handler = new GitLineHandler(project, root, GitCommand.RESET);
    handler.addParameters("--hard");
    handler.endOptions();
    GitCommandResult result = Git.getInstance().runCommand(handler);
    if (!result.success()) {
      LOG.warn("Can't reset changes:" + result.getErrorOutputAsJoinedString());
    }
  }

  private static void restoreStagedAndWorktree(@NotNull Project project, @NotNull VirtualFile root, @NotNull List<FilePath> filePaths) {
    try {
      GitFileUtils.restoreStagedAndWorktree(project, root, filePaths, "HEAD");
    }
    catch (VcsException e) {
      LOG.warn("Can't restore changes:" + e.getMessage());
    }
  }

  private static void restoreStaged(@NotNull Project project, @NotNull VirtualFile root, @NotNull List<FilePath> filePaths) {
    for (List<String> paths : VcsFileUtil.chunkPaths(root, filePaths)) {
      GitLineHandler handler = new GitLineHandler(project, root, GitCommand.RESTORE);
      handler.addParameters("--staged", "--source=HEAD");
      handler.endOptions();
      handler.addParameters(paths);
      GitCommandResult result = Git.getInstance().runCommand(handler);
      if (!result.success()) {
        LOG.warn("Can't restore changes:" + result.getErrorOutputAsJoinedString());
      }
    }
  }

  void setReportLocalHistoryActivity(boolean reportLocalHistoryActivity) {
    myVcsShelveChangesSaver.setReportLocalHistoryActivity(reportLocalHistoryActivity);
  }

  @Override
  public @NonNls String toString() {
    return "ShelveChangesSaver. Lists: " + myVcsShelveChangesSaver.getShelvedLists();
  }
}
