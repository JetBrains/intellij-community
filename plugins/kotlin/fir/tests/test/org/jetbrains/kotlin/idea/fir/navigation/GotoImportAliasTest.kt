// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.navigation

import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.search.ideaExtensions.KotlinImportAliasGotoDeclarationHandler
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.psi.KtImportAlias

class GotoImportAliasTest : KotlinLightCodeInsightFixtureTestCase() {
    override val pluginMode: KotlinPluginMode = KotlinPluginMode.K2

    fun `test multi declarations`() {
        myFixture.configureByText(
            "dummy.kt",
            """
            package c

            import c.a
            import c.a as b<caret>

            fun a() = Unit
            fun a(i: Int) = Unit

            fun test() {
                a()
                b()
            }
        """.trimIndent(),
        )

        val result = KotlinImportAliasGotoDeclarationHandler().getGotoDeclarationTargets(
            (myFixture.elementAtCaret as KtImportAlias).nameIdentifier,
            myFixture.caretOffset,
            editor,
        )

        assertEquals(2, result?.size)
    }
}