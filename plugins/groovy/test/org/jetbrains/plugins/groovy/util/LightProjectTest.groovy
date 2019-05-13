// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.util

import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.rules.TestRule

@CompileStatic
abstract class LightProjectTest {

  private final FixtureRule myFixtureRule = new FixtureRule(projectDescriptor, '')
  public final @Rule TestRule myRules = RuleChain.outerRule(myFixtureRule).around(new EdtRule())

  abstract LightProjectDescriptor getProjectDescriptor()

  @NotNull
  final CodeInsightTestFixture getFixture() {
    myFixtureRule.fixture
  }
}
