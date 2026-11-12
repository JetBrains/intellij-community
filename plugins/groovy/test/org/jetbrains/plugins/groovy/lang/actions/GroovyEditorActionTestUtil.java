// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.actions;

import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;

/**
 * The helpers of the Groovy editor action tests.
 */
public final class GroovyEditorActionTestUtil {
  private GroovyEditorActionTestUtil() {
  }

  /**
   * Runs the editor action {@code actionId} on the EDT, and then removes the trailing spaces of the document.
   */
  public static void performAction(CodeInsightTestFixture fixture, String actionId) {
    EdtTestUtil.runInEdtAndWait(() -> {
      fixture.performEditorAction(actionId);
      ((DocumentImpl)fixture.getEditor().getDocument()).stripTrailingSpaces(fixture.getProject());
    });
  }
}
