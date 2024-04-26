// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.editor.quickDoc

import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.psi.PsiElement
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.k2.codeinsight.quickDoc.resolveKDocLink
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtTypeAlias
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.test.TestMetadata
import org.junit.Assert
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@TestRoot("idea/tests")
@TestMetadata("testData/kdoc/navigate")
@RunWith(JUnit38ClassRunner::class)
class QuickDocNavigationTest() : KotlinLightCodeInsightFixtureTestCase() {
    override fun getProjectDescriptor() = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()

    override fun isFirPlugin(): Boolean {
        return true
    }

    override fun tearDown() {
        runAll(
            { runInEdtAndWait { project.invalidateCaches() } },
            { super.tearDown() }
        )
    }

    fun testSimple() {
        val target = resolveDocLink("C")
        assertInstanceOf(target, KtClass::class.java)
        Assert.assertEquals("C", (target as KtClass).name)
    }

    fun testJdkClass() {
        val target = resolveDocLink("ArrayList")
        assertInstanceOf(target, KtTypeAlias::class.java)
        Assert.assertEquals("ArrayList", (target as KtTypeAlias).name)
    }

    fun testStdlibFunction() {
        val target = resolveDocLink("reader")
        assertInstanceOf(target, KtFunction::class.java)
        Assert.assertEquals("reader", (target as KtFunction).name)
    }

    fun testQualifiedName() {
        val target = resolveDocLink("a.b.c.D")
        assertInstanceOf(target, KtClass::class.java)
        Assert.assertEquals("D", (target as KtClass).name)
    }

    fun testTopLevelFun() {
        val target = resolveDocLink("doc.topLevelFun")
        assertInstanceOf(target, KtFunction::class.java)
        Assert.assertEquals("topLevelFun", (target as KtFunction).name)
    }

    fun testTopLevelFunShortName() {
        val target = resolveDocLink("topLevelFun")
        assertInstanceOf(target, KtFunction::class.java)
        Assert.assertEquals("topLevelFun", (target as KtFunction).name)
    }

    fun testTopLevelFunParameterName() {
        val target = resolveDocLink("x")
        assertInstanceOf(target, KtParameter::class.java)
        Assert.assertEquals("x", (target as KtParameter).name)
    }

    fun testTopLevelProperty() {
        val target = resolveDocLink("doc.topLevelProperty")
        assertInstanceOf(target, KtProperty::class.java)
        Assert.assertEquals("topLevelProperty", (target as KtProperty).name)
    }

    private fun resolveDocLink(linkText: String): PsiElement? {
        myFixture.configureByFile(getTestName(true) + ".kt")
        val source = myFixture.elementAtCaret.getParentOfType<KtDeclaration>(false)
        return ActionUtil.underModalProgress(project, "") { resolveKDocLink(linkText.split('.'), source!!) }
    }
}
