// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.codeInsight

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.testFramework.fixtures.CompletionAutoPopupTester
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import com.intellij.util.ThrowableRunnable
import org.assertj.core.api.Assertions.assertThat

class PluginXmlAttributeCompletionTest : JavaCodeInsightFixtureTestCase() {
  private lateinit var myTester: CompletionAutoPopupTester

  override fun setUp() {
    super.setUp()
    myTester = CompletionAutoPopupTester(myFixture)
  }

  protected override fun runInDispatchThread() = false

  override fun runTestRunnable(testRunnable: ThrowableRunnable<Throwable?>) {
    myTester.runWithAutoPopupEnabled(testRunnable)
  }

  fun testCompletionForClassAttributeInvokesSmartCompletion() {
    myFixture.addClass("package com.example; public interface A {}")
    myFixture.addClass("package com.example; public class B implements A {}")
    myFixture.addClass("package com.example; public class C implements A {}")
    myFixture.addClass("package com.example; public class X {}")
    myFixture.configureByText(
      "plugin.xml",
      //language=XML
      """
        <idea-plugin>
          <extensionPoints>
            <extensionPoint name="testEp" interface="com.example.A"/>
          </extensionPoints>
          <extensions defaultExtensionNs="com.intellij">
            <testEp <caret>
          </extensions>
        </idea-plugin>
        """.trimIndent()
    )

    myTester.typeWithPauses("implementation")
    val lookup = myTester.lookup
    assertNotNull("Completion popup was not automatically open!", lookup)
    myTester.joinCommit { lookup.finishLookup(Lookup.NORMAL_SELECT_CHAR) }

    myTester.joinAutopopup()
    myTester.joinCompletion()
    assertThat(myTester.lookup.items.map { it.lookupString })
      .containsOnly(
        "com.example.B",
        "com.example.C"
      )
  }

}
