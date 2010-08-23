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

/**
 * Provides common functionality for <code>'toggle soft wraps usage'</code> actions.
 *
 * @author Denis Zhdanov
 * @since Aug 23, 2010 11:33:35 AM
 */
public abstract class AbstractToggleUseSoftWrapsAction extends ToggleAction {

  @Override
  public boolean isSelected(AnActionEvent e) {
    Editor editor = getEditor(e);
    return editor != null && editor.getSettings().isUseSoftWraps();
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    final Editor editor = getEditor(e);
    if (editor != null) {
      editor.getSettings().setUseSoftWraps(state);
    }
  }

  @Nullable
  protected Editor getEditor(AnActionEvent e) {
    return e.getData(PlatformDataKeys.EDITOR);
  }
}
