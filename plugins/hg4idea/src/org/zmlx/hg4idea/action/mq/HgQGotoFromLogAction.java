/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.zmlx.hg4idea.action.mq;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsFullCommitDetails;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgNameWithHashInfo;
import org.zmlx.hg4idea.HgVcsMessages;
import org.zmlx.hg4idea.command.mq.HgQGotoCommand;
import org.zmlx.hg4idea.command.mq.HgQPopCommand;
import org.zmlx.hg4idea.repo.HgRepository;

public class HgQGotoFromLogAction extends HgMqAppliedPatchAction {
  @Override
  protected void actionPerformed(@NotNull final HgRepository repository, @NotNull final VcsFullCommitDetails commit) {
    final Project project = repository.getProject();
    final Hash parentHash = commit.getParents().get(0);
    final HgNameWithHashInfo parentPatchName = ContainerUtil.find(repository.getMQAppliedPatches(), new Condition<HgNameWithHashInfo>() {
      @Override
      public boolean value(HgNameWithHashInfo info) {
        return info.getHash().equals(parentHash);
      }
    });
    new Task.Backgroundable(repository.getProject(), parentPatchName != null
                                                     ? HgVcsMessages.message("hg4idea.mq.progress.goto", parentPatchName)
                                                     : HgVcsMessages.message("hg4idea.mq.progress.pop")) {

      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        if (parentPatchName != null) {
          new HgQGotoCommand(repository).executeInCurrentThread(parentPatchName.getName());
        }
        else {
          new HgQPopCommand(repository).executeInCurrentThread();
        }
      }

      @Override
      public void onSuccess() {
        HgShowUnAppliedPatchesAction.showUnAppliedPatches(project, repository);
      }
    }.queue();
  }
}
