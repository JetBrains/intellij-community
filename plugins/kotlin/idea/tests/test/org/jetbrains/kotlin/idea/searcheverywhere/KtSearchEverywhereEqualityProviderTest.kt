// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.searcheverywhere

import com.intellij.ide.actions.searcheverywhere.SEResultsEqualityProvider.SEEqualElementsActionType.*
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereFoundElementInfo
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parentOfType
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import junit.framework.TestCase
import org.jetbrains.kotlin.asJava.LightClassGenerationSupport
import org.jetbrains.kotlin.asJava.findFacadeClass
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.idea.test.JUnit3RunnerWithInners
import org.junit.runner.RunWith

/**
 * @see KtSearchEverywhereEqualityProvider
 */
@RunWith(JUnit3RunnerWithInners::class)
abstract class KtSearchEverywhereEqualityProviderTest : LightJavaCodeInsightFixtureTestCase() {
    @RunWith(JUnit3RunnerWithInners::class)
    class KtFileAndKtClass : KtSearchEverywhereEqualityProviderTest() {
        fun `test KtFile and KtClass should be deduplicated`() {
            doTest({ it.ktFile to it.ktClass }, expectedToRemove = { it.ktFile })
        }

        fun `test KtFile and KtClass should be deduplicated even when facade class exists`() {
            doTest({ it.ktFile to it.ktClass }, expectedToRemove = { it.ktFile }, withFacade = true)
        }

        fun `test KtFile and KtClass shouldnt be deduplicated if filename doesnt match`() {
            doTest({ it.ktFile to it.ktClass }, expectedToRemove = { null }, filename = "Bar.kt")
        }
    }

    @RunWith(JUnit3RunnerWithInners::class)
    class KtFileAndKtClassForFacade : KtSearchEverywhereEqualityProviderTest() {
        fun `test KtFile and KtClassForFacade should be deduplicated`() {
            doTest({ it.ktFile to it.facadeClass }, expectedToRemove = { it.facadeClass }, withFacade = true)
        }

        fun `test KtFile and KtClassForFacade shouldnt be deduplicated when non default JvmName is used`() {
            doTest({ it.ktFile to it.facadeClass }, expectedToRemove = { null }, withFacade = true, facadeName = "Bar")
        }
    }

    @RunWith(JUnit3RunnerWithInners::class)
    class KtClassAndKtClassForFacade : KtSearchEverywhereEqualityProviderTest() {
        fun `test KtClass and KtClassForFacade should be deduplicated`() {
            doTest({ it.ktClass to it.facadeClass }, expectedToRemove = { it.facadeClass }, withFacade = true)
        }

        fun `test KtClass and KtClassForFacade shouldnt be deduplicated when non default JvmName is used`() {
            doTest({ it.ktClass to it.facadeClass }, expectedToRemove = { null }, withFacade = true, facadeName = "Bar")
        }

        fun `test KtClass and KtClassForFacade shouldnt be deduplicated if filename doesnt match`() {
            doTest({ it.ktClass to it.facadeClass }, expectedToRemove = { null }, withFacade = true, filename = "Bar.kt")
        }
    }

    @RunWith(JUnit3RunnerWithInners::class)
    class NativePsiAndUlc : KtSearchEverywhereEqualityProviderTest() {
        fun `test ulc should be skipped`() {
            doTest({ it.ktClass to it.ktClass.ulc }, expectedToRemove = { it.ktClass.ulc })
        }

        fun `test ktClassUlc and ktFile should be deduplicated`() {
            doTest({ it.ktFile to it.ktClass.ulc }, expectedToRemove = { it.ktFile })
        }

        fun `test ktClassUlc and KtClassForFacade should be deduplicated`() {
            doTest({ it.facadeClass to it.ktClass.ulc }, withFacade = true, expectedToRemove = { it.facadeClass })
        }
    }

    @RunWith(JUnit3RunnerWithInners::class)
    class EqualElements : KtSearchEverywhereEqualityProviderTest() {
        fun `test equals KtClass and KtClass should be skipped`() {
            doTest({ it.ktClass to it.ktClass }, expectedToRemove = { null })
        }

        fun `test equals KtClassUlc and KtClassUlc should be skipped`() {
            doTest({ it.ktClass.ulc to it.ktClass.ulc }, expectedToRemove = { null })
        }

        fun `test equals KtClassForFacade and KtClassForFacade should be skipped`() {
            doTest({ it.facadeClass to it.facadeClass }, withFacade = true, expectedToRemove = { null })
        }

        fun `test equals KtFile and KtFile should be skipped`() {
            doTest({ it.ktFile to it.ktFile }, expectedToRemove = { null })
        }
    }

    protected val KtClassOrObject.ulc
        get() = LightClassGenerationSupport.getInstance(project).createUltraLightClass(this)!!

    protected val PsiFile.ktFile
        get() = findElementAt(myFixture.caretOffset)?.parentOfType<KtFile>()!!

    protected val PsiFile.ktClass
        get() = findElementAt(myFixture.caretOffset)?.parentOfType<KtClass>()!!

    protected val PsiFile.facadeClass
        get() = findElementAt(myFixture.caretOffset)?.parentOfType<KtFile>()!!.findFacadeClass()!!

    protected fun doTest(
        items: (file: PsiFile) -> Pair<PsiElement, PsiElement>,
        expectedToRemove: (file: PsiFile) -> PsiElement?,
        filename: String = "Foo.kt",
        facadeName: String? = null,
        withFacade: Boolean = false,
    ) {
        val psiFile = myFixture.configureByText(
            filename,
            """
                ${facadeName?.let { """@file:JvmName("$facadeName")""" } ?: ""}
                ${if (withFacade) "fun foo() = Unit // Force to create KtLightClassForFacade" else ""}
                class F<caret>oo
            """.trimIndent()
        )

        val (x, y) = items(psiFile)

        val action1 = KtSearchEverywhereEqualityProvider().compareItems(
            newItem = SearchEverywhereFoundElementInfo(x, 0, null),
            alreadyFoundItems = listOf(SearchEverywhereFoundElementInfo(y, 0, null))
        )
        val action2 = KtSearchEverywhereEqualityProvider().compareItems(
            newItem = SearchEverywhereFoundElementInfo(y, 0, null),
            alreadyFoundItems = listOf(SearchEverywhereFoundElementInfo(x, 0, null))
        )
        if (action1 == DoNothing || action2 == DoNothing) assertTrue(action1 == DoNothing && action2 == DoNothing)
        if (x === y) {
            assertTrue(action1 == Skip)
            assertTrue(action2 == Skip)
        } else {
            if (action1 == Skip) assertTrue(action2 is Replace)
            if (action2 == Skip) assertTrue(action1 is Replace)
        }

        val actualRemoved = listOf(action1, action2)
            .filterIsInstance<Replace>()
            .flatMapTo(mutableSetOf()) { it.toBeReplaced }
            .map { it.element as PsiElement }
            .singleOrNull()
        TestCase.assertEquals(expectedToRemove(file), actualRemoved)
    }
}
