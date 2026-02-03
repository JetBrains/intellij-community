// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.analysisApiPlatform.modificationEvents

import com.intellij.openapi.application.runWriteAction
import com.intellij.testFramework.PsiTestUtil
import org.jetbrains.kotlin.analysis.api.platform.modification.KotlinModificationEventKind
import org.jetbrains.kotlin.idea.util.sourceRoots

class KotlinGlobalSourceOutOfBlockModificationTest : AbstractKotlinGlobalModificationEventTest() {
    override val expectedEventKind: KotlinModificationEventKind
        get() = KotlinModificationEventKind.GLOBAL_SOURCE_OUT_OF_BLOCK_MODIFICATION

    fun `test that global source out-of-block modification occurs after a file is added to a module content root`() {
        val moduleA = createModuleInTmpDir("a")
        createModuleInTmpDir("b")
        createModuleInTmpDir("c")

        val tracker = createTracker("the project after a file is added to the content root of module A")

        runWriteAction {
            moduleA.sourceRoots.first().createChildData(/* requestor = */ null, "file.kt")
        }

        tracker.assertModifiedOnce()
    }

    fun `test that global source out-of-block modification occurs after a file is moved to another module content root`() {
        val moduleA = createModuleInTmpDir("a") {
            listOf(
                FileWithText("main.kt", "fun main() {}")
            )
        }
        val moduleB = createModuleInTmpDir("b")
        createModuleInTmpDir("c")

        val tracker = createTracker("the project after a file from module A is moved to the content root of module B")

        val file = moduleA.sourceRoots.first().findChild("main.kt")!!
        val destination = moduleB.sourceRoots.first()
        move(file, destination)

        tracker.assertModifiedOnce()
    }

    fun `test that global source out-of-block modification occurs after moving a script file to a non-source module content root`() {
        val scriptA = createScript("a")
        val moduleB = createModuleInTmpDir("b")

        val destination = getVirtualFile(createTempDirectory())
        PsiTestUtil.addContentRoot(moduleB, destination)

        val tracker = createTracker(
            "the project after a script file is moved to a non-source content root of module B",

            // Moving the file constitutes removal of the single-file module.
            additionalAllowedEventKind = KotlinModificationEventKind.MODULE_STATE_MODIFICATION
        )

        move(scriptA.virtualFile, destination)

        tracker.assertModifiedOnce()
    }

    fun `test that global source out-of-block modification occurs after moving a script file outside the project content root`() {
        val scriptA = createScript("a")
        val destination = getVirtualFile(createTempDirectory())

        val tracker = createTracker(
            "the project after a script file is moved outside the project content root",

            // Moving the file constitutes removal of the single-file module.
            additionalAllowedEventKind = KotlinModificationEventKind.MODULE_STATE_MODIFICATION
        )

        move(scriptA.virtualFile, destination)

        tracker.assertModifiedOnce()
    }

    // TODO (marco): This test can be enabled once IDEA-324516 is fixed.
    //fun `test that global source out-of-block modification occurs after deleting a script file`() {
    //    val scriptA = createScript("a")
    //
    //    val tracker = createTracker()
    //
    //    delete(scriptA.virtualFile)
    //
    //    tracker.assertModified("the project after a script file is deleted")
    //}

    fun `test that global source out-of-block modification occurs after moving a not-under-content-root file to a non-source module content root`() {
        val fileA = createNotUnderContentRootFile("a")
        val moduleB = createModuleInTmpDir("b")

        val destination = getVirtualFile(createTempDirectory())
        PsiTestUtil.addContentRoot(moduleB, destination)

        val tracker = createTracker(
            "the project after a not-under-content-root file is moved to a non-source content root of module B",

            // Moving the file constitutes removal of the single-file module.
            additionalAllowedEventKind = KotlinModificationEventKind.MODULE_STATE_MODIFICATION
        )

        move(fileA.virtualFile, destination)

        tracker.assertModifiedOnce()
    }

    fun `test that global source out-of-block modification occurs after moving a not-under-content-root file outside the project content root`() {
        val fileA = createNotUnderContentRootFile("a")
        val destination = getVirtualFile(createTempDirectory())

        val tracker = createTracker(
            "the project after a not-under-content-root file is moved outside the project content root",

            // Moving the file constitutes removal of the single-file module.
            additionalAllowedEventKind = KotlinModificationEventKind.MODULE_STATE_MODIFICATION
        )

        // Note that the "not-under-content-root" file is under the content root of the project, and so moving it outside the content root
        // of the project does have an effect.
        move(fileA.virtualFile, destination)

        tracker.assertModifiedOnce()
    }

    fun `test that global source out-of-block modification occurs after deleting a not-under-content-root file`() {
        val fileA = createNotUnderContentRootFile("a")

        val tracker = createTracker(
            "the project after a not-under-content-root file is deleted",

            // Deleting the file constitutes removal of the single-file module.
            additionalAllowedEventKind = KotlinModificationEventKind.MODULE_STATE_MODIFICATION
        )

        delete(fileA.virtualFile)

        tracker.assertModified()
    }
}
