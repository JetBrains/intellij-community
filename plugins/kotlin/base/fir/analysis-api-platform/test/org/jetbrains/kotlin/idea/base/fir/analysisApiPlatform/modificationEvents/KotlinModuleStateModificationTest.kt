// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.analysisApiPlatform.modificationEvents

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.PsiTestUtil
import org.jetbrains.kotlin.analysis.api.platform.modification.KotlinModificationEventKind
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.idea.facet.getOrCreateFacet
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil
import org.jetbrains.kotlin.idea.test.addDependency
import org.jetbrains.kotlin.idea.test.addEmptyClassesRoot

class KotlinModuleStateModificationTest : AbstractKotlinModuleModificationEventTest() {
    override val expectedEventKind: KotlinModificationEventKind
        get() = KotlinModificationEventKind.MODULE_STATE_MODIFICATION

    override val defaultAllowedEventKinds: Set<KotlinModificationEventKind>
        get() = setOf(
            // Module state modification can easily trigger global source out-of-block modification through module roots changes.
            KotlinModificationEventKind.GLOBAL_SOURCE_OUT_OF_BLOCK_MODIFICATION
        )

    fun `test source module state modification after adding module dependency`() {
        val moduleA = createModuleInTmpDir("a")
        val moduleB = createModuleInTmpDir("b")
        val moduleC = createModuleInTmpDir("c")

        val trackerA = createTracker(moduleA, "module A with an added module dependency")
        val trackerB = createTracker(moduleB, "module B")
        val trackerC = createTracker(moduleC, "module C")

        moduleA.addDependency(moduleB)

        trackerA.assertModifiedOnce()
        trackerB.assertNotModified()
        trackerC.assertNotModified()
    }

    fun `test source module state modification after adding module dependency with existing dependent`() {
        val moduleA = createModuleInTmpDir("a")
        val moduleB = createModuleInTmpDir("b")
        val moduleC = createModuleInTmpDir("c")

        moduleC.addDependency(moduleA)

        val trackerA = createTracker(moduleA, "module A with an added module dependency")
        val trackerB = createTracker(moduleB, "module B")
        val trackerC = createTracker(moduleC, "module C with a dependency on module A")

        moduleA.addDependency(moduleB)

        trackerA.assertModifiedOnce()
        trackerB.assertNotModified()
        trackerC.assertNotModified()
    }

    fun `test source module state modification after removing module dependency`() {
        val moduleA = createModuleInTmpDir("a")
        val moduleB = createModuleInTmpDir("b")
        val moduleC = createModuleInTmpDir("c")

        moduleA.addDependency(moduleB)

        val trackerA = createTracker(moduleA, "module A with a removed module dependency")
        val trackerB = createTracker(moduleB, "module B")
        val trackerC = createTracker(moduleC, "module C")

        moduleA.removeDependency(moduleB)

        trackerA.assertModifiedOnce()
        trackerB.assertNotModified()
        trackerC.assertNotModified()
    }

    fun `test source module state modification after adding module roots`() {
        val moduleA = createModuleInTmpDir("a")
        val moduleB = createModuleInTmpDir("b")
        val moduleC = createModuleInTmpDir("c")

        val allowedEventKinds = setOf(KotlinModificationEventKind.GLOBAL_MODULE_STATE_MODIFICATION)
        val trackerA = createTracker(moduleA, "module A with added module roots", allowedEventKinds)
        val trackerB = createTracker(moduleB, "module B", allowedEventKinds)
        val trackerC = createTracker(moduleC, "module C", allowedEventKinds)

        moduleA.addContentRoot(createTempDirectory().toPath())

        trackerA.assertModifiedOnce()
        trackerB.assertNotModified()
        trackerC.assertNotModified()
    }

    fun `test source module state modification after removing module roots`() {
        val moduleA = createModuleInTmpDir("a")
        val moduleB = createModuleInTmpDir("b")
        val moduleC = createModuleInTmpDir("c")

        val root = moduleA.addContentRoot(createTempDirectory().toPath())

        val trackerA = createTracker(moduleA, "module A with removed module roots")
        val trackerB = createTracker(moduleB, "module B")
        val trackerC = createTracker(moduleC, "module C")

        PsiTestUtil.removeContentEntry(moduleA, root.file!!)

        trackerA.assertModifiedOnce()
        trackerB.assertNotModified()
        trackerC.assertNotModified()
    }

