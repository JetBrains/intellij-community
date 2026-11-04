// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ex.ToolbarLabelAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.platform.vcs.impl.shared.VcsMappingsHolder;
import com.intellij.platform.vcs.impl.shared.VcsPresentation;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class VcsToolbarLabelAction extends ToolbarLabelAction {
  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);

    Project project = e.getProject();
    e.getPresentation().setVisible(project != null && VcsMappingsHolder.getInstance(project).hasActiveVcss());
    e.getPresentation().setText(getConsolidatedVcsName(project));
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  private static @NlsContexts.Label String getConsolidatedVcsName(@Nullable Project project) {
    String name = VcsBundle.message("vcs.common.labels.vcs");
    if (project != null) {
      VcsKey vcsKey = VcsMappingsHolder.getInstance(project).getSingleActiveVcs();
      if (vcsKey == null) return name;
      VcsPresentation presentation = VcsGroupsActionGroupKt.findVcsPresentation(project, vcsKey);
      if (presentation == null) return name;
      name = presentation.getShortNameWithMnemonic() + ":";
    }
    return name;
  }
}
