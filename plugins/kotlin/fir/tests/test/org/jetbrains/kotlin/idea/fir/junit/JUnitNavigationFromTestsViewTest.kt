// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.junit

import com.intellij.execution.junit2.info.MethodLocation
import com.intellij.execution.testframework.JavaTestLocator
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import junit.framework.TestCase
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase

internal class JUnitNavigationFromTestsViewTest: KotlinLightCodeInsightFixtureTestCase() {
    override val pluginMode: KotlinPluginMode = KotlinPluginMode.K2

    fun testNavigationWithMethodWithTailingTraces() {
        myFixture.addFileToProject("ATest.kt",
            "class ATest: junit.framework.TestCase() {" +
                    "  fun `testMe with trailing space `() {}\n" +
                    "}"
        )

        val testProxy = SMTestProxy("testMe", false, "java:test://ATest/testMe with trailing space ")
        val project = getProject()
        val searchScope = GlobalSearchScope.projectScope(project)
        testProxy.setLocator(JavaTestLocator.INSTANCE)

        val location = testProxy.getLocation(project, searchScope)
        assertNotNull(location)
        assertInstanceOf(location, MethodLocation::class.java)

        val element: PsiElement = location!!.getPsiElement()
        assertInstanceOf(element, PsiMethod::class.java)
        TestCase.assertEquals("testMe with trailing space ", (element as PsiMethod).getName())
        val containingClass = element.getContainingClass()
        assertNotNull(containingClass)
        TestCase.assertEquals("ATest", containingClass!!.getQualifiedName())
    }
}