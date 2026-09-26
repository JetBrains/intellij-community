// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.editor.actions;

import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public abstract class EditorToggleDecorationAction extends ToggleAction implements DumbAware, LightEditCompatible,
                                                                                   ActionRemoteBehaviorSpecification.Frontend {
  @Override
  public final void setSelected(@NotNull AnActionEvent e, boolean state) {
    final Editor editor = getEditor(e);
    assert editor != null;
    setOption(editor, state);
    editor.getComponent().repaint();
  }

  @Override
  public final boolean isSelected(@NotNull AnActionEvent e) {
    Editor editor = getEditor(e);
    return editor != null && getOption(editor);
  }

  private static @Nullable Editor getEditor(@NotNull AnActionEvent e) {
    return e.getData(CommonDataKeys.EDITOR_EVEN_IF_INACTIVE);
  }

  @Override
  public final void update(@NotNull AnActionEvent e) {
    super.update(e);
    e.getPresentation().setEnabled(getEditor(e) != null);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  protected abstract void setOption(Editor editor, boolean state);
  protected abstract boolean getOption(Editor editor);
}
