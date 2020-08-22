// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.testFramework.LightPlatformTestCase;

import java.lang.reflect.Method;
import java.util.*;

/**
 * @author Konstantin Bulenkov
 */
public class ActionsWithoutUpdateMethodTest extends LightPlatformTestCase {
  private static final List<String> PLATFORM_WIDE_ACTIONS = Arrays.asList(
    "TestGestureAction",
    "Synchronize",
    "SaveAll",
    "MaintenanceAction",
    "ShowProjectStructureSettings",
    "FocusEditor",
    "SearchEverywhere",
    "Terminal.SmartCommandExecution.Run",
    "Terminal.SmartCommandExecution.Debug"
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
        fail("Can't find action: " + id);
        continue;
      }
      Method updateMethod = action.getClass().getMethod("update", AnActionEvent.class);
      if (updateMethod.getDeclaringClass() == AnAction.class) {
        failed.add(action);
      }
    }
    for (AnAction action : failed) {
      System.err.println(action + " ID: " + mgr.getId(action) + " Class: " + action.getClass());
    }

    assertEmpty("The following actions have shortcuts, but don't have update() method redefined", failed);
  }
}
