// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.test

import com.intellij.codeInsight.completion.PlainPrefixMatcher
import com.intellij.codeInsight.completion.PlainTextSymbolCompletionContributorEP
import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase

class KotlinPlainTextSymbolCompletionContributorTest : KotlinLightCodeInsightFixtureTestCase() {
    fun testBasics() {
        myFixture.configureByText(
            "Test.kt", """class MyClass(val param1: Int, val param2: String, param3: String) {
                |  fun method1() {}
                |  object Xyz {}
                |}
                |object MyObject {
                |  fun objectMethod() {}
                |}
                |fun myFunction() {}
                |""".trimMargin()
        )
        checkCompletion(file, "MyC", 1, "MyClass")
        checkCompletion(file, "M", 1, "MyClass", "method1", "param1", "param2", "MyObject", "objectMethod", "myFunction")
        checkCompletion(file, "MyClass.", 0, "MyClass.method1", "MyClass.Xyz", "MyClass.param1", "MyClass.param2")
        checkCompletion(file, "MyClass::", 0, "MyClass::method1", "MyClass::param1", "MyClass::param2")
        checkCompletion(file, "MyClass#", 0, "MyClass#method1", "MyClass#param1", "MyClass#param2")
        checkCompletion(file, "MyObject.", 0, "MyObject.objectMethod")
    }

    private fun checkCompletion(file: PsiFile, prefix: String, invocationCount: Int, vararg expected: String) {
        val contributor = PlainTextSymbolCompletionContributorEP.forLanguage(KotlinLanguage.INSTANCE)
        assertNotNull(contributor)
        val options = contributor!!.getLookupElements(file, invocationCount, prefix)
        val matcher: PrefixMatcher = PlainPrefixMatcher(prefix)
        assertEquals(
            listOf(*expected),
            options.filter { element -> matcher.prefixMatches(element) }
                .map { element -> element.lookupString }
                .toList()
        )
    }

}