// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.inspections.tests

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.UsefulTestCase
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginKind
import org.jetbrains.kotlin.idea.base.test.KotlinJvmLightProjectDescriptor
import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import org.jetbrains.kotlin.idea.base.test.NewLightKotlinCodeInsightFixtureTestCase
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class HighlightingAfterTypingTest : NewLightKotlinCodeInsightFixtureTestCase() {
    override val pluginKind: KotlinPluginKind
        get() = KotlinPluginKind.K2

  override fun getProjectDescriptor() = KotlinJvmLightProjectDescriptor.DEFAULT
  override fun getTestDataPath() = KotlinRoot.PATH.toString()

    fun testTypingInsideCodeBlockDoesNotLeadToIncorrectlyUnusedSymbols() {
        val documentText = """
             fun <warning descr="Function \"lazy3\" is never used">lazy3</warning>(initializer: () -> Unit) {
                initializer.invoke()<caret>
             }
        """.trimIndent()
      myFixture.enableInspections(org.jetbrains.kotlin.idea.k2.codeinsight.inspections.UnusedSymbolInspection())
      myFixture.configureByText(KotlinFileType.INSTANCE, documentText)
      myFixture.checkHighlighting(true, false, false, false)

      myFixture.type("\n")
      UsefulTestCase.assertOneElement(myFixture.doHighlighting (HighlightSeverity.WARNING))
    }
}