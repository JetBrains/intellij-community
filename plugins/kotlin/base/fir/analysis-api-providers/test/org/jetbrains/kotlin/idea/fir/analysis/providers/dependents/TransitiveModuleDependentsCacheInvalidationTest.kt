// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.analysis.providers.dependents

import org.jetbrains.kotlin.analysis.project.structure.KotlinModuleDependentsProvider
import org.jetbrains.kotlin.analysis.project.structure.KtModule
import org.jetbrains.kotlin.analysis.project.structure.KtSourceModule
import org.jetbrains.kotlin.idea.base.projectStructure.LibraryInfoCache
import org.jetbrains.kotlin.idea.base.projectStructure.getMainKtSourceModule
import org.jetbrains.kotlin.idea.base.projectStructure.toKtModule
import org.jetbrains.kotlin.idea.test.AbstractMultiModuleTest
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil
import org.jetbrains.kotlin.idea.test.addDependency
import org.junit.Assert
import java.io.File

/**
 * This test ensures that cached transitive dependents of the IDE's [KotlinModuleDependentsProvider] are properly invalidated.
 */
class TransitiveModuleDependentsCacheInvalidationTest : AbstractMultiModuleTest() {
    override fun getTestDataDirectory(): File = error("Should not be called")

    override fun isFirPlugin(): Boolean = true

    private val moduleDependentsProvider get() = KotlinModuleDependentsProvider.getInstance(project)

    fun `test that cached transitive dependents of a module are invalidated after adding a new dependent`() {
        val moduleA = createModuleInTmpDir("a")
        val moduleB = createModuleInTmpDir("b")
        val moduleC = createModuleInTmpDir("c")
        val moduleD = createModuleInTmpDir("d")

        moduleB.addDependency(moduleA)
        moduleC.addDependency(moduleB)

        val ktModuleA = moduleA.getMainKtSourceModule()!!

        Assert.assertEquals(
            setOf("b", "c"),
            ktModuleA.getTransitiveDependentNames(),
        )

        moduleD.addDependency(moduleA)

        Assert.assertEquals(
            setOf("b", "c", "d"),
            ktModuleA.getTransitiveDependentNames(),
        )
    }

    fun `test that cached transitive dependents of a library are invalidated after adding a new dependent`() {
        val libraryA = ConfigLibraryUtil.addProjectLibraryWithClassesRoot(project, "a")
        val moduleB = createModuleInTmpDir("b")
        val moduleC = createModuleInTmpDir("c")
        val moduleD = createModuleInTmpDir("d")

        moduleB.addDependency(libraryA)
        moduleC.addDependency(moduleB)

        val ktModuleA = LibraryInfoCache.getInstance(project)[libraryA].first().toKtModule()

        Assert.assertEquals(
            setOf("b", "c"),
            ktModuleA.getTransitiveDependentNames(),
        )

        moduleD.addDependency(libraryA)

        Assert.assertEquals(
            setOf("b", "c", "d"),
            ktModuleA.getTransitiveDependentNames(),
        )
    }

    private fun KtModule.getTransitiveDependentNames(): Set<String> =
        moduleDependentsProvider.getTransitiveDependents(this).mapTo(mutableSetOf()) { (it as KtSourceModule).moduleName }
}
