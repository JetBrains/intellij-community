// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.analysis.providers.modificationEvents

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.util.messages.MessageBusConnection
import org.jetbrains.kotlin.analysis.project.structure.KtModule
import org.jetbrains.kotlin.analysis.providers.topics.KotlinModuleStateModificationListener
import org.jetbrains.kotlin.analysis.providers.topics.KotlinTopics
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.idea.facet.getOrCreateFacet
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil
import org.jetbrains.kotlin.idea.test.addDependency
import org.jetbrains.kotlin.idea.test.addEmptyClassesRoot

class KotlinModuleStateModificationTest : AbstractKotlinModuleModificationEventTest<ModuleStateModificationEventTracker>() {
    override fun constructTracker(module: KtModule): ModuleStateModificationEventTracker = ModuleStateModificationEventTracker(module)

    fun `test source module state modification after adding module dependency`() {
        val moduleA = createModuleInTmpDir("a")
        val moduleB = createModuleInTmpDir("b")
        val moduleC = createModuleInTmpDir("c")

        val trackerA = createTracker(moduleA)
        val trackerB = createTracker(moduleB)
        val trackerC = createTracker(moduleC)

        moduleA.addDependency(moduleB)

        trackerA.assertModifiedOnce("module A with added module dependency")
        trackerB.assertNotModified("unchanged module B")
        trackerC.assertNotModified("unchanged module C")
    }

    fun `test source module state modification after adding module dependency with existing dependent`() {
        val moduleA = createModuleInTmpDir("a")
        val moduleB = createModuleInTmpDir("b")
        val moduleC = createModuleInTmpDir("c")

        moduleC.addDependency(moduleA)

        val trackerA = createTracker(moduleA)
        val trackerB = createTracker(moduleB)
        val trackerC = createTracker(moduleC)

        moduleA.addDependency(moduleB)

        trackerA.assertModifiedOnce("module A with added module dependency")
        trackerB.assertNotModified("unchanged module B")
        trackerC.assertNotModified("unchanged module C with a dependency on module A")
    }

    fun `test source module state modification after removing module dependency`() {
        val moduleA = createModuleInTmpDir("a")
        val moduleB = createModuleInTmpDir("b")
        val moduleC = createModuleInTmpDir("c")

        moduleA.addDependency(moduleB)

        val trackerA = createTracker(moduleA)
        val trackerB = createTracker(moduleB)
        val trackerC = createTracker(moduleC)

        moduleA.removeDependency(moduleB)

        trackerA.assertModifiedOnce("module A with removed module dependency")
        trackerB.assertNotModified("unchanged module B")
        trackerC.assertNotModified("unchanged module C")
    }

    fun `test source module state modification after adding module roots`() {
        val moduleA = createModuleInTmpDir("a")
        val moduleB = createModuleInTmpDir("b")
        val moduleC = createModuleInTmpDir("c")

        val trackerA = createTracker(moduleA)
        val trackerB = createTracker(moduleB)
        val trackerC = createTracker(moduleC)

        moduleA.addContentRoot(createTempDirectory().toPath())

        trackerA.assertModifiedOnce("module A with added module roots")
        trackerB.assertNotModified("unchanged module B")
        trackerC.assertNotModified("unchanged module C")
    }

    fun `test source module state modification after removing module roots`() {
        val moduleA = createModuleInTmpDir("a")
        val moduleB = createModuleInTmpDir("b")
        val moduleC = createModuleInTmpDir("c")

        val root = moduleA.addContentRoot(createTempDirectory().toPath())

        val trackerA = createTracker(moduleA)
        val trackerB = createTracker(moduleB)
        val trackerC = createTracker(moduleC)

        PsiTestUtil.removeContentEntry(moduleA, root.file!!)

        trackerA.assertModifiedOnce("module A with removed module roots")
        trackerB.assertNotModified("unchanged module B")
        trackerC.assertNotModified("unchanged module C")
    }

    fun `test source module state modification after adding library dependency`() {
        val moduleA = createModuleInTmpDir("a")
        val moduleB = createModuleInTmpDir("b")
        val moduleC = createModuleInTmpDir("c")

        val trackerA = createTracker(moduleA)
        val trackerB = createTracker(moduleB)
        val trackerC = createTracker(moduleC)

        moduleA.addLibrary(TestKotlinArtifacts.kotlinTestJunit)

        trackerA.assertModifiedOnce("module A with added library dependency")
        trackerB.assertNotModified("unchanged module B")
        trackerC.assertNotModified("unchanged module C")
    }

