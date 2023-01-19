// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.analysis.providers.trackers

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.testFramework.PsiTestUtil
import org.jetbrains.kotlin.analysis.providers.KotlinModificationTrackerFactory
import org.jetbrains.kotlin.analysis.providers.KtModuleStateTracker
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.idea.base.projectStructure.getMainKtSourceModule
import org.jetbrains.kotlin.idea.stubs.AbstractMultiModuleTest
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil
import org.junit.Assert
import java.io.File

class ModuleStateTrackerTest : AbstractMultiModuleTest() {
    override fun isFirPlugin(): Boolean = true

    override fun getTestDataDirectory(): File = error("Should not be called")

    fun testThatModuleModificationTrackedChangedAfterAddingModuleDependency() {
        val moduleA = createModuleInTmpDir("a")
        val moduleB = createModuleInTmpDir("b")
        val moduleC = createModuleInTmpDir("c")

        val moduleAWithTracker = ModuleWithModuleStateTracker(moduleA)
        val moduleBWithTracker = ModuleWithModuleStateTracker(moduleB)
        val moduleCWithTracker = ModuleWithModuleStateTracker(moduleC)

        moduleA.addDependency(moduleB)

        Assert.assertTrue(
            "Root modification count for module A with dependencies added should change, modification count is ${moduleAWithTracker.modificationCount}",
            moduleAWithTracker.changed()
        )
        Assert.assertFalse(
            "Root modification count for module B without dependencies added should not change, modification count is ${moduleBWithTracker.modificationCount}",
            moduleBWithTracker.changed()
        )
        Assert.assertFalse(
            "Root modification count for module C without dependencies added should not change, modification count is ${moduleCWithTracker.modificationCount}",
            moduleCWithTracker.changed()
        )
    }

    fun testThatModuleModificationTrackedChangedAfterAddingModuleRoots() {
        val moduleA = createModuleInTmpDir("a")
        val moduleB = createModuleInTmpDir("b")
        val moduleC = createModuleInTmpDir("c")

        val moduleAWithTracker = ModuleWithModuleStateTracker(moduleA)
        val moduleBWithTracker = ModuleWithModuleStateTracker(moduleB)
        val moduleCWithTracker = ModuleWithModuleStateTracker(moduleC)

        moduleA.addContentRoot(createTempDirectory().toPath())

        Assert.assertTrue(
            "Root modification count for module A with dependencies added should change, modification count is ${moduleAWithTracker.modificationCount}",
            moduleAWithTracker.changed()
        )
        Assert.assertFalse(
            "Root modification count for module B without dependencies added should not change, modification count is ${moduleBWithTracker.modificationCount}",
            moduleBWithTracker.changed()
        )
        Assert.assertFalse(
            "Root modification count for module C without dependencies added should not change, modification count is ${moduleCWithTracker.modificationCount}",
            moduleCWithTracker.changed()
        )
    }

    fun testThatModuleModificationTrackedChangedAfterRemovingModuleRoots() {
        val moduleA = createModuleInTmpDir("a")
        val moduleB = createModuleInTmpDir("b")
        val moduleC = createModuleInTmpDir("c")

        val root = moduleA.addContentRoot(createTempDirectory().toPath())

        val moduleAWithTracker = ModuleWithModuleStateTracker(moduleA)
        val moduleBWithTracker = ModuleWithModuleStateTracker(moduleB)
        val moduleCWithTracker = ModuleWithModuleStateTracker(moduleC)

        PsiTestUtil.removeContentEntry(moduleA, root.file!!)

        Assert.assertTrue(
            "Root modification count for module A with dependencies added should change, modification count is ${moduleAWithTracker.modificationCount}",
            moduleAWithTracker.changed()
        )
        Assert.assertFalse(
            "Root modification count for module B without dependencies added should not change, modification count is ${moduleBWithTracker.modificationCount}",
            moduleBWithTracker.changed()
        )
        Assert.assertFalse(
            "Root modification count for module C without dependencies added should not change, modification count is ${moduleCWithTracker.modificationCount}",
            moduleCWithTracker.changed()
        )
    }


