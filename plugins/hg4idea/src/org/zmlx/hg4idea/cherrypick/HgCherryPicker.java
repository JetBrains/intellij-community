/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.zmlx.hg4idea.cherrypick;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.cherrypick.VcsCherryPicker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsFullCommitDetails;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgBundle;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.action.HgCommandResultNotifier;
import org.zmlx.hg4idea.command.HgGraftCommand;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.provider.update.HgConflictResolver;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.repo.HgRepositoryManager;
import org.zmlx.hg4idea.util.HgErrorUtil;
import org.zmlx.hg4idea.util.HgUtil;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.zmlx.hg4idea.HgNotificationIdsHolder.GRAFT_CONTINUE_ERROR;
import static org.zmlx.hg4idea.HgNotificationIdsHolder.GRAFT_ERROR;

public class HgCherryPicker extends VcsCherryPicker {

  @NotNull private final Project myProject;

  public HgCherryPicker(@NotNull Project project) {
    myProject = project;
  }

  @NotNull
  @Override
  public VcsKey getSupportedVcs() {
    return HgVcs.getKey();
  }

  @Override
  @NotNull
  @Nls(capitalization = Nls.Capitalization.Title)
  public String getActionTitle() {
    return HgBundle.message("graft");
  }

  @Override
  public void cherryPick(@NotNull final List<? extends VcsFullCommitDetails> commits) {
    Map<HgRepository, List<VcsFullCommitDetails>> commitsInRoots = DvcsUtil.groupCommitsByRoots(
      HgUtil.getRepositoryManager(myProject), commits);
    for (Map.Entry<HgRepository, List<VcsFullCommitDetails>> entry : commitsInRoots.entrySet()) {
      processGrafting(entry.getKey(), ContainerUtil.map(entry.getValue(),
                                                        commitDetails -> commitDetails.getId().asString()));
    }
  }

  private static void processGrafting(@NotNull HgRepository repository, @NotNull List<String> hashes) {
    Project project = repository.getProject();
    VirtualFile root = repository.getRoot();
    HgGraftCommand command = new HgGraftCommand(project, repository);
    HgCommandResult result = command.startGrafting(hashes);
    boolean hasConflicts = HgConflictResolver.hasConflicts(project, root);
    if (!hasConflicts && HgErrorUtil.isCommandExecutionFailed(result)) {
      new HgCommandResultNotifier(project).notifyError(GRAFT_ERROR,
                                                       result,
                                                       HgBundle.message("hg4idea.hg.error"),
                                                       HgBundle.message("action.hg4idea.Graft.error"));
      return;
    }
    final UpdatedFiles updatedFiles = UpdatedFiles.create();
    while (hasConflicts) {
      new HgConflictResolver(project, updatedFiles).resolve(root);
      hasConflicts = HgConflictResolver.hasConflicts(project, root);
      if (!hasConflicts) {
        result = command.continueGrafting();
        hasConflicts = HgConflictResolver.hasConflicts(project, root);
      }
      else {
        new HgCommandResultNotifier(project).notifyError(GRAFT_CONTINUE_ERROR,
                                                         result,
                                                         HgBundle.message("hg4idea.hg.error"),
                                                         HgBundle.message("action.hg4idea.Graft.continue.error"));
        break;
      }
    }
    repository.update();
    root.refresh(true, true);
  }

  @Override
  public boolean canHandleForRoots(@NotNull Collection<? extends VirtualFile> roots) {
    HgRepositoryManager hgRepositoryManager = HgUtil.getRepositoryManager(myProject);
    return roots.stream().allMatch(r -> hgRepositoryManager.getRepositoryForRootQuick(r) != null);
  }
}
