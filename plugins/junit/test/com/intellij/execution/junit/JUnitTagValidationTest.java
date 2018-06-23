// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.junit;

import com.intellij.psi.PsiElementFactory;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

public class JUnitTagValidationTest extends LightCodeInsightFixtureTestCase {
  /**
   * {@link TestTags#parseAsJavaExpression(java.lang.String[])}
   */
  public void testValidTags() {
    PsiElementFactory factory = myFixture.getJavaFacade().getElementFactory();
    factory.createExpressionFromText("1+2", null);
    factory.createExpressionFromText("!1+2", null);
  }
}
