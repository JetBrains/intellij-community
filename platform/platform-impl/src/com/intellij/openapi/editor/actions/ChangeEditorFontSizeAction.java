// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.actions;

import com.intellij.application.options.EditorFontsConstants;
import com.intellij.ide.ui.UISettingsUtils;
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
      float step = myStep;
      if (myGlobal) {
        step *= UISettingsUtils.getInstance().getCurrentIdeScale();
      }
      final float size = editor.getFontSize2D() + step;
      final float unscaledSize = UISettingsUtils.scaleFontSize(size, 1 / UISettingsUtils.getInstance().getCurrentIdeScale());
      if (unscaledSize >= 8 && unscaledSize <= EditorFontsConstants.getMaxEditorFontSize()) {
        editor.setFontSize(size);
        if (myGlobal) {
          editor.adjustGlobalFontSize(unscaledSize);
        }
      }
    }
  }

  private static @Nullable EditorImpl getEditor(@NotNull AnActionEvent e) {
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

  public static final class IncreaseEditorFontSize extends ChangeEditorFontSizeAction {
    private IncreaseEditorFontSize() {
      super(EditorBundle.messagePointer("increase.editor.font"), 1, false);
    }
  }

  public static final class DecreaseEditorFontSize extends ChangeEditorFontSizeAction {
    private DecreaseEditorFontSize() {
      super(EditorBundle.messagePointer("decrease.editor.font"), -1, false);
    }
  }

  public static final class IncreaseEditorFontSizeGlobal extends ChangeEditorFontSizeAction {
    private IncreaseEditorFontSizeGlobal() {
      super(EditorBundle.messagePointer("increase.all.editors.font"), 1, true);
    }
  }

  public static final class DecreaseEditorFontSizeGlobal extends ChangeEditorFontSizeAction {
    private DecreaseEditorFontSizeGlobal() {
      super(EditorBundle.messagePointer("decrease.all.editors.font"), -1, true);
    }
  }
}
