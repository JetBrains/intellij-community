// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.analysisApiPlatform.modificationEvents

import com.intellij.openapi.application.runUndoTransparentWriteAction
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.kotlin.analysis.api.platform.modification.KotlinModificationEventKind
import org.jetbrains.kotlin.idea.base.fir.analysisApiPlatform.FirIdeOutOfBlockPsiTreeChangePreprocessor
import org.jetbrains.kotlin.idea.base.projectStructure.toKaSourceModuleForProduction
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * The [tree change preprocessor][org.jetbrains.kotlin.idea.base.fir.analysisApiPlatform.FirIdeOutOfBlockPsiTreeChangePreprocessor] is
 * limited in the number of tree change events it should process and the number of modification events it should publish. This class
 * contains tests which verify these limitations.
 *
 * See [FirIdeOutOfBlockPsiTreeChangePreprocessor][org.jetbrains.kotlin.idea.base.fir.analysisApiPlatform.FirIdeOutOfBlockPsiTreeChangePreprocessor]
 * for more information.
 */
class KotlinEventLimitOutOfBlockModificationTest : AbstractKotlinModificationEventTest() {
    override fun setUp() {
        super.setUp()

        // To avoid having to edit too many files in each test to trigger a global modification event, we set the processing limit to a low
        // value. In particular, 2 allows us to test single-file and multi-file behavior, without having to repeat steps for multi-file
        // behavior.
        Registry.get(FirIdeOutOfBlockPsiTreeChangePreprocessor.FILE_PROCESSING_LIMIT_KEY).setValue(2)
    }

    fun `test that modification events are limited when modifying multiple files`() {
        val moduleA = createModuleInTmpDir("a") {
            listOf(
                FileWithText("a.kt", """
                    fun foo(): Int { 
                        println("Hello!")
                        return 10
                    }
                """.trimIndent()),
                FileWithText("b.kt", "fun bar(): Int = 20"),
                FileWithText("c.kt", "fun baz(): Int = 30"),
                FileWithText("d.kt", "fun qux(): Int = 40"),
            )
        }

        val globalTracker = createGlobalTracker(
            "the project after modifying multiple files",
            expectedEventKind = KotlinModificationEventKind.GLOBAL_SOURCE_OUT_OF_BLOCK_MODIFICATION,
            additionalAllowedEventKinds = setOf(
                KotlinModificationEventKind.MODULE_OUT_OF_BLOCK_MODIFICATION
            )
        )

        val moduleTracker = createModuleTracker(
            moduleA.toKaSourceModuleForProduction()!!,
            "module A after modifying multiple files",
            expectedEventKind = KotlinModificationEventKind.MODULE_OUT_OF_BLOCK_MODIFICATION,
            additionalAllowedEventKinds = setOf(KotlinModificationEventKind.GLOBAL_SOURCE_OUT_OF_BLOCK_MODIFICATION),
        )

        val fileA = moduleA.findSourceKtFile("a.kt")
        val fileB = moduleA.findSourceKtFile("b.kt")
        val fileC = moduleA.findSourceKtFile("c.kt")
        val fileD = moduleA.findSourceKtFile("d.kt")

        fun deleteTypeReference(file: KtFile) {
            file.firstTopLevelFunction.typeReference!!.delete()
        }

        runUndoTransparentWriteAction {
            // First, no OOBM event should be published on in-block modification. While we test in-block modification separately, the intent
            // here is to check that OOBM publishing limits still apply even after processing an in-block event.
            fileA.firstTopLevelFunction.bodyBlockExpression!!.firstStatement!!.delete()

            moduleTracker.assertNotModified()
            globalTracker.assertNotModified()

            // Then, a single module OOBM event should be published after editing `fileA`.
            deleteTypeReference(fileA)

            moduleTracker.assertModifiedOnce()
            globalTracker.assertNotModified()

            // A second out-of-block modification in `fileA` should not lead to another modification event, since we've already published
            // an event for the file's module.
            fileA.firstTopLevelFunction.delete()

            moduleTracker.assertModifiedOnce()
            globalTracker.assertNotModified()

            // An out-of-block change in `fileB` is still within the processing limit of two files, so another out-of-block modification
            // event should be published.
            deleteTypeReference(fileB)

            moduleTracker.assertModifiedExactly(times = 2)
            globalTracker.assertNotModified()

            // When changes in `fileC` are encountered, a single global OOBM event should be published, as we're now dealing with more than
            // two files.
            deleteTypeReference(fileC)

            moduleTracker.assertModifiedExactly(times = 2)
            globalTracker.assertModifiedOnce()

            // Finally, changes in `fileD` should not lead to any further modification events.
            deleteTypeReference(fileD)

            moduleTracker.assertModifiedExactly(times = 2)
            globalTracker.assertModifiedOnce()
        }
    }

