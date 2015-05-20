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
package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.keymap.KeyMapBundle;
import com.intellij.openapi.keymap.KeymapExtension;
import com.intellij.openapi.keymap.KeymapGroup;
import com.intellij.openapi.keymap.KeymapGroupFactory;
import com.intellij.openapi.keymap.impl.ui.ActionsTreeUtil;
import com.intellij.openapi.keymap.impl.ui.Group;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.util.containers.ContainerUtil;

/**
 * @author yole
 */
public class VcsKeymapExtension implements KeymapExtension {
  public KeymapGroup createGroup(final Condition<AnAction> filtered, final Project project) {
    KeymapGroup result = KeymapGroupFactory.getInstance().createGroup(KeyMapBundle.message("version.control.group.title"));

    AnAction[] versionControlsGroups = getActions("VcsGroup");
    AnAction[] keymapGroups = getActions("Vcs.KeymapGroup");

    for (AnAction action : ContainerUtil.concat(versionControlsGroups, keymapGroups)) {
      addAction(result, action, filtered, false);
    }

    AnAction[] generalActions = getActions("VcsGeneral.KeymapGroup");
    for (AnAction action : generalActions) {
      addAction(result, action, filtered, true);
    }

    if (result instanceof Group) {
      ((Group)result).normalizeSeparators();
    }

    return result;
  }

  private static void addAction(KeymapGroup result, AnAction action, Condition<AnAction> filtered, boolean forceNonPopup) {
    if (action instanceof ActionGroup) {
      if (forceNonPopup) {
        AnAction[] actions = getActions((ActionGroup)action);
        for (AnAction childAction : actions) {
          addAction(result, childAction, filtered, true);
        }
      }
      else {
        Group subGroup = ActionsTreeUtil.createGroup((ActionGroup)action, false, filtered);
        if (subGroup.getSize() > 0) {
          result.addGroup(subGroup);
        }
      }
    }
    else if (action instanceof Separator) {
      if (result instanceof Group) {
        ((Group)result).addSeparator();
      }
    }
    else {
      if (filtered == null || filtered.value(action)) {
        String id = action instanceof ActionStub ? ((ActionStub)action).getId() : ActionManager.getInstance().getId(action);
        result.addActionId(id);
      }
    }
  }

  private static AnAction[] getActions(String actionGroup) {
    return getActions((ActionGroup)ActionManager.getInstance().getActionOrStub(actionGroup));
  }

  private static AnAction[] getActions(ActionGroup group) {
    return group instanceof DefaultActionGroup
           ? ((DefaultActionGroup)group).getChildActionsOrStubs()
           : group.getChildren(null);
  }
}