// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.analysis.providers.dependents

import com.google.gson.JsonObject
import java.io.File
import org.jetbrains.kotlin.analysis.project.structure.KotlinModuleDependentsProvider
import org.jetbrains.kotlin.analysis.project.structure.KtSourceModule
import org.jetbrains.kotlin.idea.base.projectStructure.LibraryInfoCache
import org.jetbrains.kotlin.idea.base.projectStructure.getMainKtSourceModule
import org.jetbrains.kotlin.idea.base.projectStructure.toKtModule
import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import org.jetbrains.kotlin.idea.fir.analysis.providers.AbstractProjectStructureTest
import org.jetbrains.kotlin.idea.fir.analysis.providers.TestProjectLibrary
import org.jetbrains.kotlin.idea.fir.analysis.providers.TestProjectModule
import org.jetbrains.kotlin.idea.fir.analysis.providers.TestProjectStructure
import org.jetbrains.kotlin.idea.fir.analysis.providers.TestProjectStructureParser
import org.jetbrains.kotlin.idea.fir.analysis.providers.testProjectStructure.TestProjectEntityReference
import org.jetbrains.kotlin.idea.fir.analysis.providers.testProjectStructure.TestProjectEntityReferenceParser
import org.jetbrains.kotlin.idea.fir.analysis.providers.testProjectStructure.TestProjectLibraryReference
import org.jetbrains.kotlin.idea.fir.analysis.providers.testProjectStructure.TestProjectModuleReference
import org.jetbrains.kotlin.idea.fir.analysis.providers.testProjectStructure.getAsJsonObjectList
import org.jetbrains.kotlin.idea.fir.analysis.providers.testProjectStructure.getAsStringList
import org.jetbrains.kotlin.idea.fir.analysis.providers.ProjectLibrariesByName
import org.jetbrains.kotlin.idea.fir.analysis.providers.ModulesByName

abstract class AbstractModuleDependentsTest : AbstractProjectStructureTest<ModuleDependentsTestProjectStructure>() {
    override fun isFirPlugin(): Boolean = true

    override fun getTestDataDirectory(): File =
        KotlinRoot.DIR.resolve("base").resolve("fir").resolve("analysis-api-providers").resolve("testData").resolve("moduleDependents")

    private val moduleDependentsProvider get() = KotlinModuleDependentsProvider.getInstance(project)

    protected fun doTest(path: String) {
        val (testStructure, projectLibrariesByName, modulesByName) =
            initializeProjectStructure(path, ModuleDependentsTestProjectStructureParser)

        assertNotEmpty(testStructure.targets)

        testStructure.targets.forEach { checkTarget(it, projectLibrariesByName, modulesByName) }
    }

    private fun checkTarget(
        target: ModuleDependentsTestTarget,
        projectLibrariesByName: ProjectLibrariesByName,
        modulesByName: ModulesByName,
    ) {
        val entityReference = target.entityReference

        val targetModule = when (entityReference) {
            is TestProjectLibraryReference ->
                LibraryInfoCache.getInstance(project)[projectLibrariesByName.getValue(entityReference.name)].first().toKtModule()

            is TestProjectModuleReference ->
                modulesByName.getValue(entityReference.name).getMainKtSourceModule()!!
        }

        val directDependents = moduleDependentsProvider.getDirectDependents(targetModule)
        assertEquals(
            "Direct dependents of ${entityReference.name}:",
            target.directDependents,
            directDependents.map { (it as KtSourceModule).moduleName }.toSet(),
        )

        val transitiveDependents = moduleDependentsProvider.getTransitiveDependents(targetModule)
        assertEquals(
            "Transitive dependents of ${entityReference.name}:",
            target.transitiveDependents,
            transitiveDependents.map { (it as KtSourceModule).moduleName }.toSet(),
        )
    }
}

/**
 * The module dependents test supports multiple module/library targets, which allows checking module dependents of all `KtModule`s defined
 * in the project structure, without duplicating the same project structure in multiple tests.
 */
data class ModuleDependentsTestProjectStructure(
    override val libraries: List<TestProjectLibrary>,
    override val modules: List<TestProjectModule>,
    val targets: List<ModuleDependentsTestTarget>,
) : TestProjectStructure

private object ModuleDependentsTestProjectStructureParser : TestProjectStructureParser<ModuleDependentsTestProjectStructure> {
    private const val TARGETS_FIELD = "targets"

    override fun parse(
        libraries: List<TestProjectLibrary>,
        modules: List<TestProjectModule>,
        json: JsonObject,
    ): ModuleDependentsTestProjectStructure = ModuleDependentsTestProjectStructure(
        libraries,
        modules,
        json.getAsJsonObjectList(TARGETS_FIELD)!!.map(ModuleDependentsTestTargetParser::parse),
    )
}

data class ModuleDependentsTestTarget(
    val entityReference: TestProjectEntityReference,
    val directDependents: Set<String>,
    val transitiveDependents: Set<String>,
)

private object ModuleDependentsTestTargetParser {
    private const val DIRECT_DEPENDENTS_FIELD = "directDependents"
    private const val TRANSITIVE_DEPENDENTS_FIELD = "transitiveDependents"

    fun parse(json: JsonObject): ModuleDependentsTestTarget {
        // We can parse `json` as the entity reference itself to avoid boilerplate around the entity reference.
        val entityReference = TestProjectEntityReferenceParser.parse(json)

        return ModuleDependentsTestTarget(
            entityReference,
            json.getAsStringList(DIRECT_DEPENDENTS_FIELD)!!.toSet(),
            json.getAsStringList(TRANSITIVE_DEPENDENTS_FIELD)!!.toSet(),
        )
    }
}
