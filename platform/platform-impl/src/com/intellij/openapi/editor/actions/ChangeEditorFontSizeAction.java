// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.actions;

import com.intellij.application.options.EditorFontsConstants;
import com.intellij.ide.ui.UISettingsUtils;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Konstantin Bulenkov
 */
@ApiStatus.Internal
public abstract class ChangeEditorFontSizeAction extends AnAction implements DumbAware, ActionRemoteBehaviorSpecification.Frontend {
  private final float myStep;
  private final boolean myGlobal;

  protected ChangeEditorFontSizeAction(float increaseStep, boolean global) {
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
    var editor = getEditor(e);
    var strategy = editor == null ? null : editor.getUserData(ChangeEditorFontSizeStrategy.KEY);
    e.getPresentation().setEnabled(editor != null && (strategy == null || !strategy.getOverridesChangeFontSizeActions()));
  }

  public static final class IncreaseEditorFontSize extends ChangeEditorFontSizeAction {
    private IncreaseEditorFontSize() {
      super(1, false);
    }
  }

  public static final class DecreaseEditorFontSize extends ChangeEditorFontSizeAction {
    private DecreaseEditorFontSize() {
      super(-1, false);
    }
  }

  public static final class IncreaseEditorFontSizeGlobal extends ChangeEditorFontSizeAction {
    private IncreaseEditorFontSizeGlobal() {
      super(1, true);
    }
  }

  public static final class DecreaseEditorFontSizeGlobal extends ChangeEditorFontSizeAction {
    private DecreaseEditorFontSizeGlobal() {
      super(-1, true);
    }
  }
}
