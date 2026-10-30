// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.fir.actions

import com.intellij.ide.actions.CopyReferenceAction
import com.intellij.ide.actions.QualifiedNameProviderUtil
import com.intellij.psi.PsiElement
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class QualifiedNamesTest : KotlinLightCodeInsightFixtureTestCase() {

    // a test name ending with "Runtime" gets the stdlib (so `@JvmField` resolves); others keep the default descriptor
    override fun getProjectDescriptor(): LightProjectDescriptor = getProjectDescriptorFromTestName()

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

    fun testValueClassParameter() {
        myFixture.configureByText(
            "valueClass.kt",
            """
                package foo.bar

                @JvmInline
                value class Id(val value: String)

                interface Service {
                    fun find(id: Id): String?

                    val current: Id
                }

                class Klass {
                    fun memberFun(id: Id) {
                    }

                    val memberVal: Id = Id(":)")

                    val computedVal: Id get() = Id(":)")

                    companion object {
                        fun companionFun(id: Id) {
                        }
                    }
                }

                fun topLevelFun(id: Id) {
                }

                val service: Service = object : Service {
                    override fun find(id: Id): String? = null

                    override val current: Id get() = Id(":)")
                }
            """.trimIndent()
        )
        assertEquals(
            listOf(
                "foo.bar.Id",
                null, // the primary constructor of a value class has no light method
                "foo.bar.Service",
                "foo.bar.Service#find",
                "foo.bar.Service#getCurrent",
                "foo.bar.Klass",
                "foo.bar.Klass#memberFun",
                "foo.bar.Klass#memberVal", // the light getter is missing, but the light backing field names it
                "foo.bar.Klass#getComputedVal",
                "foo.bar.Klass.Companion",
                "foo.bar.Klass.Companion#companionFun",
                "foo.bar.ValueClassKt#topLevelFun",
                "foo.bar.ValueClassKt#getService",
                null,
                "foo.bar.Service#find",
                "foo.bar.Service#getCurrent",
            ),
            getQualifiedNamesForDeclarations { it !is KtParameter }
        )
    }

    fun testConstructorPropertiesAndTypeAliasWithRuntime() {
        myFixture.configureByText(
            "ctor.kt",
            """
                package foo.bar

                class Point(
                    val ctorVal: Int,
                    var ctorVar: Int,
                    private val ctorPrivate: Int,
                    @JvmField val ctorField: Int,
                    plainParam: Int,
                )

                data class Entry(val key: String)

                annotation class Marker(val level: Int)

                typealias Names = List<String>
            """.trimIndent()
        )
        assertEquals(
            listOf(
                "foo.bar.Point",
                "foo.bar.Point#getCtorVal",
                "foo.bar.Point#getCtorVar",
                "foo.bar.Point#ctorPrivate", // private: no getter, the backing field names it
                "foo.bar.Point#ctorField",
                null, // a plain constructor parameter is not a member
                "foo.bar.Entry",
                "foo.bar.Entry#getKey",
                "foo.bar.Marker",
                "foo.bar.Marker#level", // annotation parameter: the method has no `get` prefix
                "foo.bar.Names",
            ),
            getQualifiedNamesForDeclarations { it !is KtPrimaryConstructor }
        )
    }

    private fun getQualifiedNamesForDeclarations(filter: (KtNamedDeclaration) -> Boolean = { true }): List<String?> {
        val result = ArrayList<String?>()
        file.accept(object : KtVisitorVoid() {
            override fun visitElement(element: PsiElement) {
                element.acceptChildren(this)
            }

            override fun visitNamedDeclaration(declaration: KtNamedDeclaration) {
                if (filter(declaration)) result.add(CopyReferenceAction.elementToFqn(declaration))
                super.visitNamedDeclaration(declaration)
            }
        })
        return result
    }
}