    fun `test source module state modification after adding library dependency`() {
        val moduleA = createModuleInTmpDir("a")
        val moduleB = createModuleInTmpDir("b")
        val moduleC = createModuleInTmpDir("c")

        val allowedEventKinds = setOf(KotlinModificationEventKind.GLOBAL_MODULE_STATE_MODIFICATION)
        val trackerA = createTracker(moduleA, "module A with an added library dependency", allowedEventKinds)
        val trackerB = createTracker(moduleB, "module B", allowedEventKinds)
        val trackerC = createTracker(moduleC, "module C", allowedEventKinds)

        moduleA.addLibrary(TestKotlinArtifacts.kotlinTestJunit)

        trackerA.assertModifiedOnce()
        trackerB.assertNotModified()
        trackerC.assertNotModified()
    }

    fun `test source module state modification after removing library dependency`() {
        val moduleA = createModuleInTmpDir("a")
        val moduleB = createModuleInTmpDir("b")
        val moduleC = createModuleInTmpDir("c")

        moduleA.addLibrary(TestKotlinArtifacts.kotlinTestJunit, name = "junit")

        val trackerA = createTracker(moduleA, "module A with a removed library dependency")
        val trackerB = createTracker(moduleB, "module B")
        val trackerC = createTracker(moduleC, "module C")

        ConfigLibraryUtil.removeLibrary(moduleA, "junit")

        trackerA.assertModifiedOnce()
        trackerB.assertNotModified()
        trackerC.assertNotModified()
    }

    fun `test source module state modification after removal`() {
        val moduleA = createModuleInTmpDir("a")
        val moduleB = createModuleInTmpDir("b")
        val moduleC = createModuleInTmpDir("c")

        val trackerA = createTracker(moduleA, "disposed module A")
        val trackerB = createTracker(moduleB, "module B")
        val trackerC = createTracker(moduleC, "module C")

        ModuleManager.getInstance(myProject).disposeModule(moduleA)

        trackerA.assertModifiedOnce(shouldBeRemoval = true)
        trackerB.assertNotModified()
        trackerC.assertNotModified()
    }

    fun `test library module state modification after root replacement`() {
        val libraryA = createProjectLibrary("a")
        val libraryB = createProjectLibrary("b")
        val moduleC = createModuleInTmpDir("c")
        val moduleD = createModuleInTmpDir("d")

        moduleC.addDependency(libraryA)
        moduleC.addDependency(libraryB)

        val trackerA = createTracker(libraryA, "library A with a replaced root")
        val trackerB = createTracker(libraryB, "library B")
        val trackerC = createTracker(moduleC, "module C")
        val trackerD = createTracker(moduleD, "module D")

        libraryA.swapRoot()

        trackerA.assertModifiedOnce()
        trackerB.assertNotModified()
        trackerC.assertNotModified()
        trackerD.assertNotModified()
    }

    private fun Library.swapRoot() = runWriteAction {
        val existingRootUrl = rootProvider.getUrls(OrderRootType.CLASSES)[0]!!
        modifiableModel.apply {
            removeRoot(existingRootUrl, OrderRootType.CLASSES)
            addEmptyClassesRoot()
            commit()
        }
    }

    fun `test library module state modification after removal`() {
        val libraryA = createProjectLibrary("a")
        val libraryB = createProjectLibrary("b")
        val moduleC = createModuleInTmpDir("c")
        val moduleD = createModuleInTmpDir("d")

        moduleC.addDependency(libraryA)
        moduleC.addDependency(libraryB)

        val trackerA = createTracker(libraryA, "removed library A")
        val trackerB = createTracker(libraryB, "library B")
        val trackerC = createTracker(moduleC, "module C")
        val trackerD = createTracker(moduleD, "module D")

        ConfigLibraryUtil.removeProjectLibrary(myProject, libraryA)

        trackerA.assertModifiedOnce(shouldBeRemoval = true)
        trackerB.assertNotModified()
        trackerC.assertNotModified()
        trackerD.assertNotModified()
    }

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

    fun `test module facet`() {
        val moduleA = createModuleInTmpDir("a")
        val moduleB = createModuleInTmpDir("b")

        val trackerA = createTracker(moduleA, "module A with an added facet")
        val trackerB = createTracker(moduleB, "module B")

        val modelsProvider = ProjectDataManager.getInstance().createModifiableModelsProvider(project)
        moduleA.getOrCreateFacet(modelsProvider, useProjectSettings = false)
        runWriteAction { modelsProvider.commit() }

        trackerA.assertModifiedOnce()
        trackerB.assertNotModified()
    }

    fun `test module jvm settings`() {
        val moduleA = createModuleInTmpDir("a")
        val moduleB = createModuleInTmpDir("b")

        val trackerA = createTracker(moduleA, "module A with changed language level")
        val trackerB = createTracker(moduleB, "module B")

        IdeaTestUtil.setModuleLanguageLevel(moduleA, LanguageLevel.JDK_1_8)

        trackerA.assertModifiedOnce()
        trackerB.assertNotModified()
    }
}
