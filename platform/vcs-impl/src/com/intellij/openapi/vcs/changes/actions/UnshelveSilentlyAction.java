// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Objects;

import static com.intellij.openapi.vcs.changes.shelf.ShelvedChangesViewManager.*;

@ApiStatus.Internal
public final class UnshelveSilentlyAction extends DumbAwareAction implements ActionPromoter {

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = Objects.requireNonNull(getEventProject(e));
    FileDocumentManager.getInstance().saveAllDocuments();
    DataContext dataContext = e.getDataContext();
    ShelveChangesManager.getInstance(project).
      unshelveSilentlyAsynchronously(project, getShelvedLists(dataContext), getShelveChanges(dataContext),
                                     getBinaryShelveChanges(dataContext), null);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(getEventProject(e) != null && !getShelvedLists(e.getDataContext()).isEmpty());
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public @Unmodifiable @Nullable List<AnAction> promote(@NotNull @Unmodifiable List<? extends AnAction> actions,
                                                        @NotNull DataContext context) {
    if (!getShelvedLists(context).isEmpty()) {
      return List.of(this);
    }
    return null;
  }
}
