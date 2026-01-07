// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.quickDoc

import com.intellij.codeInsight.documentation.DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL
import com.intellij.lang.documentation.ide.IdeDocumentationTargetProvider
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.platform.backend.documentation.impl.resolveLink
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.idea.codeinsight.utils.findExistingEditor
import org.jetbrains.kotlin.idea.k2.codeinsight.quickDoc.resolveKDocLink
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.kotlin.test.util.invalidateCaches
import org.junit.Assert
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@TestRoot("idea/tests")
@TestMetadata("testData/kdoc/navigate")
@RunWith(JUnit38ClassRunner::class)
class QuickDocNavigationTest() : KotlinLightCodeInsightFixtureTestCase() {
    override fun getProjectDescriptor() = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()

    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K2

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
        assertInstanceOf(target, PsiClass::class.java)
        Assert.assertEquals("ArrayList", (target as PsiClass).name)
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

    fun testTopLevelExtensionPropertyWithThis() {
        val target = resolveDocLink("this")
        assertInstanceOf(target, KtTypeReference::class.java)
        Assert.assertEquals("A", (target as KtTypeReference).text)
    }

    fun testResolveFromJava() {
        val files = myFixture.configureByFiles(
            getTestName(false) + "_Java.java",
            getTestName(false) + "_Kotlin.kt"
        )

        val editor = files.first().findExistingEditor()
        assertNotNull(editor)
        
        val targets = IdeDocumentationTargetProvider.getInstance(project).documentationTargetsForInlineDoc(
            editor!!,
            files.first(),
            files.first().findElementAt(editor.caretModel.offset)!!.startOffset
        )
        Assert.assertTrue("No documentation target available", targets.isNotEmpty())

        val resolved = resolveLink(targets.first(), "${PSI_ELEMENT_PROTOCOL}kotlinFun2")
        assertNotNull("The link isn't resolved", resolved)
    }

    private fun resolveDocLink(linkText: String): PsiElement? {
        myFixture.configureByFile(getTestName(true) + ".kt")
        val source = myFixture.elementAtCaret.getParentOfType<KtDeclaration>(false)
        return ActionUtil.underModalProgress(project, "") { resolveKDocLink(linkText.split('.'), source!!) }
    }
}
