// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.newTests.testFeatures.checkers.highlighting

import org.jetbrains.kotlin.gradle.newTests.AbstractTestChecker
import org.jetbrains.kotlin.gradle.newTests.KotlinMppTestsContext
import org.jetbrains.kotlin.idea.codeInsight.gradle.HighlightingCheck
import java.io.File

object HighlightingChecker : AbstractTestChecker<HighlightingCheckConfiguration>() {
    override fun createDefaultConfiguration() = HighlightingCheckConfiguration()

    override fun preprocessFile(origin: File, text: String): String? {
        if (origin.extension != "kt") return null
        if (HIGHLIGHTING_CONFIGURATION_HEADER !in text) return null

        // Make sure that the highlighting configuration is exactly first lines and not
        // moved elsewhere by accident
        val lines = text.lines()
        require(lines.first().trim() == HIGHLIGHTING_CONFIGURATION_HEADER) {
            "Error: Highlighting configuration should be exactly first lines in file\n" +
                    "If you've edited the file, please put the source-comment with configuration\n" +
                    "on top of the file, or remove it completely and let the tests infrastructure\n" +
                    "regenerate it for you"
        }

        return lines.drop(1) // header
            .filter { !it.startsWith(HIGHLIGHTING_CONFIGURATION_LINE_PREFIX) && it != HIGHLIGHTING_CONFIGURATION_FOOTER }
            .joinToString(separator = System.lineSeparator())
    }

    override fun KotlinMppTestsContext.check(additionalTestClassifier: String?) {
        val highlightingConfig = testConfiguration.getConfiguration(HighlightingChecker)
        if (highlightingConfig.skipCodeHighlighting) return
        val renderedConfig = renderConfiguration()
        HighlightingCheck(
            project = testProject,
            projectPath = testProjectRoot.path,
            testDataDirectory = testDataDirectory,
            testLineMarkers = !highlightingConfig.hideLineMarkers,
            severityLevel = highlightingConfig.hideHighlightsBelow,
            correspondingFilePostfix = additionalTestClassifier ?: "",
            postprocessActualTestData = { actualTestData -> renderedConfig + "\n" + actualTestData }
        ).invokeOnAllModules()
    }

    private fun KotlinMppTestsContext.renderConfiguration(): String {
        val configuration = testConfiguration.getConfiguration(this@HighlightingChecker)

        val hiddenHighlightingEntries: List<String> = buildList {
            if (configuration.skipCodeHighlighting) add("code highlighting")
            if (configuration.hideLineMarkers) add("line markers")
        }

        return if (hiddenHighlightingEntries.isNotEmpty())
            """
                $HIGHLIGHTING_CONFIGURATION_HEADER
                ${HIGHLIGHTING_CONFIGURATION_LINE_PREFIX}hidden: ${hiddenHighlightingEntries.joinToString()}
                $HIGHLIGHTING_CONFIGURATION_FOOTER
            """.trimIndent()
        else
            ""
    }

    private const val HIGHLIGHTING_CONFIGURATION_HEADER = "//region Test configuration"
    private const val HIGHLIGHTING_CONFIGURATION_LINE_PREFIX = "// - "
    private const val HIGHLIGHTING_CONFIGURATION_FOOTER = "//endregion"
}
