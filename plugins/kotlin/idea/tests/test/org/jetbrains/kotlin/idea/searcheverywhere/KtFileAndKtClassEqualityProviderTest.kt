// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.searcheverywhere

import com.intellij.ide.actions.searcheverywhere.SEResultsEqualityProvider.SEEqualElementsActionType.*
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereFoundElementInfo
import com.intellij.psi.util.parentOfType
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile

/**
 * @see KtSearchEverywhereEqualityProvider
 */
class KtFileAndKtClassEqualityProviderTest : LightJavaCodeInsightFixtureTestCase() {
    fun `test KtFile should be replaced by KtClass`() {
        val psiFile = myFixture.configureByText("Foo.kt", "class F<caret>oo")
        val klass = psiFile.findElementAt(myFixture.caretOffset)?.parentOfType<KtClass>()!!
        val ktFile = psiFile.findElementAt(myFixture.caretOffset)?.parentOfType<KtFile>()!!

        val ktFileElementInfo = SearchEverywhereFoundElementInfo(ktFile, 0, null)
        val actualAction = KtSearchEverywhereEqualityProvider().compareItems(
            newItem = SearchEverywhereFoundElementInfo(klass, 0, null),
            alreadyFoundItems = listOf(ktFileElementInfo)
        )
        assertEquals(actualAction, Replace(ktFileElementInfo))
    }

    fun `test KtFile should be skipped`() {
        val psiFile = myFixture.configureByText("Foo.kt", "class F<caret>oo")
        val klass = psiFile.findElementAt(myFixture.caretOffset)?.parentOfType<KtClass>()!!
        val ktFile = psiFile.findElementAt(myFixture.caretOffset)?.parentOfType<KtFile>()!!

        val actualAction = KtSearchEverywhereEqualityProvider().compareItems(
            newItem = SearchEverywhereFoundElementInfo(ktFile, 0, null),
            alreadyFoundItems = listOf(SearchEverywhereFoundElementInfo(klass, 0, null))
        )
        assertEquals(actualAction, Skip)
    }

    fun `test KtFile and KtClass shouldn't be deduplicated when facade class exist`() {
        val psiFile = myFixture.configureByText(
            "Foo.kt",
            """
                fun foo() = Unit // Force to create KtLightClassForFacade
                
                class F<caret>oo
            """.trimIndent()
        )
        val ktFile = psiFile.findElementAt(myFixture.caretOffset)?.parentOfType<KtFile>()!!
        val ktClass = psiFile.findElementAt(myFixture.caretOffset)?.parentOfType<KtClass>()!!

        val actualAction = KtSearchEverywhereEqualityProvider().compareItems(
            newItem = SearchEverywhereFoundElementInfo(ktFile, 0, null),
            alreadyFoundItems = listOf(SearchEverywhereFoundElementInfo(ktClass, 0, null))
        )
        assertEquals(DoNothing, actualAction)
    }
}