    fun testThatModuleModificationTrackedChangedAfterAddingLibraryDependency() {
        val moduleA = createModuleInTmpDir("a")
        val moduleB = createModuleInTmpDir("b")
        val moduleC = createModuleInTmpDir("c")

        val moduleAWithTracker = ModuleWithModuleStateTracker(moduleA)
        val moduleBWithTracker = ModuleWithModuleStateTracker(moduleB)
        val moduleCWithTracker = ModuleWithModuleStateTracker(moduleC)

        moduleA.addLibrary(TestKotlinArtifacts.kotlinTestJunit)

        Assert.assertTrue(
            "Root modification count for module A with dependencies added should change, modification count is ${moduleAWithTracker.modificationCount}",
            moduleAWithTracker.changed()
        )
        Assert.assertFalse(
            "Root modification count for module B without dependencies added should not change, modification count is ${moduleBWithTracker.modificationCount}",
            moduleBWithTracker.changed()
        )
        Assert.assertFalse(
            "Root modification count for module C without dependencies added should not change, modification count is ${moduleCWithTracker.modificationCount}",
            moduleCWithTracker.changed()
        )
    }


    fun testThatModuleModificationTrackedChangedAfterRemovingModuleDependency() {
        val moduleA = createModuleInTmpDir("a")
        val moduleB = createModuleInTmpDir("b")
        val moduleC = createModuleInTmpDir("c")

        moduleA.addDependency(moduleB)

        val moduleAWithTracker = ModuleWithModuleStateTracker(moduleA)
        val moduleBWithTracker = ModuleWithModuleStateTracker(moduleB)
        val moduleCWithTracker = ModuleWithModuleStateTracker(moduleC)

        moduleA.removeDependency(moduleB)

        Assert.assertTrue(
            "Root modification count for module A with dependencies removed should change, modification count is ${moduleAWithTracker.modificationCount}",
            moduleAWithTracker.changed()
        )
        Assert.assertFalse(
            "Root modification count for module B without dependencies removed should not change, modification count is ${moduleBWithTracker.modificationCount}",
            moduleBWithTracker.changed()
        )
        Assert.assertFalse(
            "Root modification count for module C without dependencies removed should not change, modification count is ${moduleCWithTracker.modificationCount}",
            moduleCWithTracker.changed()
        )
    }

    fun testThatModuleModificationTrackedChangedAfterRemovingLibraryDependency() {
        val moduleA = createModuleInTmpDir("a")
        val moduleB = createModuleInTmpDir("b")
        val moduleC = createModuleInTmpDir("c")

        moduleA.addLibrary(TestKotlinArtifacts.kotlinTestJunit, name = "junit")

        val moduleAWithTracker = ModuleWithModuleStateTracker(moduleA)
        val moduleBWithTracker = ModuleWithModuleStateTracker(moduleB)
        val moduleCWithTracker = ModuleWithModuleStateTracker(moduleC)

        ConfigLibraryUtil.removeLibrary(moduleA, "junit")

        Assert.assertTrue(
            "Root modification count for module A with dependencies removed should change, modification count is ${moduleAWithTracker.modificationCount}",
            moduleAWithTracker.changed()
        )
        Assert.assertFalse(
            "Root modification count for module B without dependencies removed should not change, modification count is ${moduleBWithTracker.modificationCount}",
            moduleBWithTracker.changed()
        )
        Assert.assertFalse(
            "Root modification count for module C without dependencies removed should not change, modification count is ${moduleCWithTracker.modificationCount}",
            moduleCWithTracker.changed()
        )
    }

    fun testThatModuleIsInvalidatedAfterRemoval() {
        val moduleA = createModuleInTmpDir("a")
        val moduleB = createModuleInTmpDir("b")
        val moduleC = createModuleInTmpDir("c")

        val moduleAWithTracker = ModuleWithModuleStateTracker(moduleA)
        val moduleBWithTracker = ModuleWithModuleStateTracker(moduleB)
        val moduleCWithTracker = ModuleWithModuleStateTracker(moduleC)

        ModuleManager.getInstance(myProject).disposeModule(moduleA)

        Assert.assertTrue(
            "Disposed module A should be invalidated",
            moduleAWithTracker.isInvalidated()
        )
        Assert.assertFalse(
            "not disposed module B should not be invalidated",
            moduleBWithTracker.isInvalidated()
        )
        Assert.assertFalse(
            "not disposed module C should not be invalidated",
            moduleCWithTracker.isInvalidated()
        )
    }


    abstract class WithModuleStateTracker(val state: KtModuleStateTracker) {
        private val initialModificationCount = state.rootModificationCount
        val modificationCount: Long get() = state.rootModificationCount

        fun changed(): Boolean =
            modificationCount != initialModificationCount

        fun isInvalidated(): Boolean = !state.isValid
    }


    private class ModuleWithModuleStateTracker(module: Module) : WithModuleStateTracker(
        KotlinModificationTrackerFactory.getService(module.project).createModuleStateTracker(module.getMainKtSourceModule()!!)
    )
}