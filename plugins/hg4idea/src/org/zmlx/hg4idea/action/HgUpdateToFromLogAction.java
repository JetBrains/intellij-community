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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.VcsFullCommitDetails;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.command.HgUpdateCommand;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.provider.update.HgConflictResolver;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.util.HgErrorUtil;

/**
 * @author Nadya Zabrodina
 */
public class HgUpdateToFromLogAction extends HgLogSingleCommitAction {
  @Override
  protected void actionPerformed(@NotNull HgRepository repository, @NotNull VcsFullCommitDetails commit) {
    String revisionHash = commit.getHash().asString();
    Project project = repository.getProject();
    VirtualFile rootFile = repository.getRoot();
    HgUpdateCommand updateCommand = new HgUpdateCommand(project, rootFile);
    updateCommand.setRevision(revisionHash);
    HgCommandResult result = updateCommand.execute();
    new HgConflictResolver(project).resolve(rootFile);
    if (HgErrorUtil.hasErrorsInCommandExecution(result)) {
      new HgCommandResultNotifier(project).notifyError(result, "", "Update failed");
    }
    project.getMessageBus().syncPublisher(HgVcs.BRANCH_TOPIC).update(project, null);
  }
}
