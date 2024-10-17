// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.dsl

import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.util.TestUtils

class GroovyTransformationsTest extends LightJavaCodeInsightFixtureTestCase {

  @Override
  protected String getBasePath() {
    TestUtils.getTestDataPath() + "groovy/dsl/transform"
  }

  void doPlainTest(String type = "") throws Throwable {
    myFixture.testCompletionTyping(getTestName(false) + ".groovy", type, getTestName(false) + "_after.groovy")
  }

  void doVariantsTest(String... variants) throws Throwable {
    myFixture.testCompletionVariants(getTestName(false) + ".groovy", variants)
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_1_7
  }

  void testDelegateAnnotation() throws Throwable { doPlainTest() }

  void testCategoryTransform() throws Throwable { doVariantsTest('name', 'getName') }

  void testMixinTransform() throws Throwable { doPlainTest() }

  void testBindableTransform() throws Throwable { doPlainTest() }

  void testVetoableTransform() throws Throwable { doPlainTest() }
}
