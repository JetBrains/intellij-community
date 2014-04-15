/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.keymap.impl.ui;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.QuickList;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.keymap.impl.KeymapManagerImpl;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ActionsTreeTest extends LightPlatformCodeInsightTestCase {
  private static final String ACTION_WITHOUT_TEXT_AND_DESCRIPTION = "DummyWithoutTextAndDescription";
  private static final String ACTION_WITH_TEXT_ONLY = "DummyWithTextOnly";
  private static final String ACTION_WITH_TEXT_AND_DESCRIPTION = "DummyWithTextAndDescription";
  private static final String EXISTENT_ACTION = "DummyExistent";
  private static final String NON_EXISTENT_ACTION = "DummyNonExistent";
  private static final String ACTION_WITH_USE_SHORTCUT_OF_EXISTENT_ACTION = "DummyWithUseShortcutOfExistentAction";
  private static final String ACTION_WITH_USE_SHORTCUT_OF_NON_EXISTENT_ACTION = "DummyWithUseShortcutOfNonExistentAction";

  private AnAction myActionWithoutTextAndDescription;
  private AnAction myActionWithTextOnly;
  private AnAction myActionWithTextAndDescription;
  private AnAction myActionExistent;
  private AnAction myActionWithUseShortcutOfExistent;
  private AnAction myActionWithUseShortcutOfNonExistent;
  private ActionsTree myActionsTree;

  public void setUp() throws Exception {
    super.setUp();
    // create dummy actions
    myActionWithoutTextAndDescription = new MyAction(null, null);
    myActionWithTextOnly = new MyAction("some text", null);
    myActionWithTextAndDescription = new MyAction("text", "description");
    myActionExistent = new MyAction("text", "description");
    myActionWithUseShortcutOfExistent = new MyAction("text", "description");
    myActionWithUseShortcutOfNonExistent = new MyAction("text", "description");
    ActionManager actionManager = ActionManager.getInstance();
    actionManager.registerAction(ACTION_WITHOUT_TEXT_AND_DESCRIPTION, myActionWithoutTextAndDescription);
    actionManager.registerAction(ACTION_WITH_TEXT_ONLY, myActionWithTextOnly);
    actionManager.registerAction(ACTION_WITH_TEXT_AND_DESCRIPTION, myActionWithTextAndDescription);
    actionManager.registerAction(EXISTENT_ACTION, myActionExistent);
    actionManager.registerAction(ACTION_WITH_USE_SHORTCUT_OF_EXISTENT_ACTION, myActionWithUseShortcutOfExistent);
    actionManager.registerAction(ACTION_WITH_USE_SHORTCUT_OF_NON_EXISTENT_ACTION, myActionWithUseShortcutOfNonExistent);

    KeymapManagerEx.getInstanceEx().bindShortcuts(EXISTENT_ACTION, ACTION_WITH_USE_SHORTCUT_OF_EXISTENT_ACTION);
    KeymapManagerEx.getInstanceEx().bindShortcuts(NON_EXISTENT_ACTION, ACTION_WITH_USE_SHORTCUT_OF_NON_EXISTENT_ACTION);
      
    DefaultActionGroup group = (DefaultActionGroup)actionManager.getAction(IdeActions.GROUP_EDITOR);
    group.addAll(myActionWithoutTextAndDescription, myActionWithTextOnly, myActionWithTextAndDescription,
                 myActionExistent, myActionWithUseShortcutOfExistent, myActionWithUseShortcutOfNonExistent);
    // populate action tree
    myActionsTree = new ActionsTree();
    myActionsTree.reset(KeymapManager.getInstance().getActiveKeymap(), new QuickList[0]);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      ActionManager actionManager = ActionManager.getInstance();
      DefaultActionGroup group = (DefaultActionGroup)actionManager.getAction(IdeActions.GROUP_EDITOR);
      group.remove(myActionWithoutTextAndDescription);
      group.remove(myActionWithTextOnly);
      group.remove(myActionWithTextAndDescription);
      group.remove(myActionExistent);
      group.remove(myActionWithUseShortcutOfExistent);
      group.remove(myActionWithUseShortcutOfNonExistent);
      actionManager.unregisterAction(ACTION_WITHOUT_TEXT_AND_DESCRIPTION);
      actionManager.unregisterAction(ACTION_WITH_TEXT_ONLY);
      actionManager.unregisterAction(ACTION_WITH_TEXT_AND_DESCRIPTION);
      actionManager.unregisterAction(EXISTENT_ACTION);
      actionManager.unregisterAction(ACTION_WITH_USE_SHORTCUT_OF_EXISTENT_ACTION);
      actionManager.unregisterAction(ACTION_WITH_USE_SHORTCUT_OF_NON_EXISTENT_ACTION);

      ((KeymapManagerImpl)KeymapManager.getInstance()).unbindShortcuts(ACTION_WITH_USE_SHORTCUT_OF_EXISTENT_ACTION);
      ((KeymapManagerImpl)KeymapManager.getInstance()).unbindShortcuts(ACTION_WITH_USE_SHORTCUT_OF_NON_EXISTENT_ACTION);
    }
    finally {
      super.tearDown();
    }
  }

  public void testVariousActionsArePresent() {
    doTest(null,
           Arrays.asList(
             ACTION_WITHOUT_TEXT_AND_DESCRIPTION,
             ACTION_WITH_TEXT_ONLY,
             ACTION_WITH_TEXT_AND_DESCRIPTION,
             EXISTENT_ACTION,
             ACTION_WITH_USE_SHORTCUT_OF_NON_EXISTENT_ACTION),
           Arrays.asList(
             NON_EXISTENT_ACTION,
             ACTION_WITH_USE_SHORTCUT_OF_EXISTENT_ACTION
           )
    );
  }

  public void testFiltering() {
    doTest("Dummy",
           // all below actions should still be present, as they contain 'Dummy' in their actionId
           Arrays.asList(
             ACTION_WITHOUT_TEXT_AND_DESCRIPTION,
             ACTION_WITH_TEXT_ONLY,
             ACTION_WITH_TEXT_AND_DESCRIPTION,
             EXISTENT_ACTION,
             ACTION_WITH_USE_SHORTCUT_OF_NON_EXISTENT_ACTION),
           Arrays.asList(
             NON_EXISTENT_ACTION,
             ACTION_WITH_USE_SHORTCUT_OF_EXISTENT_ACTION
           )
    );
  }

  private void doTest(String filter, List<String> idsThatMustBePresent, List<String> idsThatMustNotBePresent) {
    if (filter != null) {
      myActionsTree.filter(filter, new QuickList[0]);
    }

    List<String> missing = new ArrayList<String>();
    List<String> present = new ArrayList<String>();
    for (String actionId : idsThatMustBePresent) {
      if (!myActionsTree.getMainGroup().containsId(actionId)) missing.add(actionId);
    }
    for (String actionId : idsThatMustNotBePresent) {
      if (myActionsTree.getMainGroup().containsId(actionId)) present.add(actionId);
    }
    assertTrue("Missing actions: " + missing + "\nWrongly shown: " + present,
               missing.isEmpty() && present.isEmpty());
  }

  private static class MyAction extends AnAction {
    private MyAction(@Nullable String text, @Nullable String description) {
      super(text, description, null);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
    }
  }
}
