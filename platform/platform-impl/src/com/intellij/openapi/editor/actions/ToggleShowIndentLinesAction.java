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

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.Nullable;

public class ToggleShowIndentLinesAction extends ToggleAction {

  public void setSelected(AnActionEvent e, boolean state) {
    final Editor editor = getEditor(e);
    assert editor != null;
    editor.getSettings().setIndentGuidesShown(state);
  }

  public boolean isSelected(AnActionEvent e) {
    final Editor editor = getEditor(e);
    return editor != null && editor.getSettings().isIndentGuidesShown();
  }

  @Nullable
  private static Editor getEditor(AnActionEvent e) {
    return e.getData(PlatformDataKeys.EDITOR);
  }

  public void update(AnActionEvent e){
    super.update(e);

    if (getEditor(e) == null) {
      e.getPresentation().setEnabled(false);
      e.getPresentation().setVisible(false);
    } else {
      e.getPresentation().setEnabled(true);
      e.getPresentation().setVisible(true);
    }
  }
}
