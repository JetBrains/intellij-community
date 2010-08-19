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
 * Action that toggles <code>'show soft wraps at editor'</code> option.
 *
 * @author Denis Zhdanov
 * @since Aug 19, 2010 3:15:26 PM
 */
public class ToggleUseSoftWrapsAction extends ToggleAction {

  private final boolean myShowIcon;

  @SuppressWarnings({"UnusedDeclaration"}) // Used implicitly by IoC container
  public ToggleUseSoftWrapsAction() {
    this(false);
  }

  /**
   * This class is assumed to be configured at IDEA components container, i.e. it is expected to have all data configured
   * (name, description, icon). However, there are different use-cases for its appliance. We can point out at least two of them:
   * <pre>
   * <ul>
   *   <li>show icon (e.g. for toolbar-based action);</li>
   *   <li>don't show icon (e.g. for main menu action);</li>
   * </ul>
   * </pre>
   * Hence, it's possible to customize its behavior via given parameter(s).
   *
   * @param showIcon    flag that indicates if current action object should process configured icon if any
   */
  public ToggleUseSoftWrapsAction(boolean showIcon) {
    myShowIcon = showIcon;
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    Editor editor = getEditor(e);
    return editor != null && editor.getSettings().isUseSoftWraps();
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    final Editor editor = getEditor(e);
    assert editor != null;
    editor.getSettings().setUseSoftWraps(state);
  }

  @Override
  public void update(AnActionEvent e){
    super.update(e);
    if (!myShowIcon) {
      e.getPresentation().setIcon(null);
    }

    if (getEditor(e) == null) {
      e.getPresentation().setEnabled(false);
      e.getPresentation().setVisible(false);
    } else {
      e.getPresentation().setEnabled(true);
      e.getPresentation().setVisible(true);
    }
  }

  @Nullable
  private static Editor getEditor(AnActionEvent e) {
    return e.getData(PlatformDataKeys.EDITOR);
  }
}
