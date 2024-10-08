// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.actions;

import com.intellij.openapi.editor.impl.DocumentImpl;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;

public abstract class GroovyEditorActionTestBase extends LightGroovyTestCase {
  public void performAction(String actionId) {
    myFixture.performEditorAction(actionId);
    ((DocumentImpl)myFixture.getEditor().getDocument()).stripTrailingSpaces(getProject());
  }
}
