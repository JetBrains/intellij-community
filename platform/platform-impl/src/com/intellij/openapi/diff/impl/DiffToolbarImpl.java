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
package com.intellij.openapi.diff.impl;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diff.DiffRequest;
import com.intellij.openapi.diff.DiffToolbar;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class DiffToolbarImpl implements DiffToolbar {
  private final DefaultActionGroup myActionGroup  = new DefaultActionGroup();
  private ActionToolbar myActionToolbar;
  private JComponent myTargetComponent;

  public void registerKeyboardActions(JComponent registerActionsTo) {
    AnAction[] actions = getAllActions();
    for (AnAction action : actions) {
      action.registerCustomShortcutSet(action.getShortcutSet(), registerActionsTo);
    }
  }

  public AnAction[] getAllActions() {
    return myActionGroup.getChildren(null);
  }

  public boolean removeActionById(String actionId) {
    AnAction[] allActions = getAllActions();
    for (AnAction action : allActions) {
      if (actionId.equals(ActionManager.getInstance().getId(action))) {
        removeAction(action);
        return true;
      }
    }
    return false;
  }

  public void removeAction(AnAction action) {
    myActionGroup.remove(action);
    updateToolbar();
  }

  public JComponent getComponent() {
    if (myActionToolbar == null) {
      myActionToolbar = ActionManager.getInstance().
        createActionToolbar(ActionPlaces.UNKNOWN, myActionGroup, true);
      myActionToolbar.setTargetComponent(myTargetComponent);
    }
    return myActionToolbar.getComponent();
  }

  public void addAction(@NotNull AnAction action) {
    myActionGroup.add(action);
    updateToolbar();
  }

  private void updateToolbar() {
    if (myActionToolbar != null) myActionToolbar.updateActionsImmediately();
  }

  public void addSeparator() {
    myActionGroup.addSeparator();
    updateToolbar();
  }

  public void reset(@NotNull DiffRequest.ToolbarAddons toolBar) {
    myActionGroup.removeAll();
    toolBar.customize(this);
  }

  public void setTargetComponent(JComponent component) {
    myTargetComponent = component;
    if (myActionToolbar != null) {
      myActionToolbar.setTargetComponent(component);
    }
  }
}