    fun `test source module state modification after removing library dependency`() {
        val moduleA = createModuleInTmpDir("a")
        val moduleB = createModuleInTmpDir("b")
        val moduleC = createModuleInTmpDir("c")

        moduleA.addLibrary(TestKotlinArtifacts.kotlinTestJunit, name = "junit")

        val trackerA = createTracker(moduleA)
        val trackerB = createTracker(moduleB)
        val trackerC = createTracker(moduleC)

        ConfigLibraryUtil.removeLibrary(moduleA, "junit")

        trackerA.assertModifiedOnce("module A with removed module dependency")
        trackerB.assertNotModified("unchanged module B")
        trackerC.assertNotModified("unchanged module C")
    }

    fun `test source module state modification after removal`() {
        val moduleA = createModuleInTmpDir("a")
        val moduleB = createModuleInTmpDir("b")
        val moduleC = createModuleInTmpDir("c")

        val trackerA = createTracker(moduleA)
        val trackerB = createTracker(moduleB)
        val trackerC = createTracker(moduleC)

        ModuleManager.getInstance(myProject).disposeModule(moduleA)

        trackerA.assertModifiedOnce("disposed module A", shouldBeRemoval = true)
        trackerB.assertNotModified("unchanged module B")
        trackerC.assertNotModified("unchanged module C")
    }

    fun `test library module state modification after root replacement`() {
        val libraryA = createProjectLibrary("a")
        val libraryB = createProjectLibrary("b")
        val moduleC = createModuleInTmpDir("c")
        val moduleD = createModuleInTmpDir("d")

        moduleC.addDependency(libraryA)
        moduleC.addDependency(libraryB)

        val trackerA = createTracker(libraryA)
        val trackerB = createTracker(libraryB)
        val trackerC = createTracker(moduleC)
        val trackerD = createTracker(moduleD)

        libraryA.swapRoot()

        trackerA.assertModifiedOnce("project library A with replaced root")
        trackerB.assertNotModified("unchanged library B")
        trackerC.assertNotModified("unchanged module C")
        trackerD.assertNotModified("unchanged module D")
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

        val trackerA = createTracker(libraryA)
        val trackerB = createTracker(libraryB)
        val trackerC = createTracker(moduleC)
        val trackerD = createTracker(moduleD)

        ConfigLibraryUtil.removeProjectLibrary(myProject, libraryA)

        trackerA.assertModifiedOnce("removed project library A", shouldBeRemoval = true)
        trackerB.assertNotModified("unchanged library B")
        trackerC.assertNotModified("unchanged module C")
        trackerD.assertNotModified("unchanged module D")
    }

    fun `test script module state modification after moving the script file to another module`() {
        val scriptA = createScript("a")
        val scriptB = createScript("b")
        val libraryC = createProjectLibrary("c")
        val moduleD = createModuleInTmpDir("d")
        val moduleE = createModuleInTmpDir("e")

        val destination = getVirtualFile(createTempDirectory())
        PsiTestUtil.addContentRoot(moduleE, destination)

        val trackerA = createTracker(scriptA)
        val trackerB = createTracker(scriptB)
        val trackerC = createTracker(libraryC)
        val trackerD = createTracker(moduleD)
        val trackerE = createTracker(moduleE)

        move(scriptA.virtualFile, destination)

        trackerA.assertModifiedOnce("moved script A")
        trackerB.assertNotModified("unchanged script B")
        trackerC.assertNotModified("unchanged library C")
        trackerD.assertNotModified("unchanged module D")

        // The file move will cause a global PSI tree change event, and thereby a global out-of-block modification event, but the module
        // state of the destination module E is not affected by a file move, so the tracker should not register any module state
        // modification events.
        trackerE.assertNotModified("unchanged destination module E")
    }

    fun `test script module state modification after moving the script file outside the content root`() {
        val scriptA = createScript("a")
        val scriptB = createScript("b")
        val libraryC = createProjectLibrary("c")
        val moduleD = createModuleInTmpDir("d")

        val destination = getVirtualFile(createTempDirectory())

        val trackerA = createTracker(scriptA)
        val trackerB = createTracker(scriptB)
        val trackerC = createTracker(libraryC)
        val trackerD = createTracker(moduleD)

        move(scriptA.virtualFile, destination)

        trackerA.assertModifiedOnce("moved script A")
        trackerB.assertNotModified("unchanged script B")
        trackerC.assertNotModified("unchanged library C")
        trackerD.assertNotModified("unchanged module D")
    }

