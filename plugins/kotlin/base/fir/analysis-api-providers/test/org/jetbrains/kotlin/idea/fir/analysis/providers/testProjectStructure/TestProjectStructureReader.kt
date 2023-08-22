// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.analysis.providers.testProjectStructure

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.util.io.FileUtil
import java.nio.file.Path
import org.jetbrains.kotlin.idea.fir.analysis.providers.TestProjectLibraryParser
import org.jetbrains.kotlin.idea.fir.analysis.providers.TestProjectModuleParser
import org.jetbrains.kotlin.idea.fir.analysis.providers.TestProjectStructure
import org.jetbrains.kotlin.idea.fir.analysis.providers.TestProjectStructureParser

internal object TestProjectStructureReader {
    fun <S : TestProjectStructure> read(
        testDirectory: Path,
        jsonFileName: String = "structure.json",
        parser: TestProjectStructureParser<S>,
    ): S {
        val jsonFile = testDirectory.resolve(jsonFileName)

        @Suppress("DEPRECATION") // AS 4.0
        val json = JsonParser().parse(FileUtil.loadFile(jsonFile.toFile(), /*convertLineSeparators=*/true))
        require(json is JsonObject)

        val libraries = json.getAsJsonArray("libraries")?.map(TestProjectLibraryParser::parse) ?: emptyList()
        val modules = json.getAsJsonArray("modules")!!.map(TestProjectModuleParser::parse)
        return parser.parse(libraries, modules, json)
    }

    fun <S : TestProjectStructure> readToTestStructure(
        testDirectory: Path,
        jsonFileName: String = "structure.json",
        testProjectStructureParser: TestProjectStructureParser<S>,
    ): S = read(testDirectory, jsonFileName, testProjectStructureParser)
}
