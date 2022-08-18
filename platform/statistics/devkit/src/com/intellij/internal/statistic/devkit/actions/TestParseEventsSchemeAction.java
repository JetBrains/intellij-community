// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.devkit.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class TestParseEventsSchemeAction extends DumbAwareAction {

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getProject();
    if (project != null) {
      final PsiFile fileType = e.getData(CommonDataKeys.PSI_FILE);
      final Editor editor =
        fileType != null && StringUtil.equalsIgnoreCase(fileType.getFileType().getName(), "json") ?
        e.getData(CommonDataKeys.EDITOR) : null;
      new TestParseEventsSchemeDialog(project, editor).show();
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    boolean enabled = isEnabled(e.getProject());
    e.getPresentation().setEnabledAndVisible(enabled);
  }

  private static boolean isEnabled(@Nullable Project project) {
    return project != null && ApplicationManager.getApplication().isInternal();
  }
}