// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.zmlx.hg4idea.action.mq;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.ui.HgMqUnAppliedPatchesPanel;

import java.util.List;

public abstract class HgActionFromMqPatches extends DumbAwareAction {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final HgMqUnAppliedPatchesPanel patchInfo = e.getRequiredData(HgMqUnAppliedPatchesPanel.MQ_PATCHES);
    final List<String> names = patchInfo.getSelectedPatchNames();
    final HgRepository repository = patchInfo.getRepository();
    Runnable task = () -> {
      ProgressManager.getInstance().getProgressIndicator().setText(getTitle());
      executeInCurrentThread(repository, names);
    };
    patchInfo.updatePatchSeriesInBackground(task);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    HgMqUnAppliedPatchesPanel patchInfo = e.getData(HgMqUnAppliedPatchesPanel.MQ_PATCHES);
    e.getPresentation().setEnabled(patchInfo != null && patchInfo.getSelectedRowsCount() != 0 && isEnabled(patchInfo.getRepository()));
  }

  protected boolean isEnabled(@NotNull HgRepository repository) {
    return true;        //todo should be improved, param not needed
  }

  protected abstract void executeInCurrentThread(@NotNull HgRepository repository, @NotNull List<String> patchNames);

  protected abstract @NlsContexts.ProgressTitle @NotNull String getTitle();
}
