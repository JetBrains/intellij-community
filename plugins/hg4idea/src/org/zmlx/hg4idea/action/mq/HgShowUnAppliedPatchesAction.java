// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.zmlx.hg4idea.action.mq;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.ContentUtilEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgBundle;
import org.zmlx.hg4idea.action.HgAbstractGlobalSingleRepoAction;
import org.zmlx.hg4idea.action.HgActionUtil;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.ui.HgMqUnAppliedPatchesPanel;

import java.util.Collection;
import java.util.Objects;

public final class HgShowUnAppliedPatchesAction extends HgAbstractGlobalSingleRepoAction {
  @Override
  protected void execute(@NotNull Project project, @NotNull Collection<HgRepository> repositories, @Nullable HgRepository selectedRepo) {
    if (selectedRepo != null) {
      showUnAppliedPatches(project, selectedRepo);
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    HgRepository repository = HgActionUtil.getSelectedRepositoryFromEvent(e);
    e.getPresentation().setEnabledAndVisible(repository != null && repository.getRepositoryConfig().isMqUsed());
  }

  public static void showUnAppliedPatches(@NotNull Project project, @NotNull HgRepository selectedRepo) {
    ToolWindow toolWindow = Objects.requireNonNull(ToolWindowManager.getInstance(project).getToolWindow(ChangesViewContentManager.TOOLWINDOW_ID));
    String tabName = selectedRepo.getRoot().getName();
    HgMqUnAppliedPatchesPanel patchesPanel = new HgMqUnAppliedPatchesPanel(selectedRepo);
    ContentUtilEx.addTabbedContent(toolWindow.getContentManager(), patchesPanel,
                                   "MQ",
                                   HgBundle.messagePointer("hg4idea.mq.tab.name"), () -> tabName,
                                   true, patchesPanel);
    toolWindow.activate(null);
  }
}
