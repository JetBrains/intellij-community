// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.testFramework.fixtures.EditorMouseFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EditorLastActionTrackerTest extends BasePlatformTestCase {
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
    try {
      EditorActionManager.getInstance().setActionHandler(SAMPLE_ACTION, mySavedHandler);
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      myTracker = null;
      mySavedHandler = null;
      super.tearDown();
    }
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
