// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.editor

import com.intellij.codeInsight.highlighting.BraceMatchingUtil
import org.junit.Assert
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class KotlinBraceMatcherTest : KotlinLightCodeInsightFixtureTestCase() {
    fun testGenericsWithInOutModifiers() {
        doBraceMatchingTest("class A<caret><in T, out F, *>")
    }

    fun testGenericsWithComplexParameters() {
        doBraceMatchingTest("class A<caret><T : List<@Ann(B::class) Int>>")
    }

    private fun doBraceMatchingTest(text: String) {
        myFixture.configureByText("a.kt", text.trimMargin())

        val editor = myFixture.editor
        val iterator = editor.highlighter.createIterator(editor.caretModel.offset)
        val matched = BraceMatchingUtil.matchBrace(editor.document.charsSequence, myFixture.file.fileType, iterator, true)
        Assert.assertTrue(matched)
    }
}