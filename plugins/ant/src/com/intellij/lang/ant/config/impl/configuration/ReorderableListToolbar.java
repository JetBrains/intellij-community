package com.intellij.lang.ant.config.impl.configuration;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.ui.ReorderableListController;

import javax.swing.*;
import java.util.ArrayList;

public class ReorderableListToolbar<T> extends ReorderableListController<T> {
  private final ArrayList<ActionDescription> myActions = new ArrayList<ActionDescription>();

  public ReorderableListToolbar(final JList list) {
    super(list);
  }

  public void addActionDescription(final ActionDescription description) {
    myActions.add(description);
  }

  public DefaultActionGroup createActionGroup() {
    final DefaultActionGroup actionGroup = new DefaultActionGroup();
    for (final ActionDescription actionDescription : myActions) {
      actionGroup.add(actionDescription.createAction(getList()));
    }
    return actionGroup;
  }

  public ActionToolbar createActionToolbar(final boolean horizontal) {
    return ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, createActionGroup(), horizontal);
  }
}
