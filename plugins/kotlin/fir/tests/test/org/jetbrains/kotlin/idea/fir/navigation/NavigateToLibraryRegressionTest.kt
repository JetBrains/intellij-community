// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.fir.navigation

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.invalidateLibraryCache
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
open class NavigateToLibraryRegressionTest : LightJavaCodeInsightFixtureTestCase(), ExpectedPluginModeProvider {
    override val pluginMode: KotlinPluginMode = KotlinPluginMode.K2

    override fun setUp() {
        setUpWithKotlinPlugin { super.setUp() }
        invalidateLibraryCache(project)
    }

    fun testRefToStdlib() {
        val navigationElement = configureAndResolve("fun foo() { <caret>println() }")
        assertSame(KotlinLanguage.INSTANCE, navigationElement.language)
    }

    fun testRefToJdk() {
        configureAndResolve("val x = java.util.HashMap<String, Int>().<caret>get(\"\")")
    }

    fun testRefToClassesWithAltSignatureAnnotations() {
        val navigationElement = configureAndResolve("fun foo(e : java.util.Map.Entry<String, String>) { e.<caret>getKey(); }")
        val expectedClass = JavaPsiFacade.getInstance(project).findClass("java.util.Map.Entry", GlobalSearchScope.allScope(project))
        assertSame(expectedClass, navigationElement.parent)
    }

    fun testRefToFunctionWithVararg() {
        val navigationElement = configureAndResolve("val x = <caret>arrayListOf(\"\", \"\")")
        assertSame(KotlinLanguage.INSTANCE, navigationElement.language)
    }

    override fun getProjectDescriptor(): LightProjectDescriptor =
        KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()

    protected fun configureAndResolve(text: String): PsiElement {
        myFixture.configureByText(KotlinFileType.INSTANCE, text)
        val ref = myFixture.file.findReferenceAt(myFixture.caretOffset)
        return ref!!.resolve()!!.navigationElement
    }
}
