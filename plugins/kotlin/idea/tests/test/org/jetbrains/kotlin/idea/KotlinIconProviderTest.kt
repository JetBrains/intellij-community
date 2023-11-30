// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea

import com.intellij.openapi.util.Iconable
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.ui.icons.RowIcon
import com.intellij.util.PsiIconUtil
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import javax.swing.Icon

class KotlinIconProviderTest: KotlinLightCodeInsightFixtureTestCase() {
    fun testPublicClassFile() {
        createFileAndCheckIcon("Foo.kt", "class Foo", "org/jetbrains/kotlin/idea/icons/classKotlin.svg", "nodes/c_public.svg")
    }

    fun testFileSingleFunction() {
        createFileAndCheckIcon("Foo.kt", "fun foo() = TODO()", "org/jetbrains/kotlin/idea/icons/kotlin_file.svg")
    }

    fun testClassAndTypeAlias() {
        val fileBody = """
            typealias F = Foo
            class Foo
        """.trimIndent()
        createFileAndCheckIcon("Foo.kt", fileBody, "org/jetbrains/kotlin/idea/icons/classKotlin.svg", "nodes/c_public.svg")
    }

    fun testClassAndPrivateFunction() {
        val fileBody = """
            class Foo
            private fun bar() {}
        """.trimIndent()
        createFileAndCheckIcon("Foo.kt", fileBody, "org/jetbrains/kotlin/idea/icons/classKotlin.svg", "nodes/c_public.svg")
    }

    fun testClassAndInternalFunction() {
        val fileBody = """
            class Foo
            internal fun bar() {}
        """.trimIndent()
        createFileAndCheckIcon("Foo.kt", fileBody, "org/jetbrains/kotlin/idea/icons/kotlin_file.svg")
    }

    fun testJavaBase() {
        val aClass = myFixture.addClass("public class BaseJavaClass {}")
        myFixture.addFileToProject("foo.kt", "class Foo : BaseJavaClass() {}")
        val psiClass = ClassInheritorsSearch.search(aClass).findFirst()!!
        val icon = PsiIconUtil.getProvidersIcon(psiClass, Iconable.ICON_FLAG_VISIBILITY or Iconable.ICON_FLAG_READ_STATUS)
        val iconString = (icon.safeAs<RowIcon>()?.allIcons?.joinToString(transform = Icon::toString) ?: icon)?.toString()
        assertEquals("org/jetbrains/kotlin/idea/icons/classKotlin.svg", iconString)
    }

    private fun createFileAndCheckIcon(fileName: String, fileBody: String, vararg icons: String) {
        val psiFile = myFixture.configureByText(fileName, fileBody)
        val icon = PsiIconUtil.getProvidersIcon(psiFile, Iconable.ICON_FLAG_VISIBILITY or Iconable.ICON_FLAG_READ_STATUS)
        val iconString = (icon.safeAs<RowIcon>()?.allIcons?.joinToString(transform = Icon::toString) ?: icon)?.toString()
        assertEquals(icons.joinToString(), iconString)
    }
}