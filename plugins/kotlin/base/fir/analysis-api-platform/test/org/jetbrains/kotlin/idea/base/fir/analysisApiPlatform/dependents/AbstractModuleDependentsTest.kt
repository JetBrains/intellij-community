// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.analysisApiPlatform.dependents

import com.google.gson.JsonObject
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinModuleDependentsProvider
import org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule
import org.jetbrains.kotlin.idea.base.projectStructure.toKaSourceModuleForProduction
import org.jetbrains.kotlin.idea.base.projectStructure.toKaLibraryModules
import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import org.jetbrains.kotlin.idea.base.util.getAsJsonObjectList
import org.jetbrains.kotlin.idea.base.util.getAsStringList
import org.jetbrains.kotlin.idea.test.projectStructureTest.*
import java.io.File

abstract class AbstractModuleDependentsTest : AbstractProjectStructureTest<ModuleDependentsTestProjectStructure>(
    ModuleDependentsTestProjectStructureParser,
) {

    override fun getTestDataDirectory(): File =
        KotlinRoot.DIR.resolve("base").resolve("fir").resolve("analysis-api-platform").resolve("testData").resolve("moduleDependents")

    private val moduleDependentsProvider get() = KotlinModuleDependentsProvider.getInstance(project)

    override fun doTestWithProjectStructure(testDirectory: String) {
        assertNotEmpty(testProjectStructure.targets)
        testProjectStructure.targets.forEach(::checkTarget)
    }

    private fun checkTarget(target: ModuleDependentsTestTarget) {
        val entityReference = target.entityReference

        val targetModule = when (entityReference) {
            is TestProjectLibraryReference ->
                projectLibrariesByName.getValue(entityReference.name).toKaLibraryModules(project).first()

            is TestProjectModuleReference ->
                modulesByName.getValue(entityReference.name).toKaSourceModuleForProduction()!!
        }

        val directDependents = moduleDependentsProvider.getDirectDependents(targetModule)
        assertEquals(
            "Direct dependents of ${entityReference.name}:",
            target.directDependents,
            directDependents.map { (it as KaSourceModule).name }.toSet(),
        )

        val transitiveDependents = moduleDependentsProvider.getTransitiveDependents(targetModule)
        assertEquals(
            "Transitive dependents of ${entityReference.name}:",
            target.transitiveDependents,
            transitiveDependents.map { (it as KaSourceModule).name }.toSet(),
        )
    }
}

/**
 * The module dependents test supports multiple module/library targets, which allows checking module dependents of all `KaModule`s defined
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
