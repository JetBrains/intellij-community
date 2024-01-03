// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.junit5;

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.execution.junit.JUnit5Framework;
import com.intellij.execution.junit.JUnitUtil;
import com.intellij.psi.PsiClass;
import com.intellij.testFramework.fixtures.*;
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl;
import com.intellij.testFramework.junit5.RunInEdt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RunInEdt(writeIntent = true)
public class JUnit5AcceptanceNoJupiterTest {
  protected JavaCodeInsightTestFixture myFixture;

  @BeforeEach
  void setUp() throws Exception {
    IdeaTestFixtureFactory factory = IdeaTestFixtureFactory.getFixtureFactory();
    TestFixtureBuilder<IdeaProjectTestFixture> fixtureBuilder = factory.createLightFixtureBuilder(new DefaultLightProjectDescriptor(),
                                                                                                  "JUnit5CodeInsightTest");
    final IdeaProjectTestFixture fixture = fixtureBuilder.getFixture();
    myFixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(fixture, new LightTempDirTestFixtureImpl(true));
    myFixture.setUp();

    myFixture.addClass("package org.junit.platform.commons.annotation; public @interface Testable {}");
    
  }

  @AfterEach
  void tearDown() throws Exception {
    myFixture.tearDown();
  }

  @Test
  void customEngineOnly() {
    PsiClass customEngineTest = myFixture.addClass("import org.junit.platform.commons.annotation.Testable;" +
                                                   " /** @noinspection ALL*/ " +
                                                   "@Testable\n" +
                                                   "class MyTests{}");
    assertTrue(JUnitUtil.isTestClass(customEngineTest));
    assertInstanceOf(JUnit5Framework.class, TestFrameworks.detectFramework(customEngineTest));

    PsiClass customEngineAnnotationOnSuper
      = myFixture.addClass(
      "class MyCustomClass extends MyTests{}");
    assertTrue(JUnitUtil.isTestClass(customEngineAnnotationOnSuper));
    assertInstanceOf(JUnit5Framework.class, TestFrameworks.detectFramework(customEngineAnnotationOnSuper));
  }
}
