// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.analysisApiPlatform.dependents

import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinModuleDependentsProvider
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.base.projectStructure.toKaSourceModuleForProduction
import org.jetbrains.kotlin.idea.base.projectStructure.toKaLibraryModules
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

    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K2

    private val moduleDependentsProvider get() = KotlinModuleDependentsProvider.getInstance(project)

    fun `test that cached transitive dependents of a module are invalidated after adding a new dependent`() {
        val moduleA = createModuleInTmpDir("a")
        val moduleB = createModuleInTmpDir("b")
        val moduleC = createModuleInTmpDir("c")
        val moduleD = createModuleInTmpDir("d")

        moduleB.addDependency(moduleA)
        moduleC.addDependency(moduleB)

        val ktModuleA = moduleA.toKaSourceModuleForProduction()!!

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

        val ktModuleA = libraryA.toKaLibraryModules(project).first()

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

    private fun KaModule.getTransitiveDependentNames(): Set<String> =
        moduleDependentsProvider.getTransitiveDependents(this).mapTo(mutableSetOf()) { (it as KaSourceModule).name }
}
