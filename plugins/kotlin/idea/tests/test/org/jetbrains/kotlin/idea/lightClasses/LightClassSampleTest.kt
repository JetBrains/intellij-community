// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.lightClasses

import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiClass
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.UsefulTestCase
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.asJava.classes.KtLightClassForSourceDeclaration
import org.jetbrains.kotlin.idea.lightClasses.LightClassEqualsTest.doTestEquals
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class LightClassSampleTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun getProjectDescriptor(): LightProjectDescriptor {
        return KotlinWithJdkAndRuntimeLightProjectDescriptor.INSTANCE
    }

    fun testDeprecationLevelHIDDEN() {
        // KT27243
        myFixture.configureByText(
            "a.kt", """
                interface A {
                    @Deprecated(level = DeprecationLevel.HIDDEN, message = "nothing")
                    fun foo()
                }
            """.trimMargin()
        )

        myFixture.configureByText(
            "b.kt", """
                class B : A { 
                    override fun foo() {} 
                }
            """.trimIndent()
        )

        doTestAndCheck("B", "foo", 0)
    }

    fun testJvmSynthetic() {
        // KT33561
        myFixture.configureByText(
            "foo.kt", """
                class Foo {
                    @JvmSynthetic
                    inline fun foo(crossinline getter: () -> String) = foo(getter())
                
                    fun foo(getter: String) = println(getter)
                }
            """.trimIndent()
        )

        doTestAndCheck("Foo", "foo", 1)
    }

    @Suppress("SameParameterValue")
    private fun doTestAndCheck(className: String, methodName: String, methods: Int) {
        withLightClasses {
            val theClass: PsiClass = myFixture.javaFacade.findClass(className)
            assertNotNull(theClass)
            UsefulTestCase.assertInstanceOf(
                theClass,
                KtLightClassForSourceDeclaration::class.java
            )
            doTestEquals((theClass as KtLightClass).kotlinOrigin)
            assertEquals(methods, (theClass.allMethods + theClass.allMethods.flatMap { it.findSuperMethods().toList() })
                .count { it.name == methodName })
        }

    }


    private fun withLightClasses(block: () -> Any) {
        val registryValue = Registry.get("kotlin.use.ultra.light.classes")
        val initialValue = registryValue.asBoolean()
        registryValue.setValue(false)
        try {
            block()
        } finally {
            registryValue.setValue(initialValue)
        }
    }

}