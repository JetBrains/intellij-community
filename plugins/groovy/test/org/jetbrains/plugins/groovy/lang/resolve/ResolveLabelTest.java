// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrLabeledStatement;
import org.jetbrains.plugins.groovy.util.TestUtils;

/**
 * @author Maxim.Medvedev
 */

public class ResolveLabelTest extends GroovyResolveTestCase {
  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath()+"resolve/label";
  }

  public void testLabelResolve() {
    final PsiReference ref = configureByFile(getTestName(true)+"/"+getTestName(false) + ".groovy");
    final PsiElement resolved = ref.resolve();
    assertNotNull(resolved);
    assertInstanceOf(resolved, GrLabeledStatement.class);
  }

  public void testLabelResolve2() {
    final PsiReference ref = configureByFile(getTestName(true)+"/"+getTestName(false) + ".groovy");
    final PsiElement resolved = ref.resolve();
    assertNotNull(resolved);
    assertInstanceOf(resolved, GrLabeledStatement.class);
  }

  public void testLabelNotResolved() {
    final PsiReference ref = configureByFile(getTestName(true)+"/"+getTestName(false) + ".groovy");
    final PsiElement resolved = ref.resolve();
    assertNull(resolved);
  }
}
