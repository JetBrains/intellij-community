// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.codeInsight.test

import com.intellij.psi.util.findParentOfType
import com.intellij.testFramework.LightProjectDescriptor
import junit.framework.TestCase
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.executeOnPooledThreadInReadAction
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester.Case
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.psi.KtCallExpression

class KotlinExpressionNameSuggesterTest : KotlinLightCodeInsightFixtureTestCase() {
    fun testNumericLiteral() = test("5", "int", "i", "n")
    fun testStringLiteral() = test("\"foo\"", "string", "str", "s", "text")
    fun testClassLiteral() = test("String::class", "klass", "clazz", "declaration")
    fun testIntArray() = test("intArrayOf(1, 2, 3)", "ints")
    fun testGenericIntArray() = test("arrayOf(1, 2, 3)", "ints")
    fun testGenericArray() = test("arrayOf(1, 'c')", "values")
    fun testIntList() = test("listOf(1, 2, 3)", "ints")
    fun testClassList() = test("listOf(String::class.java, Long::class.java)", "classes")
    fun testStringList() = test("listOf(\"foo\", \"bar\")", "strings")
    fun testGenericList() = test("listOf(1, 'c')", "values")
    fun testLazy() = test("lazy { 5 }", "lazy")
    fun testJavaFile() = test("java.io.File(\".\")", "file")
    fun testAnonymousFunction() = test("fun(s: String): Boolean = s.isNotEmpty()", "function", "fn", "f")
    fun testLambda() = test("{ s: String -> s.isNotEmpty() }", "function", "fn", "f")

    private fun test(expressionText: String, vararg names: String) {
        val fileText = "fun test() { print<caret>($expressionText) }"
        val file = myFixture.configureByText("file.kt", fileText)

        val callExpression = file.findElementAt(myFixture.caretOffset)!!.findParentOfType<KtCallExpression>()!!
        val targetExpression = callExpression.valueArguments.single().getArgumentExpression()!!

        executeOnPooledThreadInReadAction {
            analyze(targetExpression) {
                val actualNames = with(KotlinNameSuggester(Case.CAMEL)) {
                    suggestExpressionNames(targetExpression).toList().sorted()
                }

                TestCase.assertEquals(names.sorted(), actualNames)
            }
        }
    }

    override fun getProjectDescriptor(): LightProjectDescriptor {
        return KotlinWithJdkAndRuntimeLightProjectDescriptor.INSTANCE
    }
}