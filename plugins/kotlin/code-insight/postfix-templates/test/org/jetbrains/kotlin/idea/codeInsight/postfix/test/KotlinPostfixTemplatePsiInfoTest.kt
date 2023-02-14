// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.postfix.test

import com.intellij.codeInsight.CodeInsightUtilCore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiElement
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.test.KotlinJvmLightProjectDescriptor
import org.jetbrains.kotlin.idea.codeInsight.postfix.KotlinPostfixTemplatePsiInfo
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtReturnExpression

class KotlinPostfixTemplatePsiInfoTest: LightJavaCodeInsightFixtureTestCase() {
    override fun getProjectDescriptor(): LightProjectDescriptor = KotlinJvmLightProjectDescriptor.DEFAULT

    fun testNameReference() = test("foo", "!foo")
    fun testNegatedNameReference() = test("!foo", "foo")
    fun testIs() = test("foo is Foo", "foo !is Foo")
    fun testNotIs() = test("foo !is Foo", "foo is Foo")
    fun testAs() = test("foo as Foo", "!(foo as Foo)")
    fun testSafeAs() = test("foo as? Foo", "!(foo as? Foo)")
    fun testIn() = test("foo in bar", "foo !in bar")
    fun testNotIn() = test("foo !in bar", "foo in bar")
    fun testParenthesized() = test("(a in b)", "!(a in b)")
    fun testLabeled() = test("(@label /*comment*/ <selection>foo</selection>)", "(@label /*comment*/ (!foo))")
    fun testAnnotated() = test("(@Anno /*comment*/ <selection>foo</selection>)", "(@Anno /*comment*/ (!foo))")
    fun testUnary() = test("+a", "!(+a)")
    fun testBinary() = test("a + b", "!(a + b)")
    fun testInfix() = test("a foo b", "!(a foo b)")
    fun testTrue() = test("true", "false")
    fun testFalse() = test("false", "true")
    fun testNotTrue() = test("!true", "true")
    fun testNotFalse() = test("!false", "false")
    fun testNumber() = test("5", "!5")
    fun testCharacter() = test("'c'", "!'c'")
    fun testString() = test("\"foo\"", "!\"foo\"")
    fun testStringInterpolation() = test("\"foo\${bar}\"", "!\"foo\${bar}\"")
    fun testNull() = test("null", "!null")
    fun testGt() = test("a > b", "a <= b")
    fun testLe() = test("a <= b", "a > b")
    fun testEquals() = test("a == b", "a != b")
    fun testNotEquals() = test("a != b", "a == b")
    fun testInstanceEquals() = test("a === b", "a !== b")
    fun testInstanceNotEquals() = test("a !== b", "a === b")
    fun testIf() = test("if (cond) a else b", "if (cond) b else a")
    fun testIfIncomplete() = test("if (cond) a", "!(if (cond) a)")
    fun testListIsEmpty() = test("listOf(1, 2, 3).<selection>isEmpty()</selection>", "listOf(1, 2, 3).isNotEmpty()")

    private fun test(text: String, expected: String) {
        myFixture.configureByText("test.kt", "fun foo() { return $text }")

        val ktFunction = (file as KtFile).declarations.single() as KtNamedFunction
        val ktFunctionReturnExpression = ktFunction.bodyBlockExpression!!.statements.single() as KtReturnExpression
        val ktFunctionReturnValue = ktFunctionReturnExpression.returnedExpression!!

        val targetElement = findSelectedElement() ?: ktFunctionReturnValue

        val negatedElement = ApplicationManager.getApplication()
            .executeOnPooledThread<PsiElement> {
                runReadAction { KotlinPostfixTemplatePsiInfo.getNegatedExpression(targetElement) }
            }
            .get()

        project.executeWriteCommand("Replace original element") {
            targetElement.replace(negatedElement)
        }

        assertEquals(expected, ktFunctionReturnExpression.returnedExpression!!.text)
    }

    private fun findSelectedElement(): PsiElement? {
        val selectionModel = myFixture.editor.selectionModel

        if (selectionModel.hasSelection()) {
            return CodeInsightUtilCore.findElementInRange(
                file,
                selectionModel.selectionStart,
                selectionModel.selectionEnd,
                KtExpression::class.java,
                KotlinLanguage.INSTANCE
            )
        }

        return null
    }
}