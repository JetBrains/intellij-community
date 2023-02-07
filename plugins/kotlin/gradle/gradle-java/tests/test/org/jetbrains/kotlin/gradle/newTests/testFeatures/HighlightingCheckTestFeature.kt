// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.newTests.testFeatures

import com.intellij.lang.annotation.HighlightSeverity
import org.jetbrains.kotlin.gradle.newTests.*
import org.jetbrains.kotlin.idea.codeInsight.gradle.HighlightingCheck
import java.io.File

internal object HighlightingChecker : AbstractTestChecker<HighlightingCheckConfiguration>() {
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

class HighlightingCheckConfiguration {
    // NB: for now, skipping highlighting disables line markers as well
    var skipCodeHighlighting: Boolean = false
    var hideLineMarkers: Boolean = false
    var hideHighlightsBelow: HighlightSeverity = HighlightSeverity.GENERIC_SERVER_ERROR_OR_WARNING
}

interface HighlightingCheckDsl {
    var TestConfigurationDslScope.skipHighlighting: Boolean
        get() = configuration.skipCodeHighlighting
        set(value) { configuration.skipCodeHighlighting = value }

    var TestConfigurationDslScope.hideLineMarkers: Boolean
        get() = configuration.hideLineMarkers
        set(value) { configuration.hideLineMarkers = value }

    var TestConfigurationDslScope.hideHighlightsBelow: HighlightSeverity
        get() = configuration.hideHighlightsBelow
        set(value) { configuration.hideHighlightsBelow = value }
}

private val TestConfigurationDslScope.configuration
    get() = writeAccess.getConfiguration(HighlightingChecker)
