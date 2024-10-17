// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.dsl;

import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.util.TestUtils;

public class GroovyTransformationsTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "groovy/dsl/transform";
  }

  public void doPlainTest(String type) throws Throwable {
    myFixture.testCompletionTyping(getTestName(false) + ".groovy", type, getTestName(false) + "_after.groovy");
  }

  public void doPlainTest() throws Throwable {
    doPlainTest("");
  }

  public void doVariantsTest(String... variants) throws Throwable {
    myFixture.testCompletionVariants(getTestName(false) + ".groovy", variants);
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_1_7;
  }

  public void testDelegateAnnotation() throws Throwable { doPlainTest(); }

  public void testCategoryTransform() throws Throwable { doVariantsTest("name", "getName"); }

  public void testMixinTransform() throws Throwable { doPlainTest(); }

  public void testBindableTransform() throws Throwable { doPlainTest(); }

  public void testVetoableTransform() throws Throwable { doPlainTest(); }
}
