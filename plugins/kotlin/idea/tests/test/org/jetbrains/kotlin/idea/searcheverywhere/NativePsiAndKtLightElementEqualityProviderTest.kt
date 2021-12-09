// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.searcheverywhere

import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereManagerImpl
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereUI
import com.intellij.java.navigation.ChooseByNameTest
import com.intellij.java.navigation.SearchEverywhereTest
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.kotlin.asJava.LightClassGenerationSupport
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.utils.addToStdlib.cast
import org.jetbrains.kotlin.utils.keysToMap

/**
 * @see KtSearchEverywhereEqualityProvider
 */
class NativePsiAndKtLightElementEqualityProviderTest : LightJavaCodeInsightFixtureTestCase() {
    fun `test only class presented`() {
        val file = myFixture.configureByText("MyKotlinClassWithStrangeName.kt", "class MyKotlinClassWithStrangeName")
        val klass = file.findElementAt(myFixture.caretOffset)?.parentOfType<KtClass>()!!
        val ulc = LightClassGenerationSupport.getInstance(project).createUltraLightClass(klass)!!
        findByPattern("MyKotlinClassWithStrangeName") { results ->
            assertTrue(klass in results)
            assertFalse(file in results)
            assertFalse(ulc in results)
        }
    }

    fun `test class conflict`() {
        val file = myFixture.configureByText(
            "MyKotlinClassWithStrangeName.kt",
            "package one.two\nclass MyKotlinClassWithStrangeName\nclass MyKotlinClassWithStrangeName<T>",
        ) as KtFile

        val klass = file.declarations.first() as KtClass
        val klass2 = file.declarations.last() as KtClass
        val ulc = LightClassGenerationSupport.getInstance(project).createUltraLightClass(klass)!!
        val ulc2 = LightClassGenerationSupport.getInstance(project).createUltraLightClass(klass2)!!
        findByPattern("MyKotlinClassWithStrangeName") { results ->
            assertTrue(klass in results)
            assertTrue(klass2 in results)
            assertFalse(file in results)
            assertFalse(ulc in results)
            assertFalse(ulc2 in results)
        }
    }

    fun `test class and file presented`() {
        val file = myFixture.configureByText(
            "MyKotlinClassWithStrangeName.kt",
            "class MyKotlinClassWithStrangeName\nfun t(){}",
        ) as KtFile

        val klass = file.findElementAt(myFixture.caretOffset)?.parentOfType<KtClass>()!!
        val ulc = LightClassGenerationSupport.getInstance(project).createUltraLightClass(klass)!!
        val syntheticClass = file.declarations.last().cast<KtNamedFunction>().toLightMethods().single().parent
        findByPattern("MyKotlinClassWithStrangeName") { results ->
            assertTrue(results.toString(), results.size == 1)
            assertTrue(klass in results)
            assertFalse(file in results)
            assertFalse(syntheticClass in results)
            assertFalse(ulc in results)
        }
    }

    private fun findByPattern(pattern: String, action: (List<PsiElement>) -> Unit) {
        val disposable = Disposer.newDisposable("ui disposer")
        try {
            val event = ChooseByNameTest.createEvent(project)
            val ui = SearchEverywhereUI(
                project,
                SearchEverywhereContributor.EP_NAME.extensionList.map {
                    val contributor = it.createContributor(event)
                    Disposer.register(disposable, contributor)
                    contributor
                }.keysToMap { null }
            )

            Disposer.register(disposable, ui)

            ui.switchToTab(SearchEverywhereManagerImpl.ALL_CONTRIBUTORS_GROUP_ID)
            val future = ui.findElementsForPattern(pattern)
            action(
                PlatformTestUtil.waitForFuture(future, SearchEverywhereTest.getSEARCH_TIMEOUT().toLong()).mapNotNull {
                    PSIPresentationBgRendererWrapper.toPsi(it)
                }
            )
        } finally {
            Disposer.dispose(disposable)
        }
    }
}