    fun `test script module state modification after deleting the script file`() {
        val scriptA = createScript("a")
        val scriptB = createScript("b")
        val libraryC = createProjectLibrary("c")
        val moduleD = createModuleInTmpDir("d")

        val trackerA = createTracker(scriptA)
        val trackerB = createTracker(scriptB)
        val trackerC = createTracker(libraryC)
        val trackerD = createTracker(moduleD)

        delete(scriptA.virtualFile)

        trackerA.assertModifiedOnce("deleted script A", shouldBeRemoval = true)
        trackerB.assertNotModified("unchanged script B")
        trackerC.assertNotModified("unchanged library C")
        trackerD.assertNotModified("unchanged module D")
    }

    fun `test not-under-content-root module state modification after moving the file to another module`() {
        val fileA = createNotUnderContentRootFile("a")
        val fileB = createNotUnderContentRootFile("b")
        val libraryC = createProjectLibrary("c")
        val moduleD = createModuleInTmpDir("d")
        val moduleE = createModuleInTmpDir("e")

        val destination = getVirtualFile(createTempDirectory())
        PsiTestUtil.addContentRoot(moduleE, destination)

        val trackerA = createTracker(fileA)
        val trackerB = createTracker(fileB)
        val trackerC = createTracker(libraryC)
        val trackerD = createTracker(moduleD)
        val trackerE = createTracker(moduleE)

        move(fileA.virtualFile, destination)

        trackerA.assertModifiedOnce("moved not-under-content-root file A")
        trackerB.assertNotModified("unchanged not-under-content-root file B")
        trackerC.assertNotModified("unchanged library C")
        trackerD.assertNotModified("unchanged module D")

        // The file move will cause a global PSI tree change event, and thereby a global out-of-block modification event, but the module
        // state of the destination module E is not affected by a file move, so the tracker should not register any module state
        // modification events.
        trackerE.assertNotModified("unchanged destination module E")
    }

    fun `test not-under-content-root module state modification after moving the file outside the content root`() {
        val fileA = createNotUnderContentRootFile("a")
        val fileB = createNotUnderContentRootFile("b")
        val libraryC = createProjectLibrary("c")
        val moduleD = createModuleInTmpDir("d")

        val destination = getVirtualFile(createTempDirectory())

        val trackerA = createTracker(fileA)
        val trackerB = createTracker(fileB)
        val trackerC = createTracker(libraryC)
        val trackerD = createTracker(moduleD)

        move(fileA.virtualFile, destination)

        trackerA.assertModifiedOnce("moved not-under-content-root file A")
        trackerB.assertNotModified("unchanged not-under-content-root file B")
        trackerC.assertNotModified("unchanged library C")
        trackerD.assertNotModified("unchanged module D")
    }

    fun `test not-under-content-root module state modification after deleting the file`() {
        val fileA = createNotUnderContentRootFile("a")
        val fileB = createNotUnderContentRootFile("b")
        val libraryC = createProjectLibrary("c")
        val moduleD = createModuleInTmpDir("d")

        val trackerA = createTracker(fileA)
        val trackerB = createTracker(fileB)
        val trackerC = createTracker(libraryC)
        val trackerD = createTracker(moduleD)

        delete(fileA.virtualFile)

        trackerA.assertModifiedOnce("deleted not-under-content-root file A", shouldBeRemoval = true)
        trackerB.assertNotModified("unchanged not-under-content-root file B")
        trackerC.assertNotModified("unchanged library C")
        trackerD.assertNotModified("unchanged module D")
    }

    fun `test module facet`() {
        val moduleA = createModuleInTmpDir("a")
        val moduleB = createModuleInTmpDir("b")

        val trackerA = createTracker(moduleA)
        val trackerB = createTracker(moduleB)

        val modelsProvider = ProjectDataManager.getInstance().createModifiableModelsProvider(project)
        moduleA.getOrCreateFacet(modelsProvider, useProjectSettings = false)
        runWriteAction { modelsProvider.commit() }

        trackerA.assertModifiedOnce("module A with added facet")
        trackerB.assertNotModified("unchanged module B")
    }

    fun `test module jvm settings`() {
        val moduleA = createModuleInTmpDir("a")
        val moduleB = createModuleInTmpDir("b")

        val trackerA = createTracker(moduleA)
        val trackerB = createTracker(moduleB)

        IdeaTestUtil.setModuleLanguageLevel(moduleA, LanguageLevel.JDK_1_8)

        trackerA.assertModifiedOnce("module A language level is changed")
        trackerB.assertNotModified("unchanged module B")
    }

}

class ModuleStateModificationEventTracker(module: KtModule) : ModuleModificationEventTracker(
    module,
    eventKind = "module state modification",
) {
    override fun configureSubscriptions(busConnection: MessageBusConnection) {
        busConnection.subscribe(
            KotlinTopics.MODULE_STATE_MODIFICATION,
            KotlinModuleStateModificationListener(::handleEvent),
        )
    }
}
