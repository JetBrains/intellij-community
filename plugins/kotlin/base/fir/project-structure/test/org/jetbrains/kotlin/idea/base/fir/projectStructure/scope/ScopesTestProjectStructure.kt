// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.projectStructure.scope

import com.google.gson.JsonObject
import org.jetbrains.kotlin.idea.test.projectStructureTest.TestProjectLibrary
import org.jetbrains.kotlin.idea.test.projectStructureTest.TestProjectModule
import org.jetbrains.kotlin.idea.test.projectStructureTest.TestProjectStructure
import org.jetbrains.kotlin.idea.test.projectStructureTest.TestProjectStructureParser

class ScopesTestProjectStructure(
    override val libraries: List<TestProjectLibrary>,
    override val modules: List<TestProjectModule>,
) : TestProjectStructure

object ScopesTestProjectStructureParser : TestProjectStructureParser<ScopesTestProjectStructure> {
    override fun parse(
        libraries: List<TestProjectLibrary>,
        modules: List<TestProjectModule>,
        json: JsonObject,
    ): ScopesTestProjectStructure = ScopesTestProjectStructure(libraries, modules)
}
