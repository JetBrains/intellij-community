// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.analysisApiPlatform.modificationEvents

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.util.registry.Registry
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.PsiTestUtil
import org.jetbrains.kotlin.analysis.api.platform.modification.KotlinModificationEventKind
import org.jetbrains.kotlin.idea.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.idea.base.fir.analysisApiPlatform.FirIdeModuleStateModificationService
import org.jetbrains.kotlin.idea.facet.getOrCreateFacet
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil
import org.jetbrains.kotlin.idea.test.addDependency
import org.jetbrains.kotlin.idea.test.addEmptyClassesRoot

/**
 * This test covers the case where [FirIdeModuleStateModificationService] publishes module-specific modification events. This is not the
 * default behavior, which is covered by [KotlinGlobalModuleStateModificationTest].
 */
class KotlinModuleSpecificModuleStateModificationTest : AbstractKotlinModuleModificationEventTest() {
    override val expectedEventKind: KotlinModificationEventKind
        get() = KotlinModificationEventKind.MODULE_STATE_MODIFICATION

    override fun setUp() {
        super.setUp()

        Registry
            .get(FirIdeModuleStateModificationService.ENABLE_MODULE_SPECIFIC_MODIFICATION_EVENTS_KEY)
            .setValue(true, testRootDisposable)
    }

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

        moduleA.addLibrary(TestKotlinArtifacts.kotlinTestJunit.toFile())

        trackerA.assertModifiedOnce()
        trackerB.assertNotModified()
        trackerC.assertNotModified()
    }

    fun `test source module state modification after removing library dependency`() {
        val moduleA = createModuleInTmpDir("a")
        val moduleB = createModuleInTmpDir("b")
        val moduleC = createModuleInTmpDir("c")

        moduleA.addLibrary(TestKotlinArtifacts.kotlinTestJunit.toFile(), name = "junit")

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
