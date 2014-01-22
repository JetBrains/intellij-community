/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.ex.QuickList;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;

public class ActionsTreeTest extends LightPlatformCodeInsightTestCase {
  private static final String ACTION_WITHOUT_TEXT_AND_DESCRIPTION = "EditorDeleteToLineEnd";
  private static final String ACTION_WITH_TEXT_ONLY = "EditorCutLineEnd";
  private static final String ACTION_WITH_TEXT_AND_DESCRIPTION = "EditorHungryBackSpace";

  private ActionsTree myActionsTree;

  public void setUp() throws Exception {
    super.setUp();
    myActionsTree = new ActionsTree();
    myActionsTree.reset(KeymapManager.getInstance().getActiveKeymap(), new QuickList[0]);
  }

  public void testVariousActionsArePresent() {
    doTest(null,
           ACTION_WITHOUT_TEXT_AND_DESCRIPTION,
           ACTION_WITH_TEXT_ONLY,
           ACTION_WITH_TEXT_AND_DESCRIPTION);
  }

  public void testFiltering() {
    doTest("Editor",
           // all below actions should still be present, as they contain 'Editor' in their actionId
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
}
