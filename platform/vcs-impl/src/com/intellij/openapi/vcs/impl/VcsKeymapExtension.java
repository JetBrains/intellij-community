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

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.keymap.KeyMapBundle;
import com.intellij.openapi.keymap.KeymapExtension;
import com.intellij.openapi.keymap.KeymapGroup;
import com.intellij.openapi.keymap.KeymapGroupFactory;
import com.intellij.openapi.keymap.impl.ui.ActionsTreeUtil;
import com.intellij.openapi.keymap.impl.ui.Group;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;

/**
 * @author yole
 */
public class VcsKeymapExtension implements KeymapExtension {
  public KeymapGroup createGroup(final Condition<AnAction> filtered, final Project project) {
    KeymapGroup result = KeymapGroupFactory.getInstance().createGroup(KeyMapBundle.message("version.control.group.title"));
    ActionGroup versionControls = (ActionGroup)ActionManager.getInstance().getActionOrStub("VcsGroup");

    AnAction[] mainMenuTopGroups = versionControls instanceof DefaultActionGroup
                                   ? ((DefaultActionGroup)versionControls).getChildActionsOrStubs()
                                   : versionControls.getChildren(null);
    for (AnAction action : mainMenuTopGroups) {
      Group subGroup = ActionsTreeUtil.createGroup((ActionGroup)action, false, filtered);
      if (subGroup.getSize() > 0) {
        result.addGroup(subGroup);
      }
    }
    return result;
  }
}