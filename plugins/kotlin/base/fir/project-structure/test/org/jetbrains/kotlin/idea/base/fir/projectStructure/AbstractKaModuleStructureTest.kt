// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.projectStructure

import com.google.gson.JsonObject
import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.projectStructureTest.*
import java.io.File
import java.nio.file.Paths
import kotlin.io.path.div

abstract class AbstractKaModuleStructureTest : AbstractProjectStructureTest<ProjectStructureTestStructure>(
    ProjectStructureTestStructure.Parser
) {
    override fun getTestDataDirectory(): File =
        (KotlinRoot.DIR.toPath() / "base" / "fir" / "project-structure" / "testData" / "kaModuleStructure").toFile()

    override fun doTestWithProjectStructure(testDirectory: String) {
        val allModules = project.getAllKaModules()

        val txt = KaModuleStructureTxtRenderer.render(allModules)
        KotlinTestUtils.assertEqualsToFile(Paths.get(testDirectory, "kaModules.txt"), txt)

        val mermaid = KaModuleStructureMermaidRenderer.render(allModules)
        KotlinTestUtils.assertEqualsToFile(Paths.get(testDirectory, "kaModules.mmd"), mermaid)
    }
}

class ProjectStructureTestStructure(
    override val libraries: List<TestProjectLibrary>,
    override val modules: List<TestProjectModule>
) : TestProjectStructure {
    object Parser : TestProjectStructureParser<ProjectStructureTestStructure> {
        override fun parse(
            libraries: List<TestProjectLibrary>,
            modules: List<TestProjectModule>,
            json: JsonObject,
        ): ProjectStructureTestStructure {
            return ProjectStructureTestStructure(libraries, modules)
        }
    }
}
