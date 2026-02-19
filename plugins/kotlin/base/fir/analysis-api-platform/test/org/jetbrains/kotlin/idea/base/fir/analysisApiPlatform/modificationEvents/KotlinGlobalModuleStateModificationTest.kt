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
import org.jetbrains.kotlin.idea.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.idea.base.fir.analysisApiPlatform.FirIdeModuleStateModificationService
import org.jetbrains.kotlin.idea.facet.getOrCreateFacet
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil
import org.jetbrains.kotlin.idea.test.addDependency
import org.jetbrains.kotlin.idea.test.addEmptyClassesRoot

/**
 * This test covers the case where [FirIdeModuleStateModificationService] publishes global modification events, which is the default
 * behavior. The alternative behavior with module-specific modification events is covered by
 * [KotlinModuleSpecificModuleStateModificationTest].
 *
 * The global modification event tracker will ensure that no [KotlinModificationEventKind.MODULE_STATE_MODIFICATION] is published, since
 * it's not an allowed event.
 */
class KotlinGlobalModuleStateModificationTest : AbstractKotlinModificationEventTest() {
    fun `test source module state modification after adding module dependency`() {
        val moduleA = createModuleInTmpDir("a")
        val moduleB = createModuleInTmpDir("b")
        createModuleInTmpDir("c")

        val globalTracker = createGlobalSourceModuleStateTracker()

        moduleA.addDependency(moduleB)

        globalTracker.assertModifiedOnce()
    }

    fun `test source module state modification after adding module dependency with existing dependent`() {
        val moduleA = createModuleInTmpDir("a")
        val moduleB = createModuleInTmpDir("b")
        val moduleC = createModuleInTmpDir("c")

        moduleC.addDependency(moduleA)

        val globalTracker = createGlobalSourceModuleStateTracker()

        moduleA.addDependency(moduleB)

        globalTracker.assertModifiedOnce()
    }

    fun `test source module state modification after removing module dependency`() {
        val moduleA = createModuleInTmpDir("a")
        val moduleB = createModuleInTmpDir("b")
        createModuleInTmpDir("c")

        moduleA.addDependency(moduleB)

        val globalTracker = createGlobalSourceModuleStateTracker()

        moduleA.removeDependency(moduleB)

        globalTracker.assertModifiedOnce()
    }

    fun `test source module state modification after adding module roots`() {
        val moduleA = createModuleInTmpDir("a")
        createModuleInTmpDir("b")
        createModuleInTmpDir("c")

        val globalTracker = createGlobalSourceModuleStateTracker(
            additionalAllowedEventKinds = setOf(KotlinModificationEventKind.GLOBAL_MODULE_STATE_MODIFICATION),
        )

        moduleA.addContentRoot(createTempDirectory().toPath())

        globalTracker.assertModifiedOnce()
    }

    fun `test source module state modification after removing module roots`() {
        val moduleA = createModuleInTmpDir("a")
        createModuleInTmpDir("b")
        createModuleInTmpDir("c")

        val root = moduleA.addContentRoot(createTempDirectory().toPath())

        val globalTracker = createGlobalSourceModuleStateTracker()

        PsiTestUtil.removeContentEntry(moduleA, root.file!!)

        globalTracker.assertModifiedOnce()
    }

    fun `test source module state modification after adding library dependency`() {
        val moduleA = createModuleInTmpDir("a")
        createModuleInTmpDir("b")
        createModuleInTmpDir("c")

        val globalTracker = createGlobalSourceModuleStateTracker(
            // `addLibrary` may trigger indexing, which can in turn trigger `FirIdeDumbModeInvalidationListener` to publish a
            // `GLOBAL_MODULE_STATE_MODIFICATION`.
            additionalAllowedEventKinds = setOf(KotlinModificationEventKind.GLOBAL_MODULE_STATE_MODIFICATION),
        )

        moduleA.addLibrary(TestKotlinArtifacts.kotlinTestJunit.toFile())

        globalTracker.assertModifiedOnce()
    }

    fun `test source module state modification after removing library dependency`() {
        val moduleA = createModuleInTmpDir("a")
        createModuleInTmpDir("b")
        createModuleInTmpDir("c")

        moduleA.addLibrary(TestKotlinArtifacts.kotlinTestJunit.toFile(), name = "junit")

        val globalTracker = createGlobalModuleStateTracker()

        ConfigLibraryUtil.removeLibrary(moduleA, "junit")

        globalTracker.assertModifiedOnce()
    }

    fun `test source module state modification after removal`() {
        val moduleA = createModuleInTmpDir("a")
        createModuleInTmpDir("b")
        createModuleInTmpDir("c")

        val globalTracker = createGlobalSourceModuleStateTracker()

        ModuleManager.getInstance(myProject).disposeModule(moduleA)

        globalTracker.assertModifiedOnce()
    }

    fun `test library module state modification after root replacement`() {
        val libraryA = createProjectLibrary("a")
        val libraryB = createProjectLibrary("b")
        val moduleC = createModuleInTmpDir("c")
        createModuleInTmpDir("d")

        moduleC.addDependency(libraryA)
        moduleC.addDependency(libraryB)

        val globalTracker = createGlobalModuleStateTracker()

        libraryA.swapRoot()

        globalTracker.assertModifiedOnce()
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
        createModuleInTmpDir("d")

        moduleC.addDependency(libraryA)
        moduleC.addDependency(libraryB)

        val globalTracker = createGlobalModuleStateTracker()

        ConfigLibraryUtil.removeProjectLibrary(myProject, libraryA)

        globalTracker.assertModifiedOnce()
    }

    fun `test module facet`() {
        val moduleA = createModuleInTmpDir("a")
        createModuleInTmpDir("b")

        val globalTracker = createGlobalSourceModuleStateTracker()

        val modelsProvider = ProjectDataManager.getInstance().createModifiableModelsProvider(project)
        moduleA.getOrCreateFacet(modelsProvider, useProjectSettings = false)
        runWriteAction { modelsProvider.commit() }

        globalTracker.assertModifiedOnce()
    }

    fun `test module jvm settings`() {
        val moduleA = createModuleInTmpDir("a")
        createModuleInTmpDir("b")

        val globalTracker = createGlobalSourceModuleStateTracker()

        IdeaTestUtil.setModuleLanguageLevel(moduleA, LanguageLevel.JDK_1_8)

        globalTracker.assertModifiedOnce()
    }

    private fun createGlobalSourceModuleStateTracker(
        additionalAllowedEventKinds: Set<KotlinModificationEventKind> = emptySet(),
    ): ModificationEventTracker =
        createGlobalTracker(
            "global source module state tracker",
            expectedEventKind = KotlinModificationEventKind.GLOBAL_SOURCE_MODULE_STATE_MODIFICATION,
            additionalAllowedEventKinds = additionalAllowedEventKinds,
        )

    private fun createGlobalModuleStateTracker(): ModificationEventTracker =
        createGlobalTracker(
            "global module state tracker",
            expectedEventKind = KotlinModificationEventKind.GLOBAL_MODULE_STATE_MODIFICATION,
        )
}
