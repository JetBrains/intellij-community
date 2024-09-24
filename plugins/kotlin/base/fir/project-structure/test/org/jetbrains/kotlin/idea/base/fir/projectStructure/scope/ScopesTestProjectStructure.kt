// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.projectStructure.scope

import com.google.gson.JsonObject
import org.jetbrains.kotlin.idea.test.projectStructureTest.TestProjectLibrary
import org.jetbrains.kotlin.idea.test.projectStructureTest.TestProjectModule
import org.jetbrains.kotlin.idea.test.projectStructureTest.TestProjectStructure
import org.jetbrains.kotlin.idea.test.projectStructureTest.TestProjectStructureParser

/**
 * [excludedLibraries] and [excludedModules] are libraries/modules which *should not* be included in the combined scopes. The test will
 * exclude these accordingly.
 */
class ScopesTestProjectStructure(
    override val libraries: List<TestProjectLibrary>,
    override val modules: List<TestProjectModule>,
    val excludedLibraries: List<String>,
    val excludedModules: List<String>,
) : TestProjectStructure

object ScopesTestProjectStructureParser : TestProjectStructureParser<ScopesTestProjectStructure> {
    private const val EXCLUDED_LIBRARIES_FIELD = "excluded_libraries"
    private const val EXCLUDED_MODULES_FIELD = "excluded_modules"

    override fun parse(
        libraries: List<TestProjectLibrary>,
        modules: List<TestProjectModule>,
        json: JsonObject,
    ): ScopesTestProjectStructure {
        val excludedLibraries = json.getAsJsonArray(EXCLUDED_LIBRARIES_FIELD)?.map { it.asString } ?: emptyList()
        val excludedModules = json.getAsJsonArray(EXCLUDED_MODULES_FIELD)?.map { it.asString } ?: emptyList()

        return ScopesTestProjectStructure(libraries, modules, excludedLibraries, excludedModules)
    }
}
