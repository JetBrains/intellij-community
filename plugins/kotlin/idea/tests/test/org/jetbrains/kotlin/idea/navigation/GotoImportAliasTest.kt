package org.jetbrains.kotlin.idea.navigation

import junit.framework.TestCase
import org.jetbrains.kotlin.idea.search.ideaExtensions.KotlinImportAliasGotoDeclarationHandler
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.psi.KtImportAlias

class GotoImportAliasTest : KotlinLightCodeInsightFixtureTestCase() {
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

        TestCase.assertEquals(2, result?.size)
    }
}