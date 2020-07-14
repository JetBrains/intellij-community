// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.stash;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.VcsShelveChangesSaver;
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangesViewManager;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.commands.Git;
import git4idea.config.GitSaveChangesPolicy;
import git4idea.rollback.GitRollbackEnvironment;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public final class GitShelveChangesSaver extends GitChangesSaver {
  private final VcsShelveChangesSaver myVcsShelveChangesSaver;

  public GitShelveChangesSaver(@NotNull Project project,
                               @NotNull Git git,
                               @NotNull ProgressIndicator indicator,
                               @NotNull String stashMessage) {
    super(project, git, indicator, GitSaveChangesPolicy.SHELVE, stashMessage);
    myVcsShelveChangesSaver = new VcsShelveChangesSaver(project, indicator, stashMessage) {
      @Override
      protected void doRollback(@NotNull Collection<? extends VirtualFile> rootsToSave) {
        for (VirtualFile root : rootsToSave) {
          GitRollbackEnvironment.resetHardLocal(myProject, root);
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
    return myVcsShelveChangesSaver.getShelvedLists() != null && !myVcsShelveChangesSaver.getShelvedLists().isEmpty();
  }

  @Override
  public void showSavedChanges() {
    if (myVcsShelveChangesSaver.getShelvedLists() == null) {
      return;
    }
    ShelvedChangesViewManager.getInstance(myProject)
      .activateView(myVcsShelveChangesSaver.getShelvedLists().get(myVcsShelveChangesSaver.getShelvedLists().keySet().iterator().next()));
  }

  @Override
  public String toString() {
    return "ShelveChangesSaver. Lists: " + myVcsShelveChangesSaver.getShelvedLists();
  }
}
