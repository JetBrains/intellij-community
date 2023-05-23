// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.analysis.providers.testProjectStructure

import com.google.gson.JsonObject
import org.jetbrains.kotlin.idea.jsonUtils.getNullableString

/**
 * A reference that points to a library or module in the test project structure.
 */
sealed interface TestProjectEntityReference

data class TestProjectLibraryReference(val name: String) : TestProjectEntityReference

data class TestProjectModuleReference(val name: String) : TestProjectEntityReference

object TestProjectEntityReferenceParser {
    private const val LIBRARY_FIELD = "library"
    private const val MODULE_FIELD = "module"

    fun parse(json: JsonObject): TestProjectEntityReference {
        val libraryTarget = json.getNullableString(LIBRARY_FIELD)?.let { TestProjectLibraryReference(it) }
        val moduleTarget = json.getNullableString(MODULE_FIELD)?.let { TestProjectModuleReference(it) }

        if (libraryTarget == null && moduleTarget == null || libraryTarget != null && moduleTarget != null) {
            error("Expected exactly one of `$LIBRARY_FIELD` and `$MODULE_FIELD` to be specified.")
        }

        return libraryTarget ?: moduleTarget!!
    }
}
