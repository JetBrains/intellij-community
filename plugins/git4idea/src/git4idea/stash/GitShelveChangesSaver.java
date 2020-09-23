// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.stash;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vcs.changes.VcsShelveChangesSaver;
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangeList;
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangesViewManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import git4idea.commands.Git;
import git4idea.config.GitSaveChangesPolicy;
import git4idea.rollback.GitRollbackEnvironment;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public final class GitShelveChangesSaver extends GitChangesSaver {
  private final VcsShelveChangesSaver myVcsShelveChangesSaver;

  public GitShelveChangesSaver(@NotNull Project project,
                               @NotNull Git git,
                               @NotNull ProgressIndicator indicator,
                               @NotNull @Nls String stashMessage) {
    super(project, git, indicator, GitSaveChangesPolicy.SHELVE, stashMessage);
    myVcsShelveChangesSaver = new VcsShelveChangesSaver(project, indicator, stashMessage) {
      @Override
      protected void doRollback(@NotNull Collection<? extends VirtualFile> rootsToSave) {
        for (VirtualFile root : rootsToSave) {
          GitRollbackEnvironment.resetHardLocal(myProject, root);
          VcsDirtyScopeManager.getInstance(myProject).dirDirtyRecursively(root);
        }
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

  @NonNls
  @Override
  public String toString() {
    return "ShelveChangesSaver. Lists: " + myVcsShelveChangesSaver.getShelvedLists();
  }
}
