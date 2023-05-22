// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.analysis.providers

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.kotlin.idea.jsonUtils.getString
import java.nio.file.Path

interface TestProjectStructure {
    val modules: List<TestProjectModule>
}

data class TestProjectModule(val name: String, val dependsOnModules: List<String>) {
    companion object {
        fun parse(json: JsonElement): TestProjectModule {
            require(json is JsonObject)
            val dependencies = if (json.has(DEPENDS_ON_FIELD)) {
                json.getAsJsonArray(DEPENDS_ON_FIELD).map { (it as JsonPrimitive).asString }
            } else emptyList()
            return TestProjectModule(
                json.getString("name"),
                dependencies
            )
        }

        private const val DEPENDS_ON_FIELD = "dependsOn"
    }
}

internal object TestProjectStructureReader {
    fun  <S : TestProjectStructure> read(
        testDirectory: Path,
        jsonFileName: String = "structure.json",
        parser: TestProjectStructureParser<S>,
    ): S {
        val jsonFile = testDirectory.resolve(jsonFileName)

        val json = JsonParser.parseString(FileUtil.loadFile(jsonFile.toFile(), /*convertLineSeparators=*/true))
        require(json is JsonObject)

        val modules = json.getAsJsonArray("modules").map(TestProjectModule.Companion::parse)
        return parser.parse(modules, json)
    }

    fun <S : TestProjectStructure> readToTestStructure(
        testDirectory: Path,
        jsonFileName: String = "structure.json",
        testProjectStructureParser: TestProjectStructureParser<S>,
    ): S = read(testDirectory, jsonFileName, testProjectStructureParser)
}

fun interface TestProjectStructureParser<S : TestProjectStructure> {
    /**
     * Parses a [TestProjectStructure] from an already parsed list of [TestProjectModule]s and the original [JsonObject].
     */
    fun parse(modules: List<TestProjectModule>, json: JsonObject): S
}
