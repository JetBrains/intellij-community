// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.dsl;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class GroovyTransformationsTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_1_7);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(TestUtils.getAbsoluteTestDataPath() + "groovy/dsl/transform");
  }

  public void doPlainTest(String type) throws Throwable {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.testCompletionTyping(myFixture.getTestName(false) + ".groovy", type, myFixture.getTestName(false) + "_after.groovy");
  }

  public void doPlainTest() throws Throwable {
    doPlainTest("");
  }

  public void doVariantsTest(String... variants) throws Throwable {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.testCompletionVariants(myFixture.getTestName(false) + ".groovy", variants);
  }

  @Test
  public void testDelegateAnnotation() throws Throwable { doPlainTest(); }

  @Test
  public void testCategoryTransform() throws Throwable { doVariantsTest("name", "getName"); }

  @Test
  public void testMixinTransform() throws Throwable { doPlainTest(); }

  @Test
  public void testBindableTransform() throws Throwable { doPlainTest(); }

  @Test
  public void testVetoableTransform() throws Throwable { doPlainTest(); }
}
