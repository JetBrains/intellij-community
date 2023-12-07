// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2

import com.intellij.psi.util.findParentOfType
import junit.framework.TestCase
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginKind
import org.jetbrains.kotlin.idea.base.test.KotlinJvmLightProjectDescriptor
import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import org.jetbrains.kotlin.idea.base.test.NewLightKotlinCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.base.test.executeOnPooledThreadInReadAction
import org.jetbrains.kotlin.psi.KtCallExpression

class KotlinExpressionNameSuggesterTest : NewLightKotlinCodeInsightFixtureTestCase() {
    override val pluginKind: KotlinPluginKind
        get() = KotlinPluginKind.FIR_PLUGIN

    fun testNumericLiteral() = test("5", "i", "n", "message")
    fun testStringLiteral() = test("\"foo\"", "string", "str", "s", "text", "message")
    fun testClassLiteral() = test("String::class", "klass", "clazz", "declaration", "message")
    fun testIntArray() = test("intArrayOf(1, 2, 3)", "ints", "message", "intArrayOf", "arrayOf")
    fun testGenericIntArray() = test("arrayOf(1, 2, 3)", "ints", "message", "arrayOf")
    fun testGenericArray() = test("arrayOf(1, 'c')", "values", "message", "arrayOf",)
    fun testIntList() = test("listOf(1, 2, 3)", "ints", "message", "listOf")
    fun testClassList() = test("listOf(String::class.java, Long::class.java)", "classes", "message", "listOf")
    fun testStringList() = test("listOf(\"foo\", \"bar\")", "strings", "message", "listOf")
    fun testGenericList() = test("listOf(1, 'c')", "values", "message", "listOf")
    fun testLazy() = test("lazy { 5 }", "lazy", "message")
    fun testJavaFile() = test("java.io.File(\".\")", "file", "message")
    fun testAnonymousFunction() = test("fun(s: String): Boolean = s.isNotEmpty()", "function", "fn", "f", "message")
    fun testLambda() = test("{ s: String -> s.isNotEmpty() }", "function", "fn", "f", "message")
    fun testShortUnresolvedCall() = test("E()", "e")
    fun testShortUnresolvedCallWithPrefixIs() = test("isE()", "e")
    fun testShortUnresolvedCallWithPrefixGet() = test("getE()", "e")

    private fun test(expressionText: String, vararg names: String) {
        val fileText = "fun test() { print<caret>($expressionText) }"
        val file = myFixture.configureByText("file.kt", fileText)

        val callExpression = file.findElementAt(myFixture.caretOffset)!!.findParentOfType<KtCallExpression>()!!
        val targetExpression = callExpression.valueArguments.single().getArgumentExpression()!!

        executeOnPooledThreadInReadAction {
            analyze(targetExpression) {
                val actualNames = with(KotlinNameSuggester()) {
                    suggestExpressionNames(targetExpression).toList().sorted()
                }

                TestCase.assertEquals(names.sorted(), actualNames)
            }
        }
    }

    override fun getProjectDescriptor() = KotlinJvmLightProjectDescriptor.DEFAULT
    override fun getTestDataPath() = KotlinRoot.PATH.toString()
}