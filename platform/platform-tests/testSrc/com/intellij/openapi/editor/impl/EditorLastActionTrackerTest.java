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
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorLastActionTracker;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.testFramework.fixtures.EditorMouseFixture;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EditorLastActionTrackerTest extends LightPlatformCodeInsightFixtureTestCase {
  public static final String SAMPLE_ACTION = "EditorDelete";
  private final EditorActionHandler myActionHandler = new MyActionHandler();

  private EditorLastActionTracker myTracker;
  private EditorActionHandler mySavedHandler;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myTracker = EditorLastActionTracker.getInstance();
    mySavedHandler = EditorActionManager.getInstance().setActionHandler(SAMPLE_ACTION, myActionHandler);

    myFixture.configureByText(getTestName(true) + ".txt", "doesn't matter");
    myFixture.performEditorAction(SAMPLE_ACTION);
  }

  @Override
  public void tearDown() throws Exception {
    EditorActionManager.getInstance().setActionHandler(SAMPLE_ACTION, mySavedHandler);
    myTracker = null;
    mySavedHandler = null;
    
    super.tearDown();
  }

  public void testLastActionIsAvailable() {
    assertEquals(SAMPLE_ACTION, myTracker.getLastActionId());
  }

  public void testMouseClickClearsLastAction() {
    new EditorMouseFixture((EditorImpl)myFixture.getEditor()).clickAt(0, 1);
    assertNull(myTracker.getLastActionId());
  }

  public void testTypingClearsLastAction() {
    myFixture.type('A');
    assertNull(myTracker.getLastActionId());
  }

  public void testTwoEditors() {
    myFixture.configureByText(getTestName(true) + "-other.txt", "doesn't matter as well");
    myFixture.performEditorAction(SAMPLE_ACTION);
  }

  private class MyActionHandler extends EditorActionHandler {
    @Override
    public void doExecute(@NotNull Editor editor, @Nullable Caret caret, DataContext dataContext) {
      assertNull(myTracker.getLastActionId());
    }
  }
}
