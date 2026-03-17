// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.analysisApiPlatform.modificationEvents

import com.intellij.testFramework.PsiTestUtil
import org.jetbrains.kotlin.analysis.api.platform.modification.KotlinModificationEventKind

class KotlinSingleFileModuleStateModificationTest : AbstractKotlinModuleModificationEventTest() {
    override val expectedEventKind: KotlinModificationEventKind
        get() = KotlinModificationEventKind.MODULE_STATE_MODIFICATION

    override val defaultAllowedEventKinds: Set<KotlinModificationEventKind>
        get() = setOf(
            // Module state modification can easily trigger global source out-of-block modification through module roots changes.
            KotlinModificationEventKind.GLOBAL_SOURCE_OUT_OF_BLOCK_MODIFICATION
        )

    fun `test script module state modification after moving the script file to another module`() {
        val scriptA = createScript("a")
        val scriptB = createScript("b")
        val libraryC = createProjectLibrary("c")
        val moduleD = createModuleInTmpDir("d")
        val moduleE = createModuleInTmpDir("e")

        val destination = getVirtualFile(createTempDirectory())
        PsiTestUtil.addContentRoot(moduleE, destination)

        val trackerA = createTracker(scriptA, "moved script A")
        val trackerB = createTracker(scriptB, "script B")
        val trackerC = createTracker(libraryC, "library C")
        val trackerD = createTracker(moduleD, "module D")
        val trackerE = createTracker(moduleE, "destination module E")

        move(scriptA.virtualFile, destination)

        trackerA.assertModifiedOnce()
        trackerB.assertNotModified()
        trackerC.assertNotModified()
        trackerD.assertNotModified()

        // The file move will cause a global PSI tree change event, and thereby a global out-of-block modification event, but the module
        // state of the destination module E is not affected by a file move, so the tracker should not register any module state
        // modification events.
        trackerE.assertNotModified()
    }

    fun `test script module state modification after moving the script file outside the content root`() {
        val scriptA = createScript("a")
        val scriptB = createScript("b")
        val libraryC = createProjectLibrary("c")
        val moduleD = createModuleInTmpDir("d")

        val destination = getVirtualFile(createTempDirectory())

        val trackerA = createTracker(scriptA, "moved script A")
        val trackerB = createTracker(scriptB, "script B")
        val trackerC = createTracker(libraryC, "library C")
        val trackerD = createTracker(moduleD, "module D")

        move(scriptA.virtualFile, destination)

        trackerA.assertModifiedOnce()
        trackerB.assertNotModified()
        trackerC.assertNotModified()
        trackerD.assertNotModified()
    }

    fun `test script module state modification after deleting the script file`() {
        val scriptA = createScript("a")
        val scriptB = createScript("b")
        val libraryC = createProjectLibrary("c")
        val moduleD = createModuleInTmpDir("d")

        val trackerA = createTracker(scriptA, "deleted script A")
        val trackerB = createTracker(scriptB, "script B")
        val trackerC = createTracker(libraryC, "library C")
        val trackerD = createTracker(moduleD, "module D")

        delete(scriptA.virtualFile)

        trackerA.assertModifiedOnce(shouldBeRemoval = true)
        trackerB.assertNotModified()
        trackerC.assertNotModified()
        trackerD.assertNotModified()
    }

    fun `test not-under-content-root module state modification after moving the file to another module`() {
        val fileA = createNotUnderContentRootFile("a")
        val fileB = createNotUnderContentRootFile("b")
        val libraryC = createProjectLibrary("c")
        val moduleD = createModuleInTmpDir("d")
        val moduleE = createModuleInTmpDir("e")

        val destination = getVirtualFile(createTempDirectory())
        PsiTestUtil.addContentRoot(moduleE, destination)

        val trackerA = createTracker(fileA, "moved not-under-content-root file A")
        val trackerB = createTracker(fileB, "not-under-content-root file B")
        val trackerC = createTracker(libraryC, "library C")
        val trackerD = createTracker(moduleD, "module D")
        val trackerE = createTracker(moduleE, "destination module E")

        move(fileA.virtualFile, destination)

        trackerA.assertModifiedOnce()
        trackerB.assertNotModified()
        trackerC.assertNotModified()
        trackerD.assertNotModified()

        // The file move will cause a global PSI tree change event, and thereby a global out-of-block modification event, but the module
        // state of the destination module E is not affected by a file move, so the tracker should not register any module state
        // modification events.
        trackerE.assertNotModified()
    }

    fun `test not-under-content-root module state modification after moving the file outside the content root`() {
        val fileA = createNotUnderContentRootFile("a")
        val fileB = createNotUnderContentRootFile("b")
        val libraryC = createProjectLibrary("c")
        val moduleD = createModuleInTmpDir("d")

        val destination = getVirtualFile(createTempDirectory())

        val trackerA = createTracker(fileA, "moved not-under-content-root file A")
        val trackerB = createTracker(fileB, "not-under-content-root file B")
        val trackerC = createTracker(libraryC, "library C")
        val trackerD = createTracker(moduleD, "module D")

        move(fileA.virtualFile, destination)

        trackerA.assertModifiedOnce()
        trackerB.assertNotModified()
        trackerC.assertNotModified()
        trackerD.assertNotModified()
    }

    fun `test not-under-content-root module state modification after deleting the file`() {
        val fileA = createNotUnderContentRootFile("a")
        val fileB = createNotUnderContentRootFile("b")
        val libraryC = createProjectLibrary("c")
        val moduleD = createModuleInTmpDir("d")

        val trackerA = createTracker(fileA, "deleted not-under-content-root file A")
        val trackerB = createTracker(fileB, "not-under-content-root file B")
        val trackerC = createTracker(libraryC, "library C")
        val trackerD = createTracker(moduleD, "module D")

        delete(fileA.virtualFile)

        trackerA.assertModifiedOnce(shouldBeRemoval = true)
        trackerB.assertNotModified()
        trackerC.assertNotModified()
        trackerD.assertNotModified()
    }
}
