// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.zmlx.hg4idea.action;

import com.intellij.openapi.actionSystem.DataContext;
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
  public void execute(final @NotNull Project project,
                      final @NotNull Collection<HgRepository> repos,
                      final @Nullable HgRepository selectedRepo, @NotNull DataContext dataContext) {
    final HgMergeDialog mergeDialog = new HgMergeDialog(project, repos, selectedRepo);
    if (mergeDialog.showAndGet()) {
      final String targetValue = StringUtil.escapeBackSlashes(mergeDialog.getTargetValue());
      final HgRepository repo = mergeDialog.getRepository();
      HgMergeCommand.mergeWith(repo, targetValue, UpdatedFiles.create());
    }
  }
}
