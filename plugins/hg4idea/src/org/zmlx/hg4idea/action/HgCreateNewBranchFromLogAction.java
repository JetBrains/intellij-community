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
package org.zmlx.hg4idea.action;

import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.vcs.log.Hash;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgVcsMessages;
import org.zmlx.hg4idea.branch.HgBranchPopupActions;
import org.zmlx.hg4idea.command.HgUpdateCommand;
import org.zmlx.hg4idea.repo.HgRepository;

import java.util.Collections;

import static org.zmlx.hg4idea.util.HgUtil.getNewBranchNameFromUser;

public class HgCreateNewBranchFromLogAction extends HgLogSingleCommitAction {
  @Override
  protected void actionPerformed(@NotNull final HgRepository repository, @NotNull final Hash commit) {
    final Project project = repository.getProject();
    FileDocumentManager.getInstance().saveAllDocuments();
    String shortHash = commit.toShortString();
    final String name = getNewBranchNameFromUser(repository, "Create New Branch From " + shortHash);
    if (name != null) {
      new Task.Backgroundable(project, HgVcsMessages.message("hg4idea.progress.updatingTo", shortHash)) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          if (HgUpdateCommand.updateRepoToInCurrentThread(project, repository.getRoot(), commit.asString(), false)) {
            new HgBranchPopupActions.HgNewBranchAction(project, Collections.singletonList(repository), repository).createNewBranch(name);
          }
        }
      }.queue();
    }
  }
}
