/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package git4idea.stash;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.VcsShelveChangesSaver;
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangesViewManager;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.commands.Git;
import git4idea.rollback.GitRollbackEnvironment;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class GitShelveChangesSaver extends GitChangesSaver {
  private final VcsShelveChangesSaver myVcsShelveChangesSaver;
  private final ShelvedChangesViewManager myShelveViewManager;

  public GitShelveChangesSaver(@NotNull Project project, @NotNull Git git, @NotNull ProgressIndicator indicator, String stashMessage) {
    super(project, git, indicator, stashMessage);
    myShelveViewManager = ShelvedChangesViewManager.getInstance(myProject);
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
  protected void save(@NotNull Collection<VirtualFile> rootsToSave) throws VcsException {
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
  public String getSaverName() {
    return "shelf";
  }

  @NotNull
  @Override
  public String getOperationName() {
    return "shelve";
  }

  @Override
  public void showSavedChanges() {
    if (myVcsShelveChangesSaver.getShelvedLists() == null) {
      return;
    }
    myShelveViewManager
      .activateView(myVcsShelveChangesSaver.getShelvedLists().get(myVcsShelveChangesSaver.getShelvedLists().keySet().iterator().next()));
  }

  @Override
  public String toString() {
    return "ShelveChangesSaver. Lists: " + myVcsShelveChangesSaver.getShelvedLists();
  }
}
