// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.junit;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiElementFactory;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class JUnitTagValidationTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  /**
   * {@link TestTags#parseAsJavaExpression(java.lang.String[])}
   */
  @Test
  public void testValidTags() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiElementFactory factory = myFixture.getJavaFacade().getElementFactory();
    ReadAction.runBlocking(() -> {
      factory.createExpressionFromText("1+2", null);
      factory.createExpressionFromText("!1+2", null);
      factory.createExpressionFromText("!1+ 2", null);
    });
  }
}
