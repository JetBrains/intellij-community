// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector;

import com.intellij.codeInsight.lookup.LookupEx;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.ComponentUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
final class ApplyWindowSizeAction extends DumbAwareAction {
  ApplyWindowSizeAction() {
    setEnabledInModalContext(true);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Component owner = IdeFocusManager.findInstance().getFocusOwner();
    Editor editor = e.getData(CommonDataKeys.EDITOR);
    Project project = e.getProject();
    Window window = null;
    if (owner == null) {
      return;
    }

    if (editor != null && project != null) {
      LookupEx lookup = LookupManager.getInstance(project).getActiveLookup();
      if (lookup != null) {
        window = ComponentUtil.getParentOfType((Class<? extends Window>)Window.class, lookup.getComponent());
      }
    }

    if (window == null) {
      window = ComponentUtil.getParentOfType((Class<? extends Window>)Window.class, owner);
    }
    if (window != null) {
      int w = ConfigureCustomSizeAction.CustomSizeModel.INSTANCE.getWidth();
      int h = ConfigureCustomSizeAction.CustomSizeModel.INSTANCE.getHeight();
      window.setMinimumSize(new Dimension(w, h));
      window.setSize(w, h);
    }
  }
}
