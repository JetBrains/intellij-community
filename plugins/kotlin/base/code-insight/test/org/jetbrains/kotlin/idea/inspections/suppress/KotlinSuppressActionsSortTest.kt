// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.inspections.suppress

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.testFramework.LeakHunter
import com.intellij.testFramework.runInEdtAndGet
import org.jetbrains.kotlin.idea.test.KotlinPluginUnitTest
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.junit.jupiter.api.AfterAll
import kotlin.test.assertEquals

class KotlinSuppressActionsSortTest {

    @KotlinPluginUnitTest
    fun testStatementFirstThenFunctionClassFile(project: Project) = runReadActionBlocking {
        val ktClass = classBar(project, "fun foo() { 42 }")
        assertEquals(
            listOf(PRIORITY_STATEMENT, PRIORITY_MEMBER, PRIORITY_CLASS, PRIORITY_FILE),
            suppressPriorities(ktClass.funFoo().singleStatement())
        )
    }

    @KotlinPluginUnitTest
    fun testParameterBeforeFunctionAndClass(project: Project) = runReadActionBlocking {
        val ktClass = classBar(project, "fun foo(p: Int) {}")
        val parameter = ktClass.funFoo().valueParameters.single()
        assertEquals(
            listOf(PRIORITY_PARAMETER, PRIORITY_MEMBER, PRIORITY_CLASS, PRIORITY_FILE), suppressPriorities(parameter)
        )
    }

    private fun classBar(project: Project, body: String) = KtPsiFactory(project).createClass("class Bar { $body }")

    private fun KtClass.funFoo() = declarations.single() as KtNamedFunction

    private fun KtNamedFunction.singleStatement() = (bodyBlockExpression ?: error("expected block body")).statements.single()

    private fun suppressPriorities(element: PsiElement) =
        KotlinInspectionSuppressor().getSuppressActions(element, "unused").map { it.priority }

    companion object {
        @JvmStatic
        @AfterAll
        fun cleanupProject() {
            runInEdtAndGet {
                LeakHunter.cleanupAllProjects()
            }
        }
    }
}
