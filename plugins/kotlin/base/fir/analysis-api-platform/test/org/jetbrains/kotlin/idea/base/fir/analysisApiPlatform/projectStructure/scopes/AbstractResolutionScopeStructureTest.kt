// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.analysisApiPlatform.projectStructure.scopes

import com.google.gson.JsonObject
import org.jetbrains.kotlin.analysis.api.KaImplementationDetail
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KaResolutionScopeProvider
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.idea.base.projectStructure.toKaLibraryModules
import org.jetbrains.kotlin.idea.base.projectStructure.toKaSourceModuleForProductionOrTest
import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.projectStructureTest.*
import java.io.File
import kotlin.io.path.Path

/**
 * Tests that resolution scopes provided by [KaResolutionScopeProvider] are correct, especially with respect to scope merging.
 *
 * This test exists on the IntelliJ side for now because the union and intersection scope mergers are currently defined here (see KT-77194).
 */
abstract class AbstractResolutionScopeStructureTest : AbstractProjectStructureTest<ResolutionScopeTestProjectStructure>(
    ResolutionScopeTestProjectStructureParser,
) {
    override fun getTestDataDirectory(): File =
        KotlinRoot.DIR.resolve("base").resolve("fir").resolve("analysis-api-platform").resolve("testData").resolve("resolutionScopes")

    override fun doTestWithProjectStructure(testDirectory: String) {
        testProjectStructure.libraries.forEach { library ->
            val intellijLibrary = projectLibrariesByName[library.name] ?: error("Library '${library.name}' not found.")
            val kaModule = intellijLibrary.toKaLibraryModules(project).singleOrNull()
                ?: error("Expected a single `KaLibraryModule` for '${library.name}'.")

            checkResolutionScope(kaModule, testDirectory, library.name)
        }

        testProjectStructure.modules.forEach { module ->
            val intellijModule = modulesByName[module.name] ?: error("Module '${module.name}' not found.")
            val kaModule = intellijModule.toKaSourceModuleForProductionOrTest() ?: error("Could not get `KaModule` for '${module.name}'.")

            checkResolutionScope(kaModule, testDirectory, module.name)
        }
    }

    @OptIn(KaImplementationDetail::class)
    private fun checkResolutionScope(
        kaModule: KaModule,
        testDirectory: String,
        testModuleName: String,
    ) {
        val resolutionScope = KaResolutionScopeProvider.getInstance(project).getResolutionScope(kaModule)
        val scopeOutput = resolutionScope.underlyingSearchScope.renderAsTestOutput()

        val expectedFile = Path(testDirectory, testModuleName, "resolutionScope.txt")
        KotlinTestUtils.assertEqualsToFile(expectedFile, scopeOutput)
    }
}

data class ResolutionScopeTestProjectStructure(
    override val libraries: List<TestProjectLibrary>,
    override val modules: List<TestProjectModule>,
) : TestProjectStructure

private object ResolutionScopeTestProjectStructureParser : TestProjectStructureParser<ResolutionScopeTestProjectStructure> {
    override fun parse(
        libraries: List<TestProjectLibrary>,
        modules: List<TestProjectModule>,
        json: JsonObject,
    ): ResolutionScopeTestProjectStructure = ResolutionScopeTestProjectStructure(libraries, modules)
}
