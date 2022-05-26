// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl

import com.intellij.testFramework.fixtures.BareTestFixture
import com.intellij.testFramework.fixtures.TestFixtureRule
import org.junit.Rule
import org.junit.rules.RuleChain


open class WslTestBase {
  private val testFixtureRule = TestFixtureRule()
  internal val wslRule = WslRule()

  protected val wsl: WSLDistribution get() = wslRule.wsl
  protected val testFixture: BareTestFixture get() = testFixtureRule.fixture


  /**
   * [WslRule] depends on [TestFixtureRule]
   */
  @JvmField
  @Rule
  val ruleChain: RuleChain = RuleChain.outerRule(testFixtureRule).around(wslRule)
}

