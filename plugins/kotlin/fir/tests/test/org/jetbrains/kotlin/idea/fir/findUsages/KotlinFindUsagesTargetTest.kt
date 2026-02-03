// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.findUsages

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.usages.PsiElementUsageTarget
import com.intellij.usages.UsageTargetUtil
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPrimaryConstructor

class KotlinFindUsagesTargetTest: KotlinLightCodeInsightFixtureTestCase() {

    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K2

    fun testPrimaryConstructorValProperty() {
        doTestPosition("class Foo(<caret>val x: Int)")
    }

    fun testPrimaryConstructorVarProperty() {
        doTestPosition("class Foo(<caret>var x: Int)")
    }

    fun testPrimaryConstructorParam() {
        myFixture.configureByText("Foo.kt", "class Foo(<caret>x: Int)")
        val dataContext = (myFixture.editor as EditorEx).getDataContext()
        val editor = CommonDataKeys.EDITOR.getData(dataContext)
        val psiFile = CommonDataKeys.PSI_FILE.getData(dataContext)
        val psiElement = CommonDataKeys.PSI_ELEMENT.getData(dataContext)
        val targets = UsageTargetUtil.findUsageTargets(editor, psiFile, psiElement)
        assertNotNull(targets)
        assertEquals(1, targets!!.size)
        assertInstanceOf(targets[0], PsiElementUsageTarget::class.java)
        val element = (targets[0] as PsiElementUsageTarget).element
        assertTrue(element is KtParameter)
        assertEquals("x", (element as KtParameter).name)
    }

    private fun doTestPosition(fileText: String) {
        myFixture.configureByText("Foo.kt", fileText)
        val dataContext = (myFixture.editor as EditorEx).getDataContext()
        val editor = CommonDataKeys.EDITOR.getData(dataContext)
        val psiFile = CommonDataKeys.PSI_FILE.getData(dataContext)
        val psiElement = CommonDataKeys.PSI_ELEMENT.getData(dataContext)
        val targets = UsageTargetUtil.findUsageTargets(editor, psiFile, psiElement)
        assertNotNull(targets)
        assertEquals(1, targets!!.size)
        assertInstanceOf(targets[0], PsiElementUsageTarget::class.java)
        val element = (targets[0] as PsiElementUsageTarget).element
        assertTrue(element is KtPrimaryConstructor)
        assertEquals("Foo", (element.parent as KtClass).name)
    }
}