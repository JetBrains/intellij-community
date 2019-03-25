// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.inspector;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class ApplyWindowSizeAction extends DumbAwareAction {
  public ApplyWindowSizeAction() {
    setEnabledInModalContext(true);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Component owner = IdeFocusManager.findInstance().getFocusOwner();
    if (owner != null) {
      Window window = UIUtil.getParentOfType(Window.class, owner);
      if (window != null) {
        int w = ConfigureCustomSizeAction.CustomSizeModel.INSTANCE.getWidth();
        int h = ConfigureCustomSizeAction.CustomSizeModel.INSTANCE.getHeight();
        window.setMinimumSize(new Dimension(w, h));
        window.setSize(w, h);
      }
    }
  }
}
