/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.internal.ui;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.popup.JBPopupFactory;

/**
 * @author Konstantin Bulenkov
 */
public class ShowMainMenuAction extends DumbAwareAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    ActionGroup mainMenu = (ActionGroup)ActionManager.getInstance().getAction(IdeActions.GROUP_MAIN_MENU);
    JBPopupFactory.getInstance()
      .createActionGroupPopup("Main Menu", mainMenu,
                              e.getDataContext(),
                              false, true, false, null, 30, null)
      .showInBestPositionFor(e.getDataContext());
  }
}
