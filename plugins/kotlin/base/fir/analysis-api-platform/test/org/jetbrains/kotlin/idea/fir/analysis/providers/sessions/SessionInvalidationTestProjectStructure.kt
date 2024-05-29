// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.analysis.providers.sessions

import com.google.gson.JsonObject
import org.jetbrains.kotlin.idea.base.util.getString
import org.jetbrains.kotlin.idea.test.projectStructureTest.TestProjectLibrary
import org.jetbrains.kotlin.idea.test.projectStructureTest.TestProjectModule
import org.jetbrains.kotlin.idea.test.projectStructureTest.TestProjectStructure
import org.jetbrains.kotlin.idea.test.projectStructureTest.TestProjectStructureParser

data class SessionInvalidationTestProjectStructure(
    override val libraries: List<TestProjectLibrary>,
    override val modules: List<TestProjectModule>,
    val rootModule: String,
    val modulesToMakeOOBM: List<String>,
    val expectedInvalidatedModules: List<String>,
) : TestProjectStructure

object SessionInvalidationTestProjectStructureParser : TestProjectStructureParser<SessionInvalidationTestProjectStructure> {
    private const val ROOT_MODULE_FIELD = "rootModule"
    private const val MODULES_TO_MAKE_OOBM_IN_FIELD = "modulesToMakeOOBM"
    private const val EXPECTED_INVALIDATED_MODULES_FIELD = "expectedInvalidatedModules"

    override fun parse(
        libraries: List<TestProjectLibrary>,
        modules: List<TestProjectModule>,
        json: JsonObject,
    ): SessionInvalidationTestProjectStructure =
        SessionInvalidationTestProjectStructure(
            libraries,
            modules,
            json.getString(ROOT_MODULE_FIELD),
            json.getAsJsonArray(MODULES_TO_MAKE_OOBM_IN_FIELD)!!.map { it.asString }.sorted(),
            json.getAsJsonArray(EXPECTED_INVALIDATED_MODULES_FIELD)!!.map { it.asString }.sorted(),
        )
}
