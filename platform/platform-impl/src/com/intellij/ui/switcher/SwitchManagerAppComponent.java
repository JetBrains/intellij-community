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
package com.intellij.ui.switcher;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.util.ui.UIUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.Set;

final class SwitchManagerAppComponent extends AnActionListener.Adapter implements KeyEventDispatcher {
  private final Set<AnAction> switchActions = new THashSet<>();

  public SwitchManagerAppComponent(@NotNull ActionManager actionManager) {
    switchActions.add(actionManager.getAction(QuickAccessSettings.SWITCH_UP));
    switchActions.add(actionManager.getAction(QuickAccessSettings.SWITCH_DOWN));
    switchActions.add(actionManager.getAction(QuickAccessSettings.SWITCH_LEFT));
    switchActions.add(actionManager.getAction(QuickAccessSettings.SWITCH_RIGHT));
    switchActions.add(actionManager.getAction(QuickAccessSettings.SWITCH_APPLY));

    actionManager.addAnActionListener(this);
    KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(this);
  }

  @Override
  public void beforeActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event) {
    Project project = event.getProject();
    if (project != null && !project.isDisposed() && !project.isDefault() && !switchActions.contains(action)) {
      SwitchManager.getInstance(project).disposeCurrentSession(false);
    }
  }

  @Override
  public boolean dispatchKeyEvent(@NotNull KeyEvent e) {
    if (!QuickAccessSettings.getInstance().isEnabled()) {
      return false;
    }

    Component frame = UIUtil.findUltimateParent(e.getComponent());
    if (frame instanceof IdeFrame) {
      Project project = ((IdeFrame)frame).getProject();
      if (project != null && !project.isDefault()) {
        return SwitchManager.getInstance(project).dispatchKeyEvent(e);
      }
    }
    return false;
  }
}
