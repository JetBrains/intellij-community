/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: May 14, 2002
 * Time: 7:40:40 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.Nullable;

public abstract class EditorToggleDecorationAction extends ToggleAction implements DumbAware {
  @Override
  public final void setSelected(AnActionEvent e, boolean state) {
    final Editor editor = getEditor(e);
    assert editor != null;
    setOption(editor, state);
    editor.getComponent().repaint();
  }

  @Override
  public final boolean isSelected(AnActionEvent e) {
    Editor editor = getEditor(e);
    return editor != null && getOption(editor);
  }

  @Nullable
  private static Editor getEditor(AnActionEvent e) {
    return e.getData(CommonDataKeys.EDITOR_EVEN_IF_INACTIVE);
  }

  @Override
  public final void update(AnActionEvent e) {
    super.update(e);
    e.getPresentation().setEnabled(getEditor(e) != null);
  }
  
  protected abstract void setOption(Editor editor, boolean state);
  protected abstract boolean getOption(Editor editor);
}