    fun `test that a global modification event is published after in-block modifications in multiple files`() {
        val moduleA = createModuleInTmpDir("a") {
            listOf(
                FileWithText("a.kt", """
                    fun foo(): Int { 
                        println("foo")
                        println("foo2")
                        return 10
                    }
                """.trimIndent()),
                FileWithText("b.kt", """
                    fun bar(): Int { 
                        println("bar")
                        return 20
                    }
                """.trimIndent()),
                FileWithText("c.kt", """
                    fun baz(): Int { 
                        println("baz")
                        return 30
                    }
                """.trimIndent()),
                FileWithText("d.kt", """
                    fun qux(): Int { 
                        println("qux")
                        return 40
                    }
                """.trimIndent()),
            )
        }

        val globalTracker = createGlobalTracker(
            "the project after multi-file in-block modification",
            expectedEventKind = KotlinModificationEventKind.GLOBAL_SOURCE_OUT_OF_BLOCK_MODIFICATION,
            additionalAllowedEventKinds = setOf(
                KotlinModificationEventKind.MODULE_OUT_OF_BLOCK_MODIFICATION
            )
        )

        val moduleTracker = createModuleTracker(
            moduleA.toKaSourceModuleForProduction()!!,
            "module A after multi-file in-block modification",
            expectedEventKind = KotlinModificationEventKind.MODULE_OUT_OF_BLOCK_MODIFICATION,
            additionalAllowedEventKinds = setOf(KotlinModificationEventKind.GLOBAL_SOURCE_OUT_OF_BLOCK_MODIFICATION),
        )

        val fileA = moduleA.findSourceKtFile("a.kt")
        val fileB = moduleA.findSourceKtFile("b.kt")
        val fileC = moduleA.findSourceKtFile("c.kt")
        val fileD = moduleA.findSourceKtFile("d.kt")

        fun deleteFirstLineInFunction(file: KtFile) {
            file.firstTopLevelFunction.bodyBlockExpression!!.firstStatement!!.delete()
        }

        runUndoTransparentWriteAction {
            // First in-block modification: There should be no modification events.
            deleteFirstLineInFunction(fileA)

            moduleTracker.assertNotModified()
            globalTracker.assertNotModified()

            // Another in-block modification in the same file: No modification events either.
            deleteFirstLineInFunction(fileA)

            moduleTracker.assertNotModified()
            globalTracker.assertNotModified()

            // An in-block modification in `fileB`: The file is within the processing limit, so no global event should be published.
            deleteFirstLineInFunction(fileB)

            moduleTracker.assertNotModified()
            globalTracker.assertNotModified()

            // An in-block modification in `fileC`: Since we're processing a third file, a global event should be published.
            deleteFirstLineInFunction(fileC)

            moduleTracker.assertNotModified()
            globalTracker.assertModifiedOnce()

            // An in-block modification in `fileD`: As we've already published a global event, no further events should be published.
            deleteFirstLineInFunction(fileD)

            moduleTracker.assertNotModified()
            globalTracker.assertModifiedOnce()
        }
    }

    private val KtFile.firstTopLevelFunction: KtNamedFunction
        get() = declarations.first() as KtNamedFunction
}
