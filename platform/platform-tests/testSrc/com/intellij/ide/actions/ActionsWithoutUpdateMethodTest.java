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
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.util.containers.HashSet;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * @author Konstantin Bulenkov
 */
public class ActionsWithoutUpdateMethodTest extends PlatformTestCase {
  private static final List<String> PLATFORM_WIDE_ACTIONS = Arrays.asList(
    "TestGestureAction",
    "Synchronize",
    "SaveAll",
    "MaintenanceAction",
    "ShowProjectStructureSettings"
  );

  public void testActionsWithShortcuts() throws Exception {
    Set<String> ids = new HashSet<>();
    for (String id : ((ActionManagerImpl)ActionManager.getInstance()).getActionIds()) {
      for (Keymap keymap : KeymapManagerEx.getInstanceEx().getAllKeymaps()) {
        if (keymap.getShortcuts(id).length > 0 && !PLATFORM_WIDE_ACTIONS.contains(id)) {
          ids.add(id);
        }
      }
    }

    ActionManager mgr = ActionManager.getInstance();
    ArrayList<AnAction> failed = new ArrayList<>();
    for (String id : ids) {
      AnAction action = mgr.getAction(id);
      if (action == null) {
        System.out.println("Can't find action: " + id);
        continue;
      }
      Method updateMethod = action.getClass().getMethod("update", AnActionEvent.class);
      if (updateMethod.getDeclaringClass() == AnAction.class) {
        failed.add(action);
      }
    }
    for (AnAction action : failed) {
      System.out.println(action + " ID: " + mgr.getId(action) + " Class: " + action.getClass());
    }

    assertEmpty("The following actions have shortcuts, but don't have update() method redefined", failed);
  }
}
