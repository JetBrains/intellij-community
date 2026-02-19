// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixTestCase
import com.intellij.structuralsearch.MatchOptions
import com.intellij.structuralsearch.impl.matcher.compiler.PatternCompiler
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin

class KotlinOptimizedSearchScanTest : LightQuickFixTestCase(), ExpectedPluginModeProvider {
    override val pluginMode: KotlinPluginMode = KotlinPluginMode.K2

    override fun setUp() {
        setUpWithKotlinPlugin { super.setUp() }
    }

    private fun getSearchPlan(query: String): String {
        val matchOptions = MatchOptions()
        matchOptions.fillSearchCriteria(query)
        matchOptions.setFileType(KotlinFileType.INSTANCE)
      PatternCompiler.compilePattern(project, matchOptions, true, true)
        return PatternCompiler.getLastSearchPlan()
    }

    fun doTest(message: String, query: String, plan: String) {
        assertEquals(message, plan, getSearchPlan(query))
    }

    fun doTest(query: String, plan: String) {
        assertEquals(plan, getSearchPlan(query))
    }

    fun testClass() {
        doTest("class Foo", "[in code:Foo]")
    }

    fun testNamedFunction() {
        doTest("fun foo(): '_", "[in code:foo]")
    }

    fun testParameter() {
        doTest("fun '_(foo: '_)", "[in code:foo]")
    }

    fun testProperty() {
        doTest("val foo = 1", "[in code:foo]")
    }

    fun testDQE() {
        doTest("'_.lifetime", "[in code:lifetime]")
    }

    fun testAnnotation() {
        doTest("@Deprecated fun('_*)", "[in code:Deprecated]")
    }

    fun testKDocTag() {
        doTest("/** \n" +
                      "* @author '_\n" +
                      "*/", "[in code:author]")
    }

}