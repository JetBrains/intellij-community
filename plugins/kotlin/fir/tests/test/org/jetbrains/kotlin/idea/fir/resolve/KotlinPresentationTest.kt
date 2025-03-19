// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.resolve

import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.refactoring.chooseContainer.popupPresentationProvider
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.junit.Assert

class KotlinPresentationTest: KotlinLightCodeInsightFixtureTestCase() {
    override val pluginMode: KotlinPluginMode = KotlinPluginMode.K2
    fun testFunctionPresentation() {
        val file = myFixture.configureByText("a.kt", "fun <T> (() -> kotlin.Boolean).foo(p: T.() -> kotlin.Boolean) {}") as KtFile
        val function = file.declarations[0] as KtFunction
        val presentableText = function.presentation!!.presentableText
        Assert.assertEquals("(() -> kotlin.Boolean).foo(T.() -> kotlin.Boolean)", presentableText)
    }

    fun testTruncatedPopupsPresentation() {
        val file = myFixture.configureByText("a.kt", "fun String.main(a: kotlin.Int, vararg b: String) {}"
        ) as KtFile
        val function = file.declarations[0] as KtFunction
        val targetPresentation = popupPresentationProvider().getPresentation(function)
        assertEquals("fun String.main(a: kotlin.Int, vararg b: String)", targetPresentation.presentableText)
    }
}