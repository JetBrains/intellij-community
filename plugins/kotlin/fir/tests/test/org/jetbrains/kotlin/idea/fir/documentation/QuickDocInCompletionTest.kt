// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.fir.documentation

import com.intellij.lang.documentation.ide.IdeDocumentationTargetProvider
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.test.TestMetadata
import org.junit.Assert
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@TestRoot("idea/tests")
@TestMetadata("testData/kdoc/inCompletion")
@RunWith(JUnit38ClassRunner::class)
class QuickDocInCompletionTest : KotlinLightCodeInsightFixtureTestCase() {
    override val pluginMode: KotlinPluginMode = KotlinPluginMode.K2

    override fun getProjectDescriptor() = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()

    fun testSimple() {
        val element = getElementFromLookup()
        Assert.assertTrue(element is KtClass)
    }

    fun testProp() {
        val element = getElementFromLookup()
        Assert.assertTrue(element is KtProperty)
    }

    private fun getElementFromLookup(): PsiElement? {
        myFixture.configureByFile(getTestName(true) + ".kt")
        val lookupElements = myFixture.completeBasic()
        val lookupElement = lookupElements.first()
        val target = IdeDocumentationTargetProvider.getInstance(project)
            .documentationTargets(editor, file, lookupElement)
            .firstOrNull() ?: return null
        return target.navigatable as? PsiElement
    }
}
