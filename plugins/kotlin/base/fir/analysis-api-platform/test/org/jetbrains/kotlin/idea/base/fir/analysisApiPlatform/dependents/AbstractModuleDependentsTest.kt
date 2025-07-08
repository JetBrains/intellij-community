// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.analysisApiPlatform.dependents

import com.google.gson.JsonObject
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinModuleDependentsProvider
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.idea.base.projectStructure.toKaSourceModuleForProduction
import org.jetbrains.kotlin.idea.base.projectStructure.toKaLibraryModules
import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import org.jetbrains.kotlin.idea.base.util.getAsJsonObjectList
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.projectStructureTest.*
import java.io.File
import kotlin.io.path.Path

abstract class AbstractModuleDependentsTest : AbstractProjectStructureTest<ModuleDependentsTestProjectStructure>(
    ModuleDependentsTestProjectStructureParser,
) {
    override fun getTestDataDirectory(): File =
        KotlinRoot.DIR.resolve("base").resolve("fir").resolve("analysis-api-platform").resolve("testData").resolve("moduleDependents")

    private val moduleDependentsProvider get() = KotlinModuleDependentsProvider.getInstance(project)

    override fun doTestWithProjectStructure(testDirectory: String) {
        assertNotEmpty(testProjectStructure.targets)
        testProjectStructure.targets.forEach { checkTarget(it, testDirectory) }
    }

    private fun checkTarget(target: TestProjectEntityReference, testDirectory: String) {
        val targetModule = when (target) {
            is TestProjectLibraryReference ->
                projectLibrariesByName.getValue(target.name).toKaLibraryModules(project).first()

            is TestProjectModuleReference ->
                modulesByName.getValue(target.name).toKaSourceModuleForProduction()!!
        }

        checkDirectDependents(target, targetModule, testDirectory)
        checkTransitiveDependents(target, targetModule, testDirectory)
    }

    private fun checkDirectDependents(target: TestProjectEntityReference, targetModule: KaModule, testDirectory: String) {
        val directDependents = moduleDependentsProvider.getDirectDependents(targetModule)
        checkDependentsAgainstFile(directDependents, testDirectory, target.name, "direct.txt")
    }

    private fun checkTransitiveDependents(target: TestProjectEntityReference, targetModule: KaModule, testDirectory: String) {
        val transitiveDependents = moduleDependentsProvider.getTransitiveDependents(targetModule)
        checkDependentsAgainstFile(transitiveDependents, testDirectory, target.name, "transitive.txt")
    }

    private fun checkDependentsAgainstFile(
        dependents: Collection<KaModule>,
        testDirectory: String,
        targetName: String,
        fileSuffix: String
    ) {
        val expectedFile = Path(testDirectory, targetName, fileSuffix)
        val renderedDependents = if (dependents.isNotEmpty()) {
            dependents.map { renderModuleName(it) }.sorted().joinToString("\n")
        } else {
            "<EMPTY>"
        }
        KotlinTestUtils.assertEqualsToFile(expectedFile, renderedDependents)
    }
}

/**
 * The module dependents test supports multiple module/library targets, which allows checking module dependents of all `KaModule`s defined
 * in the project structure, without duplicating the same project structure in multiple tests.
 */
data class ModuleDependentsTestProjectStructure(
    override val libraries: List<TestProjectLibrary>,
    override val modules: List<TestProjectModule>,
    val targets: List<TestProjectEntityReference>,
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
        json.getAsJsonObjectList(TARGETS_FIELD)!!.map(TestProjectEntityReferenceParser::parse),
    )
}
