// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.injection;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixTestCase;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.source.tree.injected.MyTestInjector;
import com.intellij.testFramework.ParsingTestCase;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.util.TestUtils;

/**
 * Checks the PSI of a language fragment injected into a Groovy comment.
 */
public class InjectedGroovyParsingTest extends LightQuickFixTestCase {
  @Override
  public @NotNull String getTestDataPath() {
    return TestUtils.getAbsoluteTestDataPath() + "injected/";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    new MyTestInjector(getPsiManager()).injectAll(getTestRootDisposable());
  }

  public void testCommentInjectionInGroovy() throws Exception {
    configureByFile(getTestName(false) + ".groovy");
    ParsingTestCase.doCheckResult(getTestDataPath(), getTestName(false) + ".txt", toParseTreeText(getFile()));
  }

  private @NotNull String toParseTreeText(@NotNull PsiFile file) {
    return String.join("", JBIterable.from(file.getViewProvider().getAllFiles()).map(
      root -> DebugUtil.psiToString(root, true, false, (psiElement, consumer) ->
        InjectedLanguageManager.getInstance(getProject()).enumerate(psiElement, (injectedPsi, places) -> consumer.consume(injectedPsi)))));
  }
}
