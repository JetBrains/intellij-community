// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.actions;

import com.intellij.application.options.EditorFontsConstants;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorBundle;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * @author Konstantin Bulenkov
 */
public abstract class ChangeEditorFontSizeAction extends AnAction implements DumbAware {
  private final float myStep;
  private final boolean myGlobal;

  protected ChangeEditorFontSizeAction(@NotNull Supplier<String> text, float increaseStep, boolean global) {
    super(text);
    myStep = increaseStep;
    myGlobal = global;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final EditorImpl editor = getEditor(e);
    if (editor != null) {
      final float size = editor.getFontSize2D() + myStep;
      if (size >= 8 && size <= EditorFontsConstants.getMaxEditorFontSize()) {
        editor.setFontSize(size);
        if (myGlobal) {
          editor.adjustGlobalFontSize(size);
        }
      }
    }
  }

  @Nullable
  private static EditorImpl getEditor(@NotNull AnActionEvent e) {
    final Editor editor = e.getData(CommonDataKeys.EDITOR);
    if (editor instanceof EditorImpl) {
      return (EditorImpl)editor;
    }
    return null;
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(getEditor(e) != null);
  }

  public static class IncreaseEditorFontSize extends ChangeEditorFontSizeAction {
    protected IncreaseEditorFontSize() {
      super(EditorBundle.messagePointer("increase.editor.font"), 1, false);
    }
  }

  public static class DecreaseEditorFontSize extends ChangeEditorFontSizeAction {
    protected DecreaseEditorFontSize() {
      super(EditorBundle.messagePointer("decrease.editor.font"), -1, false);
    }
  }

  public static class IncreaseEditorFontSizeGlobal extends ChangeEditorFontSizeAction {
    protected IncreaseEditorFontSizeGlobal() {
      super(EditorBundle.messagePointer("increase.all.editors.font"), 1, true);
    }
  }

  public static class DecreaseEditorFontSizeGlobal extends ChangeEditorFontSizeAction {
    protected DecreaseEditorFontSizeGlobal() {
      super(EditorBundle.messagePointer("decrease.all.editors.font"), -1, true);
    }
  }
}
