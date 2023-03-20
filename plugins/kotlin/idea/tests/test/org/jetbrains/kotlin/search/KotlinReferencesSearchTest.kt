// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.search

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.jetbrains.kotlin.idea.references.KtDestructuringDeclarationReference
import org.jetbrains.kotlin.idea.references.KtInvokeFunctionReference
import org.jetbrains.kotlin.idea.search.usagesSearch.ExpressionsOfTypeProcessor
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.test.TestMetadata
import org.junit.Assert
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@TestRoot("idea/tests")
@TestMetadata("testData/search/references")
@RunWith(JUnit38ClassRunner::class)
class KotlinReferencesSearchTest : AbstractSearcherTest() {
    fun testPlus() {
        val refs = doTest<KtFunction>()
        Assert.assertEquals(3, refs.size)
        Assert.assertEquals("+", refs[0].canonicalText)
        Assert.assertEquals("plus", refs[1].canonicalText)
        Assert.assertEquals("plus", refs[2].canonicalText)
    }

    fun testParam() {
        val refs = doTest<KtParameter>()
        Assert.assertEquals(3, refs.size)
        Assert.assertEquals("n", refs[0].canonicalText)
        Assert.assertEquals("component1", refs[1].canonicalText)
        Assert.assertTrue(refs[2] is KtDestructuringDeclarationReference)
    }

    fun testComponentFun() {
        val refs = doTest<KtFunction>()
        Assert.assertEquals(2, refs.size)
        Assert.assertEquals("component1", refs[0].canonicalText)
        Assert.assertTrue(refs[1] is KtDestructuringDeclarationReference)
    }

    fun testInvokeFun() {
        val refs = doTest<KtFunction>()
        Assert.assertEquals(2, refs.size)
        Assert.assertEquals("invoke", refs[0].canonicalText)
        Assert.assertTrue(refs[1] is KtInvokeFunctionReference)
    }

    // workaround for KT-9788 AssertionError from backand when we read field from inline function
    private val myFixtureProxy: JavaCodeInsightTestFixture get() = myFixture

    private inline fun <reified T : PsiElement> doTest(): List<PsiReference> {
        val psiFile = myFixtureProxy.configureByFile(dataFile())
        val func = myFixtureProxy.elementAtCaret.getParentOfType<T>(false)!!
        val refs = ReferencesSearch.search(func).findAll().sortedBy { it.element.textRange.startOffset }

        // check that local reference search gives the same result
        try {
            ExpressionsOfTypeProcessor.prodMode()
            val localRefs = ReferencesSearch.search(func, LocalSearchScope(psiFile)).findAll()
            Assert.assertEquals(refs.size, localRefs.size)
        } finally {
            ExpressionsOfTypeProcessor.resetMode()
        }

        return refs
    }
}
