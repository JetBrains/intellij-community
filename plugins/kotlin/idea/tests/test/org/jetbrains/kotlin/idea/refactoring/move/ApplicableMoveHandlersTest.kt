// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.move

import com.intellij.psi.PsiElement
import com.intellij.refactoring.move.MoveHandlerDelegate
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.core.getPackage
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction

class ApplicableMoveHandlersTest : KotlinLightCodeInsightFixtureTestCase() {
    fun testOrder() {
        val kotlinHandlers = MoveHandlerDelegate.EP_NAME.extensionList.filter { it.supportsLanguage(KotlinLanguage.INSTANCE) }
        assertContainsOrdered(
            kotlinHandlers.map { it::class.simpleName },
            listOf(
                "MoveKotlinMethodHandler",
                "MoveKotlinDeclarationsHandler",
                "KotlinAwareJavaMovePackagesHandler",
                "KotlinMoveFilesOrDirectoriesHandler",
                "MoveRelatedFilesHandler",
                "JavaMoveClassesOrPackagesHandler",
                "JavaMoveFilesOrDirectoriesHandler",
                "MoveFilesOrDirectoriesHandler",
            ),
        )
    }

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
            "JavaMoveClassesOrPackagesHandler" to "Move Classes…",
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

    fun testClass() {
        val kotlinFile = myFixture.addFileToProject(
            "a/kotlinFile.kt",
            """
                package a
                class One
                class Two
            """.trimIndent(),
        ) as KtFile

        val anotherFile = myFixture.addFileToProject("b/anotherFile.kt", "package b")
        val targetDirectory = anotherFile.containingDirectory
        val kotlinClassAsElements: Array<PsiElement> = arrayOf(kotlinFile.declarations.first() as KtClass)
        val declarationAndFilesHandlers = listOf(
            "MoveKotlinDeclarationsHandler" to null,
            "KotlinMoveFilesOrDirectoriesHandler" to "Move Files…",
        )

        // without context
        assertApplicableHandlers(
            sourceElements = kotlinClassAsElements,
            targetElement = null,
            expectedHandlers = declarationAndFilesHandlers,
        )

        // to directory
        assertApplicableHandlers(
            sourceElements = kotlinClassAsElements,
            targetElement = targetDirectory,
            expectedHandlers = declarationAndFilesHandlers,
        )

        // to file
        assertApplicableHandlers(
            sourceElements = kotlinClassAsElements,
            targetElement = anotherFile,
            expectedHandlers = listOf("MoveKotlinDeclarationsHandler" to null),
        )

        // to package
        assertApplicableHandlers(
            sourceElements = kotlinClassAsElements,
            targetElement = anotherFile.containingDirectory.getPackage()!!,
            expectedHandlers = declarationAndFilesHandlers,
        )
    }

    fun testFunction() {
        val kotlinFile = myFixture.addFileToProject(
            "a/kotlinFile.kt",
            """
                package a
                class One
                class Two
                fun check() = Unit
            """.trimIndent(),
        ) as KtFile

        val anotherFile = myFixture.addFileToProject("b/anotherFile.kt", "package b")
        val targetDirectory = anotherFile.containingDirectory
        val kotlinFunctionAsElements: Array<PsiElement> = arrayOf(kotlinFile.declarations.last() as KtNamedFunction)
        val declarationAndFilesHandlers = listOf(
            "MoveKotlinDeclarationsHandler" to null,
        )

        // without context
        assertApplicableHandlers(
            sourceElements = kotlinFunctionAsElements,
            targetElement = null,
            expectedHandlers = declarationAndFilesHandlers,
        )

        // to directory
        assertApplicableHandlers(
            sourceElements = kotlinFunctionAsElements,
            targetElement = targetDirectory,
            expectedHandlers = declarationAndFilesHandlers,
        )

        // to file
        assertApplicableHandlers(
            sourceElements = kotlinFunctionAsElements,
            targetElement = anotherFile,
            expectedHandlers = listOf("MoveKotlinDeclarationsHandler" to null),
        )

        // to package
        assertApplicableHandlers(
            sourceElements = kotlinFunctionAsElements,
            targetElement = anotherFile.containingDirectory.getPackage()!!,
            expectedHandlers = declarationAndFilesHandlers,
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

        assertEquals(expectedHandlers.map { it.first }, availableHandlers.map { it::class.simpleName })
        assertEquals(expectedHandlers.map { it.second }, availableHandlers.map { it.getActionName(sourceElements) })
    }
}