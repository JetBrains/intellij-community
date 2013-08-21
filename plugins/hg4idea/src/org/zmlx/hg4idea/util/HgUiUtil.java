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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgVcsMessages;
import org.zmlx.hg4idea.action.HgCommandResultNotifier;
import org.zmlx.hg4idea.command.HgTagBranch;
import org.zmlx.hg4idea.command.HgTagBranchCommand;
import org.zmlx.hg4idea.execution.HgCommandResult;

import javax.swing.*;
import java.util.Collection;
import java.util.Map;

/**
 * @author Nadya Zabrodina
 */
public class HgUiUtil {

  public static void loadBranchesInBackgroundableAndExecuteAction(@NotNull final Project project,
                                                                  @NotNull final Collection<VirtualFile> repos,
                                                                  @NotNull final Consumer<HgBranchesAndTags> successHandler) {
    final HgBranchesAndTags branchTagInfo = new HgBranchesAndTags();
    new Task.Backgroundable(project, "Collecting information...") {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        for (final VirtualFile repo : repos) {
          HgTagBranchCommand tagBranchCommand = new HgTagBranchCommand(project, repo);
          HgCommandResult result = tagBranchCommand.collectBranches();
          if (result == null) {
            indicator.cancel();
            return;
          }
          branchTagInfo.addBranches(repo, HgTagBranchCommand.parseResult(result));
          result = tagBranchCommand.collectTags();
          if (result == null) {
            indicator.cancel();
            return;
          }
          branchTagInfo.addTags(repo, HgTagBranchCommand.parseResult(result));

          result = tagBranchCommand.collectBookmarks();
          if (result == null) {
            indicator.cancel();
            return;
          }
          branchTagInfo.addBookmarks(repo, HgTagBranchCommand.parseResult(result));
        }
      }

      @Override
      public void onCancel() {
        new HgCommandResultNotifier(project)
          .notifyError(null, "Mercurial command failed", HgVcsMessages.message("hg4idea.branches.error.description"));
      }

      @Override
      public void onSuccess() {
        successHandler.consume(branchTagInfo);
      }
    }.queue();
  }

  public static void loadContentToDialog(@Nullable VirtualFile root, @NotNull Map<VirtualFile, Collection<HgTagBranch>> contentMap,
                                         @NotNull JComboBox selector) {
    assert contentMap.get(root) != null : "No information about root " + root;
    selector.setModel(new DefaultComboBoxModel(contentMap.get(root).toArray()));
  }
}
