// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.themes.actions;

import com.intellij.application.options.colors.ColorAndFontOptions;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.impl.EditorColorsManagerImpl;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
final class EditThemeEditorColorSchemeAction extends DumbAwareAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    if (ApplyThemeAction.applyTempTheme(e)) {
      EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
      if (EditorColorsManagerImpl.Companion.isTempScheme(scheme)) {
        Configurable[] configurables = new ColorAndFontOptions().getConfigurables();
        ApplicationManager.getApplication().invokeLater(() ->
          ShowSettingsUtil.getInstance().showSettingsDialog(e.getProject(), configurables[0].getClass())
        );
      }
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
