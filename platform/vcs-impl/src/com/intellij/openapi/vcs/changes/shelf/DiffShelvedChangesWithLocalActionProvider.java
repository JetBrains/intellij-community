// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.shelf;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.AnActionExtensionProvider;
import com.intellij.openapi.vcs.VcsBundle;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class DiffShelvedChangesWithLocalActionProvider implements AnActionExtensionProvider {
  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public boolean isActive(@NotNull AnActionEvent e) {
    return e.getData(ShelvedChangesViewManager.SHELVED_CHANGES_TREE) != null;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setDescription(VcsBundle.messagePointer("action.presentation.DiffShelvedChangesWithLocalActionProvider.description"));
    e.getPresentation().setEnabled(DiffShelvedChangesActionProvider.isEnabled(e.getDataContext()));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    DiffShelvedChangesActionProvider.showShelvedChangesDiff(e.getDataContext(), true);
  }
}
