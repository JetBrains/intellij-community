// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.analysis.providers.modificationEvents

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PsiTestUtil
import org.jetbrains.kotlin.analysis.project.structure.KtModule
import org.jetbrains.kotlin.analysis.project.structure.ProjectStructureProvider
import org.jetbrains.kotlin.analysis.providers.analysisMessageBus
import org.jetbrains.kotlin.analysis.providers.topics.KotlinModuleStateModificationListener
import org.jetbrains.kotlin.analysis.providers.topics.KotlinTopics
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.idea.base.projectStructure.LibraryInfoCache
import org.jetbrains.kotlin.idea.base.projectStructure.productionSourceInfo
import org.jetbrains.kotlin.idea.base.projectStructure.toKtModule
import org.jetbrains.kotlin.idea.stubs.AbstractMultiModuleTest
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil
import org.jetbrains.kotlin.idea.test.addEmptyClassesRoot
import org.jetbrains.kotlin.psi.KtFile
import org.junit.Assert
import java.io.File
import org.jetbrains.kotlin.test.util.addDependency

class KotlinModuleStateModificationTest : AbstractMultiModuleTest() {
    override fun isFirPlugin(): Boolean = true

    override fun getTestDataDirectory(): File = error("Should not be called")

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

        disposeTrackers(trackerA, trackerB, trackerC)
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

        disposeTrackers(trackerA, trackerB, trackerC)
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

        disposeTrackers(trackerA, trackerB, trackerC)
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

        disposeTrackers(trackerA, trackerB, trackerC)
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

        disposeTrackers(trackerA, trackerB, trackerC)
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

        disposeTrackers(trackerA, trackerB, trackerC)
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

        disposeTrackers(trackerA, trackerB, trackerC)
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

        disposeTrackers(trackerA, trackerB, trackerC)
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

        disposeTrackers(trackerA, trackerB, trackerC, trackerD)
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

        disposeTrackers(trackerA, trackerB, trackerC, trackerD)
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

        disposeTrackers(trackerA, trackerB, trackerC, trackerD, trackerE)
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

        disposeTrackers(trackerA, trackerB, trackerC, trackerD)
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

        disposeTrackers(trackerA, trackerB, trackerC, trackerD)
    }

    private fun createProjectLibrary(name: String): Library = ConfigLibraryUtil.addProjectLibraryWithClassesRoot(myProject, name)

    private fun createScript(name: String): KtFile = createKtFileUnderNewContentRoot(FileWithText("$name.kts", ""))

    private fun createTracker(module: KtModule): ModuleStateModificationTracker = ModuleStateModificationTracker(module)

    private fun createTracker(module: Module): ModuleStateModificationTracker = createTracker(module.productionSourceInfo!!.toKtModule())

    private fun createTracker(library: Library): ModuleStateModificationTracker =
        createTracker(LibraryInfoCache.getInstance(myProject)[library].single().toKtModule())

    private fun createTracker(file: KtFile): ModuleStateModificationTracker =
        createTracker(ProjectStructureProvider.getModule(myProject, file, contextualModule = null))

    private fun disposeTrackers(vararg trackers: ModuleStateModificationTracker) {
        trackers.forEach { Disposer.dispose(it) }
    }

    private class ModuleStateModificationTracker(val module: KtModule) : Disposable {
        private class ReceivedEvent(val isRemoval: Boolean)

        private val receivedEvents: MutableList<ReceivedEvent> = mutableListOf()

        init {
            val busConnection = module.project.analysisMessageBus.connect(this)
            busConnection.subscribe(
                KotlinTopics.MODULE_STATE_MODIFICATION,
                KotlinModuleStateModificationListener { eventModule, isRemoval ->
                    if (eventModule == module) {
                        receivedEvents.add(ReceivedEvent(isRemoval))
                    }
                }
            )
        }

        override fun dispose() { }

        fun assertNotModified(label: String) {
            Assert.assertTrue(
                "Module state modification events for '$label' should not have been published, but ${receivedEvents.size} events were received.",
                receivedEvents.isEmpty(),
            )
        }

        fun assertModifiedOnce(label: String, shouldBeRemoval: Boolean = false) {
            Assert.assertTrue(
                "A single module state modification event for '$label' should have been published, but ${receivedEvents.size} events were received.",
                receivedEvents.size == 1,
            )

            val receivedEvent = receivedEvents.single()
            val shouldOrShouldNot = if (shouldBeRemoval) "should" else "should not"
            Assert.assertTrue(
                "The module state modification event for '$label' $shouldOrShouldNot be a removal event.",
                receivedEvent.isRemoval == shouldBeRemoval,
            )
        }
    }
}
