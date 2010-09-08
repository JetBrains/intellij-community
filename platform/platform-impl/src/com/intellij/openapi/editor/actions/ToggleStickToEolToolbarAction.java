/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.Nullable;

/**
 * @author oleg
 */
public class ToggleStickToEolToolbarAction extends ToggleAction {

  public ToggleStickToEolToolbarAction() {
    super();
    final String message = ActionsBundle.message("action.EditorConsoleToggleStickToEndOfOutput.text");
    getTemplatePresentation().setDescription(message);
    getTemplatePresentation().setText(message);
    getTemplatePresentation().setIcon(IconLoader.getIcon("/general/autoscrollToSource.png"));
  }

  @Override
  public boolean isSelected(final AnActionEvent e) {
    final Editor editor = getEditor(e);
    return editor != null && editor.getSettings().isForceScrollToEnd();
  }

  @Nullable
  protected Editor getEditor(final AnActionEvent e) {
    return e.getData(PlatformDataKeys.EDITOR);
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    final Editor editor = getEditor(e);
    if (editor != null) {
      editor.getSettings().setForceScrollToEnd(state);
    }
  }
}