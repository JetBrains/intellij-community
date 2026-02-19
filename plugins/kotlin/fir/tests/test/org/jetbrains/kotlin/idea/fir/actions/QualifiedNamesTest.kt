// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.fir.actions

import com.intellij.ide.actions.CopyReferenceAction
import com.intellij.ide.actions.QualifiedNameProviderUtil
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class QualifiedNamesTest : KotlinLightCodeInsightFixtureTestCase() {

    override val pluginMode: KotlinPluginMode = KotlinPluginMode.K2

    fun testClassRef() {
        myFixture.configureByText(
            "class.kt",
            """
                package foo.bar

                class Klass {
                    class Nested

                    companion object {
                    }
                }

                object Object {
                }

                val anonymous = object {
                }
            """.trimIndent()
        )
        assertEquals(
            listOf(
                "foo.bar.Klass",
                "foo.bar.Klass.Nested",
                "foo.bar.Klass.Companion",
                "foo.bar.Object",
                "foo.bar.ClassKt#getAnonymous",
                null
            ),
            getQualifiedNamesForDeclarations()
        )
    }

    fun testOnClassRef() {
        val file = myFixture.configureByText(
            "Klass.kt",
            """
                package foo.bar

                class Klass {}

                fun m() = Kla<caret>ss()
            """.trimIndent()
        )
        val element = file.findElementAt(myFixture.caretOffset)?.parent ?: error("Cannot find reference at caret")
        assertEquals("foo.bar.Klass", QualifiedNameProviderUtil.getQualifiedName(element))
    }

    fun testFunRef() {
        myFixture.configureByText(
            "fun.kt",
            """
                package foo.bar

                class Klass {
                    fun memberFun() {
                    }

                    val memberVal = ":)"
                }

                fun topLevelFun()

                val topLevelVal = ":)"
            """.trimIndent()
        )
        assertEquals(
            listOf(
                "foo.bar.Klass",
                "foo.bar.Klass#memberFun",
                "foo.bar.Klass#getMemberVal",
                "foo.bar.FunKt#topLevelFun",
                "foo.bar.FunKt#getTopLevelVal"
            ),
            getQualifiedNamesForDeclarations()
        )
    }




    private fun getQualifiedNamesForDeclarations(): List<String?> {
        val result = ArrayList<String?>()
        file.accept(object : KtVisitorVoid() {
            override fun visitElement(element: PsiElement) {
                element.acceptChildren(this)
            }

            override fun visitNamedDeclaration(declaration: KtNamedDeclaration) {
                result.add(CopyReferenceAction.elementToFqn(declaration))
                super.visitNamedDeclaration(declaration)
            }
        })
        return result
    }
}
