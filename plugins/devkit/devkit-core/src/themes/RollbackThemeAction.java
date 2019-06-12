// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.themes;

import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.laf.TempUIThemeBasedLookAndFeelInfo;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.impl.EditorColorsManagerImpl;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.ui.AppUIUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Konstantin Bulenkov
 */
public class RollbackThemeAction extends DumbAwareAction {
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
    } else {
      LafManager.getInstance().setCurrentLookAndFeel(feel);
    }
    EditorColorsManagerImpl.schemeChangedOrSwitched();
    AppUIUtil.updateForDarcula(UIUtil.isUnderDarcula());
    LafManager.getInstance().updateUI();
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    UIManager.LookAndFeelInfo feel = LafManager.getInstance().getCurrentLookAndFeel();
    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();

    e.getPresentation().setEnabled(feel instanceof TempUIThemeBasedLookAndFeelInfo
                                   || EditorColorsManagerImpl.isTempScheme(scheme));
  }
}
