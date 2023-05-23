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

abstract class AbstractModuleDependentsTest : AbstractProjectStructureTest<ModuleDependentsTestProjectStructure>() {
    override fun isFirPlugin(): Boolean = true

    override fun getTestDataDirectory(): File =
        KotlinRoot.DIR.resolve("base").resolve("fir").resolve("analysis-api-providers").resolve("testData").resolve("moduleDependents")

    protected fun doTest(path: String) {
        val (testStructure, projectLibrariesByName, modulesByNames) =
            initializeProjectStructure(path, ModuleDependentsTestProjectStructureParser)

        val targetModule = when (val target = testStructure.target) {
            is TestProjectLibraryReference ->
                LibraryInfoCache.getInstance(project)[projectLibrariesByName.getValue(target.name)].first().toKtModule()

            is TestProjectModuleReference ->
                modulesByNames.getValue(target.name).getMainKtSourceModule()!!
        }

        val moduleDependentsProvider = KotlinModuleDependentsProvider.getInstance(project)

        val directDependents = moduleDependentsProvider.getDirectDependents(targetModule)
        assertEquals(
            testStructure.directDependents,
            directDependents.map { (it as KtSourceModule).moduleName }.toSet(),
        )

        val transitiveDependents = moduleDependentsProvider.getTransitiveDependents(targetModule)
        assertEquals(
            testStructure.transitiveDependents,
            transitiveDependents.map { (it as KtSourceModule).moduleName }.toSet(),
        )
    }
}

data class ModuleDependentsTestProjectStructure(
    override val libraries: List<TestProjectLibrary>,
    override val modules: List<TestProjectModule>,
    val target: TestProjectEntityReference,
    val directDependents: Set<String>,
    val transitiveDependents: Set<String>,
) : TestProjectStructure

private object ModuleDependentsTestProjectStructureParser : TestProjectStructureParser<ModuleDependentsTestProjectStructure> {
    private const val TARGET_FIELD = "target"
    private const val DIRECT_DEPENDENTS_FIELD = "directDependents"
    private const val TRANSITIVE_DEPENDENTS_FIELD = "transitiveDependents"

    override fun parse(
        libraries: List<TestProjectLibrary>,
        modules: List<TestProjectModule>,
        json: JsonObject,
    ): ModuleDependentsTestProjectStructure = ModuleDependentsTestProjectStructure(
        libraries,
        modules,
        json.getAsJsonObject(TARGET_FIELD).let(TestProjectEntityReferenceParser::parse),
        json.getAsJsonArray(DIRECT_DEPENDENTS_FIELD)!!.map { it.asString }.toSet(),
        json.getAsJsonArray(TRANSITIVE_DEPENDENTS_FIELD)!!.map { it.asString }.toSet(),
    )
}
