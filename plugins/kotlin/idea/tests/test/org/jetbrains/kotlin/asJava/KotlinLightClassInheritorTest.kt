// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.asJava

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinLightProjectDescriptor
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.junit.Assert
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class KotlinLightClassInheritorTest11 : KotlinLightCodeInsightFixtureTestCase() {
    fun testAnnotation() {
        doTestInheritorByText("annotation class A", "java.lang.annotation.Annotation", false)
    }

    fun testEnum() {
        doTestInheritorByText("enum class A", "java.lang.Enum", false)
    }

    fun testIterable() {
        doTestInheritorByText("abstract class A<T> : Iterable<T>", "java.lang.Iterable", false)
    }

    fun testIterableDeep() {
        doTestInheritorByText("abstract class A<T> : List<T>", "java.lang.Iterable", true)
    }

    fun testObjectDeep() {
        doTestInheritorByText("abstract class A<T> : List<T>", "java.lang.Object", true)
    }

    fun testClassOnSelfDeep() {
        doTestInheritorByText("class A", "A", checkDeep = true, result = false)
    }

    private fun doTestInheritorByText(text: String, superQName: String, checkDeep: Boolean, result: Boolean = true) {
        val file = myFixture.configureByText("A.kt", text) as KtFile
        val ktClass = file.declarations.filterIsInstance<KtClass>().single()
        val psiClass = KotlinAsJavaSupport.getInstance(project).getLightClass(ktClass)!!
        val baseClass = JavaPsiFacade.getInstance(project).findClass(superQName, GlobalSearchScope.allScope(project))!!
        Assert.assertEquals(psiClass.isInheritor(baseClass, checkDeep), result)
    }

    override fun getProjectDescriptor(): LightProjectDescriptor = KotlinLightProjectDescriptor.INSTANCE
}
