// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.searcheverywhere

import com.intellij.ide.actions.searcheverywhere.SEResultsEqualityProvider.SEEqualElementsActionType.Replace
import com.intellij.ide.actions.searcheverywhere.SEResultsEqualityProvider.SEEqualElementsActionType.Skip
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereFoundElementInfo
import com.intellij.psi.util.parentOfType
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.psi.KtClass

/**
 * @see KtSearchEverywhereEqualityProvider
 */
class NativePsiAndKtLightElementEqualityProviderTest : LightJavaCodeInsightFixtureTestCase() {
    fun `test KtLightElement should be skipped`() {
        val file = myFixture.configureByText("Foo.kt", "class Fo<caret>o")
        val klass = file.findElementAt(myFixture.caretOffset)?.parentOfType<KtClass>()!!
        val ulc = klass.toLightClass()!!

        val actualAction = KtSearchEverywhereEqualityProvider().compareItems(
            newItem = SearchEverywhereFoundElementInfo(ulc, 0, null),
            alreadyFoundItems = listOf(SearchEverywhereFoundElementInfo(klass, 0, null))
        )
        assertEquals(Skip, actualAction)
    }

    fun `test KtLightElement should be replaced`() {
        val file = myFixture.configureByText("Foo.kt", "class Fo<caret>o")
        val klass = file.findElementAt(myFixture.caretOffset)?.parentOfType<KtClass>()!!
        val ulc = klass.toLightClass()!!

        val ulcElementInfo = SearchEverywhereFoundElementInfo(ulc, 0, null)
        val actualAction = KtSearchEverywhereEqualityProvider().compareItems(
            newItem = SearchEverywhereFoundElementInfo(klass, 0, null),
            alreadyFoundItems = listOf(ulcElementInfo)
        )
        assertEquals(Replace(ulcElementInfo), actualAction)
    }
}
