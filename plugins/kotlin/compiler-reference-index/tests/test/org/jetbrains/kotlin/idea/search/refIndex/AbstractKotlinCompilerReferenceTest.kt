// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.search.refIndex

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.compiler.impl.CompileDriver
import com.intellij.testFramework.assertEqualsToFile
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import kotlin.io.path.*

abstract class AbstractKotlinCompilerReferenceTest : KotlinCompilerReferenceTestBase() {
    override fun setUp() {
        super.setUp()
        installCompiler()
    }

    protected fun doTest(testDataFilePath: String) {

        myFixture.testDataPath = testDataFilePath

        val configurationPath = Path(testDataFilePath, "testConfig.json")
        val isK2 = pluginMode == KotlinPluginMode.K2
        val firConfigurationPath = Path(testDataFilePath, "testConfig.fir.json")
            .takeIf { isK2 && it.exists() }
        val pathToCheck = firConfigurationPath ?: configurationPath
        val config: JsonObject = JsonParser.parseReader(pathToCheck.reader()).asJsonObject

        val mainFile = config[MAIN_FILE]?.asString ?: error("Main file not found")
        val shouldBeUsage = config[SHOULD_BE_USAGE]?.asJsonArray?.map { it.asString }?.toSet().orEmpty()
        val ignoreK2Compiler = config[IGNORE_K2_COMPILER]?.asString

        val isTestEnabled = isCompatibleVersions && (!isK2 || ignoreK2Compiler == null)

        runCatching {
            val allFiles = listOf(mainFile) + Path(testDataFilePath).listDirectoryEntries().map { it.name }.minus(mainFile)
            myFixture.configureByFiles(*allFiles.toTypedArray())
            project.putUserData(CompileDriver.TIMEOUT, 100_000)
            try {
                rebuildProject()
            } finally {
                project.putUserData(CompileDriver.TIMEOUT, null)
            }

            val actualUsages = getReferentFilesForElementUnderCaret()
            assertEqualsToFile(
                "",
                pathToCheck.toFile(),
                createActualText(mainFile, actualUsages, shouldBeUsage, ignoreK2Compiler)
            )
        }.fold(
            onSuccess = { require(isTestEnabled) { "This test passes and shouldn't be muted!" } },
            onFailure = { exception -> if (isTestEnabled) throw exception },
        )

        if (firConfigurationPath?.readText()?.trim() == configurationPath.readText().trim()) {
            firConfigurationPath.deleteExisting()
            error("Fir file is redundant")
        }
    }

    companion object {
        private const val USAGES = "usages"
        private const val SHOULD_BE_USAGE = "shouldBeUsage"
        private const val MAIN_FILE = "mainFile"
        private const val IGNORE_K2_COMPILER = "ignoreK2Compiler"

        private fun createActualText(
            mainFile: String,
            actualUsages: Set<String>?,
            shouldBeUsage: Set<String>,
            ignoreK2Compiler: String?,
        ): String {
            val mainFileText = createPair(MAIN_FILE, "\"$mainFile\"")
            val actualUsagesText = actualUsages?.ifNotEmpty { createPair(USAGES, createArray(this)) }
            val shouldBeFixedText = shouldBeUsage.minus(actualUsages.orEmpty()).ifNotEmpty {
                createPair(SHOULD_BE_USAGE, createArray(this))
            }

            val ignoreK2CompilerText = ignoreK2Compiler?.let { createPair(IGNORE_K2_COMPILER, "\"$ignoreK2Compiler\"") }
            return listOfNotNull(mainFileText, actualUsagesText, shouldBeFixedText, ignoreK2CompilerText).joinToString(
                prefix = "{\n    ",
                separator = ",\n    ",
                postfix = "\n}"
            )
        }

        private fun createArray(values: Set<String>): String = values.sorted().joinToString(
            prefix = "[\n        \"",
            separator = "\",\n        \"",
            postfix = "\"\n    ]"
        )

        private fun createPair(name: String, value: String) = "\"$name\": $value"
    }
}
