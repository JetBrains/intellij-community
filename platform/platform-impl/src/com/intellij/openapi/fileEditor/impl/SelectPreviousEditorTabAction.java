// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

// The only purpose of this action is to serve as placeholder for assigning keyboard shortcuts.
// For actual tab switching code, see EditorComposite constructor.
@ApiStatus.Internal
public final class SelectPreviousEditorTabAction extends AnAction implements DumbAware, ActionRemoteBehaviorSpecification.Frontend {
  @Override
  public void actionPerformed(final @NotNull AnActionEvent e) {
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    // allow plugins to use the same keyboard shortcut
    e.getPresentation().setEnabled(false);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}