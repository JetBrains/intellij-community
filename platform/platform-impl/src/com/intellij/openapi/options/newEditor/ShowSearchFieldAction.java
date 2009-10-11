/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.options.newEditor;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Toggleable;
import com.intellij.openapi.ui.ShadowAction;
import com.intellij.openapi.util.IconLoader;

public class ShowSearchFieldAction extends AnAction implements Toggleable {

  private final ShadowAction myShadow;
  private final OptionsEditor myEditor;


  public ShowSearchFieldAction(OptionsEditor editor) {
    myEditor = editor;
    myShadow = new ShadowAction(this, ActionManager.getInstance().getAction("Find"), editor);
  }

  @Override
  public void update(final AnActionEvent e) {
    super.update(e);
    e.getPresentation().setIcon(IconLoader.getIcon("/actions/find.png"));
    e.getPresentation().putClientProperty(SELECTED_PROPERTY, myEditor.isFilterFieldVisible());
  }

  public void actionPerformed(final AnActionEvent e) {
    if (myEditor.getContext().isHoldingFilter()) {
      myEditor.setFilterFieldVisible(true, true, true);
    } else {
      myEditor.setFilterFieldVisible(!myEditor.isFilterFieldVisible(), true, true);
    }
  }
}
