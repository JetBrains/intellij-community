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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.command.HgMergeCommand;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.ui.HgMergeDialog;

import java.util.Collection;

public class HgMerge extends HgAbstractGlobalSingleRepoAction {

  @Override
  public void execute(@NotNull final Project project,
                      @NotNull final Collection<HgRepository> repos,
                      @Nullable final HgRepository selectedRepo) {
    final HgMergeDialog mergeDialog = new HgMergeDialog(project, repos, selectedRepo);
    if (mergeDialog.showAndGet()) {
      final String targetValue = StringUtil.escapeBackSlashes(mergeDialog.getTargetValue());
      final HgRepository repo = mergeDialog.getRepository();
      HgMergeCommand.mergeWith(repo, targetValue, UpdatedFiles.create());
    }
  }
}
