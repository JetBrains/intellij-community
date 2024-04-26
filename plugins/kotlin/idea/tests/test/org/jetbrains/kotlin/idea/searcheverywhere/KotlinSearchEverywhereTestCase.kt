// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.searcheverywhere

import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereManagerImpl
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereUI
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.java.navigation.ChooseByNameTest
import com.intellij.java.navigation.SearchEverywhereTest
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiElement
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

abstract class KotlinSearchEverywhereTestCase : LightJavaCodeInsightFixtureTestCase() {
    protected fun findPsiByPattern(pattern: String, action: (List<PsiElement>) -> Unit) {
        findByPattern(pattern) {
            action(it.mapNotNull(PSIPresentationBgRendererWrapper::toPsi))
        }
    }

    protected fun findByPattern(pattern: String, action: (List<Any>) -> Unit) {
        val disposable = Disposer.newDisposable("ui disposer")
        try {
            val event = ChooseByNameTest.createEvent(project)
            val ui = SearchEverywhereUI(
                project,
                SearchEverywhereContributor.EP_NAME.extensionList.map {
                    val contributor = it.createContributor(event)
                    Disposer.register(disposable, contributor)
                    contributor
                }
            )

            Disposer.register(disposable, ui)

            ui.switchToTab(SearchEverywhereManagerImpl.ALL_CONTRIBUTORS_GROUP_ID)
            val future = ui.findElementsForPattern(pattern)
            action(
                PlatformTestUtil.waitForFuture(future, SearchEverywhereTest.SEARCH_TIMEOUT)
            )
        } finally {
            Disposer.dispose(disposable)
        }
    }

    protected fun configureAndCheckActions(text: String, expected: List<String>, pattern: String) {
        myFixture.configureByText("TestFile.kt", text)
        findByPattern(pattern) { elements ->
            val actions = elements.filterIsInstance<GotoActionModel.MatchedValue>()
                .map { it.value }
                .filterIsInstance<GotoActionModel.ActionWrapper>()

            val presentationTexts = actions.map { it.presentation.text }
            UsefulTestCase.assertContainsElements(presentationTexts, expected)
        }
    }
}