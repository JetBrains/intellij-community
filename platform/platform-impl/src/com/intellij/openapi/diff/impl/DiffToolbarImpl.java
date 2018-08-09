// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
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

  @Override
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
        createActionToolbar("Diff", myActionGroup, true);
      myActionToolbar.setTargetComponent(myTargetComponent);
    }
    return myActionToolbar.getComponent();
  }

  @Override
  public void addAction(@NotNull AnAction action) {
    myActionGroup.add(action);
    updateToolbar();
  }

  private void updateToolbar() {
    if (myActionToolbar != null) myActionToolbar.updateActionsImmediately();
  }

  @Override
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
