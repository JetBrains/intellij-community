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
package com.intellij.lang.ant.config.impl.configuration;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.ui.ReorderableListController;

import javax.swing.*;
import java.util.ArrayList;

public class ReorderableListToolbar<T> extends ReorderableListController<T> {
  private final ArrayList<ActionDescription> myActions = new ArrayList<>();

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
