// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.searcheverywhere

import com.intellij.ide.actions.searcheverywhere.ClassSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SEResultsEqualityProvider.SEEqualElementsActionType.DoNothing
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereFoundElementInfo
import com.intellij.ide.util.scopeChooser.ScopeDescriptor
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.util.Disposer
import com.intellij.psi.util.parentOfType
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.util.indexing.FindSymbolParameters
import org.jetbrains.kotlin.asJava.findFacadeClass
import org.jetbrains.kotlin.psi.KtFile
import kotlin.test.assertNotEquals

/**
 * @see KtSearchEverywhereEqualityProvider
 */
class KtFileAndKtClassForFacadeTest : LightJavaCodeInsightFixtureTestCase() {
    fun `test KtFile and KtClassForFacade should be deduplicated`() {
        val psiFile = myFixture.configureByText(
            "Foo.kt",
            """
                fun foo() = Unit // Force to create KtLightClassForFacade
                
                class F<caret>oo
            """.trimIndent()
        )
        val ktFile = psiFile.findElementAt(myFixture.caretOffset)?.parentOfType<KtFile>()!!
        val facadeClass = psiFile.findElementAt(myFixture.caretOffset)?.parentOfType<KtFile>()!!.findFacadeClass()

        val actualAction = KtSearchEverywhereEqualityProvider().compareItems(
            newItem = SearchEverywhereFoundElementInfo(facadeClass, 0, createMockContributor()),
            alreadyFoundItems = listOf(SearchEverywhereFoundElementInfo(ktFile, 0, createMockContributor()))
        )
        assertNotEquals(DoNothing, actualAction)
    }

    fun `test KtFile and KtClassForFacade shouldn't be deduplicated when non default JvmName is used`() {
        val psiFile = myFixture.configureByText(
            "Foo.kt",
            """
                @file:JvmName("NonDefaultName")
                
                fun foo() = Unit // Force to create KtLightClassForFacade
                
                class F<caret>oo
            """.trimIndent()
        )
        val ktFile = psiFile.findElementAt(myFixture.caretOffset)?.parentOfType<KtFile>()!!
        val facadeClass = psiFile.findElementAt(myFixture.caretOffset)?.parentOfType<KtFile>()!!.findFacadeClass()

        val actualAction = KtSearchEverywhereEqualityProvider().compareItems(
            newItem = SearchEverywhereFoundElementInfo(facadeClass, 0, createMockContributor()),
            alreadyFoundItems = listOf(SearchEverywhereFoundElementInfo(ktFile, 0, createMockContributor()))
        )
        assertEquals(DoNothing, actualAction)
    }

    private fun createMockContributor(): SearchEverywhereContributor<*> {
        val dataContext = SimpleDataContext.getProjectContext(project)
        val event = AnActionEvent.createFromDataContext(ActionPlaces.UNKNOWN, null, dataContext)
        val contributor: ClassSearchEverywhereContributor = object : ClassSearchEverywhereContributor(event) {
            init {
                myScopeDescriptor = ScopeDescriptor(FindSymbolParameters.searchScopeFor(myProject, false))
            }
        }
        Disposer.register(myFixture.projectDisposable, contributor)
        return contributor
    }
}
