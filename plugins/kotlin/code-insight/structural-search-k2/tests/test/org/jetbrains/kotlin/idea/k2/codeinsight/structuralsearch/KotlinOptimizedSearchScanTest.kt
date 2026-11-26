// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.structuralsearch.MatchOptions
import com.intellij.structuralsearch.impl.matcher.compiler.PatternCompiler
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.fixtures.lightJavaTestDescriptor
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.kotlin.idea.KotlinFileType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@TestApplication
class KotlinOptimizedSearchScanTest {
    companion object {
        private val projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor())
    }

    private fun getSearchPlan(query: String): String {
        val matchOptions = MatchOptions()
        matchOptions.fillSearchCriteria(query)
        matchOptions.setFileType(KotlinFileType.INSTANCE)
        val project = projectFixture.get().project
        return runReadActionBlocking {
            PatternCompiler.compilePattern(project, matchOptions, true, true)
            PatternCompiler.getLastSearchPlan()
        }
    }

    fun doTest(message: String, query: String, plan: String) {
        assertEquals(plan, getSearchPlan(query), message)
    }

    fun doTest(query: String, plan: String) {
        assertEquals(plan, getSearchPlan(query))
    }

    @Test
    fun testClass() {
        doTest("class Foo", "[in code:Foo]")
    }

    @Test
    fun testNamedFunction() {
        doTest("fun foo(): '_", "[in code:foo]")
    }

    @Test
    fun testParameter() {
        doTest("fun '_(foo: '_)", "[in code:foo]")
    }

    @Test
    fun testProperty() {
        doTest("val foo = 1", "[in code:foo]")
    }

    @Test
    fun testDQE() {
        doTest("'_.lifetime", "[in code:lifetime]")
    }

    @Test
    fun testAnnotation() {
        doTest("@Deprecated fun('_*)", "[in code:Deprecated]")
    }

    @Test
    fun testKDocTag() {
        doTest("/** \n" +
                      "* @author '_\n" +
                      "*/", "[in code:author]")
    }

}
