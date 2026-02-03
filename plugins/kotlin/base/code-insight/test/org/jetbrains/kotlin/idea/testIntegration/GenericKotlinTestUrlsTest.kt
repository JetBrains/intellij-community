// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.testIntegration

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.test.KotlinPluginUnitTest
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.findFunctionByName
import kotlin.test.assertEquals


class GenericKotlinTestUrlsTest {

    @KotlinPluginUnitTest
    fun testSimpleClass(project: Project) = ApplicationManager.getApplication().runReadAction {
        val ktClass = KtPsiFactory(project).createClass("class A")
        assertEquals(listOf("java:suite://A"), ktClass.genericKotlinTestUrls())
    }

    @KotlinPluginUnitTest
    fun testClassInPackage(project: Project) = ApplicationManager.getApplication().runReadAction {
        val ktClass = KtPsiFactory(project).createClass("package a.b.c; class A")
        assertEquals(listOf("java:suite://a.b.c.A"), ktClass.genericKotlinTestUrls())
    }

    @KotlinPluginUnitTest
    fun testNestedClass(project: Project) = ApplicationManager.getApplication().runReadAction {
        val ktClass = KtPsiFactory(project).createClass("class A { class B }")
        val bKtClass = ktClass.declarations.find { it.name == "B" } as? KtClass ?: error("Cannot find B")
        assertEquals(listOf("java:suite://A.B"), bKtClass.genericKotlinTestUrls())
    }

    @KotlinPluginUnitTest
    fun testFun(project: Project) = ApplicationManager.getApplication().runReadAction {
        val ktClass = KtPsiFactory(project).createClass(
            """
             package a.b.c
             class Foo {
                 fun foo()
             }
         """.trimIndent()
        )

        val foo = ktClass.findFunctionByName("foo") ?: error("Cannot find foo()")
        assertEquals(listOf("java:test://a.b.c.Foo/foo"), foo.genericKotlinTestUrls())
    }
}