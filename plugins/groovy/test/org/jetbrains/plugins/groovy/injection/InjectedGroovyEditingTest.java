// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.injection;

import org.jetbrains.plugins.groovy.util.TestUtils;
import com.intellij.psi.impl.source.tree.injected.MyTestInjector;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixTestCase;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.psi.PsiDocumentManager;
import org.jetbrains.annotations.NotNull;

public class InjectedGroovyEditingTest extends LightQuickFixTestCase {
  @Override
  public @NotNull String getTestDataPath() {
    return TestUtils.getAbsoluteTestDataPath();
  }
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    new MyTestInjector(getPsiManager()).injectAll(getTestRootDisposable());
  }

  public void testBackspaceAfterQuoteInsideStringLiteral2() {
    configureByFile("injected/groovy.java");
    assertTrue(getEditor() instanceof EditorWindow);
    assertEmpty(highlightErrors());

    bringRealEditorBack();
    String oldText = getFile().getText();

    type("\"");
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    backspace();
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    assertEmpty(highlightErrors());
    assertEquals(oldText, getFile().getText());
  }
}
