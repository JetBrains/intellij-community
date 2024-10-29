// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.findUsages

import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector
import com.intellij.codeInsight.highlighting.ReadWriteUtil
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase

class KotlinFindUsagesReadAccessTest: KotlinLightCodeInsightFixtureTestCase() {

    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K2

    fun testKotlinReadWriteAccessFromJava() {
        myFixture.addFileToProject("Foo.kt", "class Foo { var p = 0 }")
        myFixture.configureByText("Bar.java", """class Bar {
            | void foo(Foo foo) {
            |   foo.<caret>p = 42;
            | }
            |}""".trimMargin())
        assertWriteAccess()
    }

    fun testJavaReadWriteAccessFromKotlin() {
        myFixture.addFileToProject("Foo.java", "class Foo { public Int p = 0; }")
        myFixture.configureByText("Bar.kt", """class Bar {
            | fun foo(foo: Foo) {
            |   foo.<caret>p = 42;
            | }
            |}""".trimMargin())
        assertWriteAccess()
    }

    private fun assertWriteAccess() {
        val primaryElement = myFixture.elementAtCaret
        val element = myFixture.file.findElementAt(myFixture.caretOffset)!!.parent
        assertEquals(ReadWriteAccessDetector.Access.Write, ReadWriteUtil.getReadWriteAccess(arrayOf(primaryElement), element))
    }
}