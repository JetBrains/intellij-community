/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.editor.actions;

import com.intellij.application.options.OptionsConstants;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorBundle;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.Nullable;

/**
 * @author Konstantin Bulenkov
 */
public abstract class ChangeEditorFontSizeAction extends AnAction implements DumbAware {
  private final int myStep;

  protected ChangeEditorFontSizeAction(@Nullable String text, int increaseStep) {
    super(text);
    myStep = increaseStep;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final EditorImpl editor = getEditor(e);
    if (editor != null) {
      final int size = editor.getFontSize() + myStep;
      if (size >= 8 && size <= OptionsConstants.MAX_EDITOR_FONT_SIZE) {
        editor.setFontSize(size);
      }
    }
  }

  @Nullable
  private static EditorImpl getEditor(AnActionEvent e) {
    final Editor editor = e.getData(CommonDataKeys.EDITOR);
    if (editor instanceof EditorImpl) {
      return (EditorImpl)editor;
    }
    return null;
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(getEditor(e) != null);
  }

  public static class IncreaseEditorFontSize extends ChangeEditorFontSizeAction {
    protected IncreaseEditorFontSize() {
      super(EditorBundle.message("increase.editor.font"), 1);
    }
  }

  public static class DecreaseEditorFontSize extends ChangeEditorFontSizeAction {
    protected DecreaseEditorFontSize() {
      super(EditorBundle.message("decrease.editor.font"), -1);
    }
  }
}
