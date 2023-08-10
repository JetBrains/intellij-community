// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.themes.actions;

import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.laf.TempUIThemeBasedLookAndFeelInfo;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.impl.EditorColorsManagerImpl;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.ui.AppUIUtil;
import com.intellij.util.ui.StartupUiUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Konstantin Bulenkov
 */
final class RollbackThemeAction extends DumbAwareAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    EditorColorsManagerImpl colorsManager = (EditorColorsManagerImpl)EditorColorsManager.getInstance();
    EditorColorsScheme scheme = colorsManager.getGlobalScheme();
    if (EditorColorsManagerImpl.isTempScheme(scheme)) {
      colorsManager.getSchemeManager().removeScheme(scheme);
      colorsManager.loadState(colorsManager.getState());
    }
    UIManager.LookAndFeelInfo feel = LafManager.getInstance().getCurrentLookAndFeel();
    if (feel instanceof TempUIThemeBasedLookAndFeelInfo) {
      LafManager.getInstance().setCurrentLookAndFeel(((TempUIThemeBasedLookAndFeelInfo)feel).getPreviousLaf());
    }
    else {
      LafManager.getInstance().setCurrentLookAndFeel(feel);
    }

    EditorColorsManagerImpl manager = (EditorColorsManagerImpl)EditorColorsManager.getInstance();
    manager.schemeChangedOrSwitched(manager.getGlobalScheme());
    AppUIUtil.updateForDarcula(StartupUiUtil.isUnderDarcula());
    LafManager.getInstance().updateUI();
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    UIManager.LookAndFeelInfo feel = LafManager.getInstance().getCurrentLookAndFeel();
    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();

    e.getPresentation().setEnabled(feel instanceof TempUIThemeBasedLookAndFeelInfo
                                   || EditorColorsManagerImpl.isTempScheme(scheme));
  }
}
