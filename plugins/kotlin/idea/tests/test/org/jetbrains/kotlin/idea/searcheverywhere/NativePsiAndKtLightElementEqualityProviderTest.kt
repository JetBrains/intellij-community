// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.searcheverywhere

import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.utils.addToStdlib.cast

/**
 * @see KtSearchEverywhereEqualityProvider
 */
class NativePsiAndKtLightElementEqualityProviderTest : KotlinSearchEverywhereTestCase() {
    fun `test only class presented`() {
        val file = myFixture.configureByText("MyKotlinClassWithStrangeName.kt", "class MyKotlinClassWithStrangeName")
        val klass = file.findElementAt(myFixture.caretOffset)?.parentOfType<KtClass>()!!
        val ulc = klass.toLightClass()!!
        findPsiByPattern("MyKotlinClassWithStrangeName") { results ->
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
        val ulc = klass.toLightClass()!!
        val ulc2 = klass2.toLightClass()!!
        findPsiByPattern("MyKotlinClassWithStrangeName") { results ->
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
        val ulc = klass.toLightClass()!!
        val syntheticClass = file.declarations.last().cast<KtNamedFunction>().toLightMethods().single().parent
        findPsiByPattern("MyKotlinClassWithStrangeName") { results ->
            assertTrue(results.toString(), results.size == 1)
            assertTrue(klass in results)
            assertFalse(file in results)
            assertFalse(syntheticClass in results)
            assertFalse(ulc in results)
        }
    }
}
