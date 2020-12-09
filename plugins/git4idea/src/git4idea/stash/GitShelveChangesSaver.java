// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
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
      Set<FilePath> filePaths = new HashSet<>();
      for (Change change : shelvedChanges) {
        ContainerUtil.addAllNotNull(filePaths, ChangesUtil.getBeforePath(change));
        ContainerUtil.addAllNotNull(filePaths, ChangesUtil.getAfterPath(change));
      }

      GitUtil.sortFilePathsByGitRootIgnoringMissing(myProject, filePaths).forEach((root, paths) -> {
        if (!rootsToSave.contains(root)) {
          LOG.warn(String.format("Paths not under shelved root: root - %s, paths - %s, shelved roots - %s", root, paths, rootsToSave));
          return;
        }

        GitRepository repository = GitRepositoryManager.getInstance(myProject).getRepositoryForRoot(root);
        boolean isFreshRepository = repository != null && repository.getCurrentRevision() == null;
        if (isFreshRepository) {
          resetHardLocal(myProject, root);
        }
        else {
          restoreStagedWorktree(myProject, root, paths);
        }
      });
    }
    else {
      for (VirtualFile root : rootsToSave) {
        resetHardLocal(myProject, root);
      }
    }

    for (VirtualFile root : rootsToSave) {
      VcsDirtyScopeManager.getInstance(myProject).dirDirtyRecursively(root);
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

  private static void restoreStagedWorktree(@NotNull Project project, @NotNull VirtualFile root, @NotNull List<FilePath> filePaths) {
    for (List<String> paths : VcsFileUtil.chunkPaths(root, filePaths)) {
      GitLineHandler handler = new GitLineHandler(project, root, GitCommand.RESTORE);
      handler.addParameters("--staged", "--worktree", "--source=HEAD");
      handler.endOptions();
      handler.addParameters(paths);
      GitCommandResult result = Git.getInstance().runCommand(handler);
      if (!result.success()) {
        LOG.warn("Can't restore changes:" + result.getErrorOutputAsJoinedString());
      }
    }
  }

  @NonNls
  @Override
  public String toString() {
    return "ShelveChangesSaver. Lists: " + myVcsShelveChangesSaver.getShelvedLists();
  }
}
