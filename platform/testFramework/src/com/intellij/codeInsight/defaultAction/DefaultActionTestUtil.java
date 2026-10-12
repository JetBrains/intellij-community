// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.defaultAction;

import com.intellij.ide.DataManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actionSystem.TypedAction;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

/**
 * Typing checks for a JUnit 5 test.
 * This is the replacement of {@link DefaultActionTestCase}.
 */
@TestOnly
public final class DefaultActionTestUtil {
  private DefaultActionTestUtil() {
  }

  /**
   * Types {@code c} in the editor of {@code fixture} with the {@link TypedAction}.
   * Call it from any thread.
   */
  public static void performAction(@NotNull CodeInsightTestFixture fixture, char c) {
    Editor editor = fixture.getEditor();
    EdtTestUtil.runInEdtAndWait(() -> {
      EditorActionManager.getInstance();
      TypedAction action = TypedAction.getInstance();
      action.actionPerformed(editor, c, DataManager.getInstance().getDataContext());
    });
  }
}
