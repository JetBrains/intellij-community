// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.move

import com.intellij.psi.PsiElement
import com.intellij.refactoring.move.MoveHandlerDelegate
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase

class ApplicableMoveHandlersTest : KotlinLightCodeInsightFixtureTestCase() {
    fun testFile() {
        val kotlinFile = myFixture.addFileToProject(
            "a/kotlinFile.kt",
            """
                package a
                class One
                class Two
            """.trimIndent(),
        )

        val anotherFile = myFixture.addFileToProject("b/anotherFile.kt", "package b")
        val targetDirectory = anotherFile.containingDirectory
        val kotlinFileAsElements: Array<PsiElement> = arrayOf(kotlinFile)
        val expectedHandlers = listOf(
            "MoveKotlinDeclarationsHandler" to null,
            "KotlinMoveFilesOrDirectoriesHandler" to "Move File…",
            "JavaMoveClassesOrPackagesHandler" to "Move Classes...",
            "JavaMoveFilesOrDirectoriesHandler" to "Move File…",
            "MoveFilesOrDirectoriesHandler" to "Move File…",
        )

        assertApplicableHandlers(
            sourceElements = kotlinFileAsElements,
            targetElement = targetDirectory,
            expectedHandlers = expectedHandlers,
        )

        assertApplicableHandlers(
            sourceElements = kotlinFileAsElements,
            targetElement = null,
            expectedHandlers = expectedHandlers,
        )
    }

    private fun assertApplicableHandlers(
        sourceElements: Array<PsiElement>,
        targetElement: PsiElement?,
        expectedHandlers: List<Pair<String, String?>>,
    ) {
        val availableHandlers = MoveHandlerDelegate.EP_NAME.extensionList.filter {
            it.canMove(sourceElements, targetElement, null)
        }

        assertEquals(expectedHandlers.map{ it.first }, availableHandlers.map { it::class.simpleName })
        assertEquals(expectedHandlers.map { it.second }, availableHandlers.map { it.getActionName(sourceElements) })
    }
}