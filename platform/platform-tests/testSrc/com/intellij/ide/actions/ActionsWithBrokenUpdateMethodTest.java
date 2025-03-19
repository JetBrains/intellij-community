// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.actionSystem.impl.Utils;
import com.intellij.testFramework.LightPlatformTestCase;

import java.util.ArrayList;
import java.util.Iterator;

public class ActionsWithBrokenUpdateMethodTest extends LightPlatformTestCase {
  public void testActionsUpdateMethods() {
    ActionManagerImpl actionManager = (ActionManagerImpl)ActionManager.getInstance();

    AnActionEvent event1 = new AnActionEvent(null, DataContext.EMPTY_CONTEXT,
                                             ActionPlaces.UNKNOWN, new Presentation(), actionManager, 0);
    AnActionEvent event2 = new AnActionEvent(null, SimpleDataContext.getProjectContext(getProject()),
                                             ActionPlaces.UNKNOWN, new Presentation(), actionManager, 0);
    Utils.initUpdateSession(event1);
    Utils.initUpdateSession(event2);

    ArrayList<String> failed = new ArrayList<>();
    for (Iterator<AnAction> iterator = actionManager.actions(false).iterator(); iterator.hasNext(); ) {
      AnAction action = iterator.next();
      // check invalid getRequiredData usages
      try {
        action.update(event1);
        action.update(event2);

        if (action instanceof ActionGroup group) {
          group.getChildren(null);
          group.getChildren(event1);
          group.getChildren(event2);
        }
      }
      catch (Throwable e) {
        e.printStackTrace();
        failed.add(String.format("%s (%s): %s", actionManager.getId(action), action.getClass().getName(), e.getMessage()));
      }
    }

    assertEmpty("The following actions threw an error with empty DataContext: ", failed);
  }
}
