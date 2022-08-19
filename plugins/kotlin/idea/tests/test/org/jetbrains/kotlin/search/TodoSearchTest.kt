// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.search

import com.intellij.psi.search.PsiTodoSearchHelper
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinLightProjectDescriptor
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.test.TestMetadata
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@TestRoot("idea/tests")
@TestMetadata("testData/search/todo")
@RunWith(JUnit38ClassRunner::class)
class TodoSearchTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun getProjectDescriptor(): KotlinLightProjectDescriptor = KotlinLightProjectDescriptor.INSTANCE

    fun testTodoCall() {
        val file = myFixture.configureByFile("todoCall.kt")
        val todoItems = PsiTodoSearchHelper.getInstance(myFixture.project).findTodoItems(file)

        val actualItems = todoItems.map { it.textRange.substring(it.file.text) }
        assertOrderedEquals(
            listOf(
                "TODO(\"Fix me\")",
                "TODO()",
                "TODO(\"Fix me in lambda\")"
            ).sorted(),
            actualItems.sorted()
        )
    }

    fun testTodoDef() {
        val file = myFixture.configureByFile("todoDecl.kt")
        assertEquals(0, PsiTodoSearchHelper.getInstance(myFixture.project).getTodoItemsCount(file))
    }
}
