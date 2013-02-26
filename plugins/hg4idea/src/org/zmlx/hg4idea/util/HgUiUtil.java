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
package org.zmlx.hg4idea.util;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgVcsMessages;
import org.zmlx.hg4idea.action.HgCommandResultNotifier;
import org.zmlx.hg4idea.command.HgTagBranch;
import org.zmlx.hg4idea.command.HgTagBranchCommand;
import org.zmlx.hg4idea.execution.HgCommandResult;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author Nadya Zabrodina
 */
public class HgUiUtil {

  public static void loadBranchesInBackgroundableAndExecuteAction(final Project project,
                                                                  final Collection<VirtualFile> repos,
                                                                  final Consumer<Map<VirtualFile, List<HgTagBranch>>> successHandler) {
    final Map<VirtualFile, List<HgTagBranch>> branchesForRepos = new HashMap<VirtualFile, List<HgTagBranch>>();
    new Task.Backgroundable(project, "Collecting information...") {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        for (final VirtualFile repo : repos) {
          HgCommandResult result = new HgTagBranchCommand(project, repo).collectBranches();
          if (result == null) {
            indicator.cancel();
            return;
          }
          branchesForRepos.put(repo, HgTagBranchCommand.parseResult(result));
        }
      }

      @Override
      public void onCancel() {
        new HgCommandResultNotifier(project)
          .notifyError(null, "Mercurial command failed", HgVcsMessages.message("hg4idea.branches.error.description"));
      }

      @Override
      public void onSuccess() {
        successHandler.consume(branchesForRepos);
      }
    }.queue();
  }
}
