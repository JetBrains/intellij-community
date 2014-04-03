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
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import org.jetbrains.annotations.Nullable;

public class ActionsTreeTest extends LightPlatformCodeInsightTestCase {
  private static final String ACTION_WITHOUT_TEXT_AND_DESCRIPTION = "DummyWithoutTextAndDescription";
  private static final String ACTION_WITH_TEXT_ONLY = "DummyWithTextOnly";
  private static final String ACTION_WITH_TEXT_AND_DESCRIPTION = "DummyWithTextAndDescription";

  private AnAction myActionWithoutTextAndDescription;
  private AnAction myActionWithTextOnly;
  private AnAction myActionWithTextAndDescription;
  private ActionsTree myActionsTree;

  public void setUp() throws Exception {
    super.setUp();
    // create dummy actions
    myActionWithoutTextAndDescription = new MyAction(null, null);
    myActionWithTextOnly = new MyAction("some text", null);
    myActionWithTextAndDescription = new MyAction("text", "description");
    ActionManager actionManager = ActionManager.getInstance();
    actionManager.registerAction(ACTION_WITHOUT_TEXT_AND_DESCRIPTION, myActionWithoutTextAndDescription);
    actionManager.registerAction(ACTION_WITH_TEXT_ONLY, myActionWithTextOnly);
    actionManager.registerAction(ACTION_WITH_TEXT_AND_DESCRIPTION, myActionWithTextAndDescription);
    DefaultActionGroup group = (DefaultActionGroup)actionManager.getAction(IdeActions.GROUP_EDITOR);
    group.addAll(myActionWithoutTextAndDescription, myActionWithTextOnly, myActionWithTextAndDescription);
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
      actionManager.unregisterAction(ACTION_WITHOUT_TEXT_AND_DESCRIPTION);
      actionManager.unregisterAction(ACTION_WITH_TEXT_ONLY);
      actionManager.unregisterAction(ACTION_WITH_TEXT_AND_DESCRIPTION);
    }
    finally {
      super.tearDown();
    }
  }

  public void testVariousActionsArePresent() {
    doTest(null,
           ACTION_WITHOUT_TEXT_AND_DESCRIPTION,
           ACTION_WITH_TEXT_ONLY,
           ACTION_WITH_TEXT_AND_DESCRIPTION);
  }

  public void testFiltering() {
    doTest("Dummy",
           // all below actions should still be present, as they contain 'Dummy' in their actionId
           ACTION_WITHOUT_TEXT_AND_DESCRIPTION,
           ACTION_WITH_TEXT_ONLY,
           ACTION_WITH_TEXT_AND_DESCRIPTION);
  }

  private void doTest(String filter, String... idsThatMustBePresent) {
    if (filter != null) {
      myActionsTree.filter(filter, new QuickList[0]);
    }

    for (String actionId : idsThatMustBePresent) {
      assertTrue(actionId + " is absent", myActionsTree.getMainGroup().containsId(actionId));
    }
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
