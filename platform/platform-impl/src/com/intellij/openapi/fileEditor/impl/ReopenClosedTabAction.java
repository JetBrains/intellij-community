// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.ui.ComponentUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class ReopenClosedTabAction extends AnAction {
  public ReopenClosedTabAction() {
    super("Reopen Closed Tab");
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final EditorWindow window = getEditorWindow(e);
    if (window != null) {
      window.restoreClosedTab();
    }
  }

  @Nullable
  private static EditorWindow getEditorWindow(@NotNull AnActionEvent e) {
    final Component component = e.getData(PlatformDataKeys.CONTEXT_COMPONENT);
    if (component != null) {
      final EditorsSplitters splitters =
        ComponentUtil.getParentOfType((Class<? extends EditorsSplitters>)EditorsSplitters.class, component);
      if (splitters != null) {
        return splitters.getCurrentWindow();
      }
    }
    return null;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    final EditorWindow window = getEditorWindow(e);
    e.getPresentation().setEnabledAndVisible(window != null && window.hasClosedTabs());
  }
}
