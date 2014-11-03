/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsFullCommitDetails;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgVcsMessages;
import org.zmlx.hg4idea.repo.HgRepository;

public class HgUpdateToFromLogAction extends HgLogSingleCommitAction {
  @Override
  protected void actionPerformed(@NotNull final HgRepository repository, @NotNull VcsFullCommitDetails commit) {
    final Hash revisionHash = commit.getId();
    final Project project = repository.getProject();
    final VirtualFile root = repository.getRoot();
    FileDocumentManager.getInstance().saveAllDocuments();
    new Task.Backgroundable(project, HgVcsMessages.message("hg4idea.progress.updatingTo", revisionHash.toShortString())) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        HgUpdateToAction.updateTo(project, root, revisionHash.asString(), false);
      }
    }.queue();
  }
}
