// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.util

import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.rules.TestName
import org.junit.rules.TestRule

@CompileStatic
abstract class LightProjectTest {

  private final TestName myTestName
  private final FixtureRule myFixtureRule
  public final @Rule TestRule myRules

  LightProjectTest(String testDataPath = '') {
    myTestName = new TestName()
    myFixtureRule = new FixtureRule(projectDescriptor, testDataPath)
    myRules = RuleChain.outerRule(myTestName).around(myFixtureRule).around(new EdtRule())
  }

  abstract LightProjectDescriptor getProjectDescriptor()

  String getTestName() {
    return myTestName.methodName
  }

  @NotNull
  final JavaCodeInsightTestFixture getFixture() {
    myFixtureRule.fixture
  }
}
