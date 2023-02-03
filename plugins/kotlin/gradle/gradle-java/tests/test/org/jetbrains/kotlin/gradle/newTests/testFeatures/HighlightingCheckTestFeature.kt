// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.newTests.testFeatures

import com.intellij.lang.annotation.HighlightSeverity
import org.jetbrains.kotlin.gradle.newTests.*
import org.jetbrains.kotlin.idea.codeInsight.gradle.HighlightingCheck

internal object HighlightingCheckTestFeature : TestFeature<HighlightingCheckConfiguration> {
    override fun renderConfiguration(configuration: HighlightingCheckConfiguration): List<String> {
        val hiddenHighlightingEntries: List<String> = buildList {
            if (configuration.skipCodeHighlighting) add("code highlighting")
            if (configuration.hideLineMarkers) add("line markers")
        }
        return if (hiddenHighlightingEntries.isNotEmpty())
            listOf("hiding ${hiddenHighlightingEntries.joinToString()}")
        else
            emptyList()
    }

    override fun createDefaultConfiguration() = HighlightingCheckConfiguration()

    override fun KotlinMppTestsContext.afterImport() {
        val highlightingConfig = testConfiguration.getConfiguration(HighlightingCheckTestFeature)
        if (highlightingConfig.skipCodeHighlighting) return
        HighlightingCheck(
            project = testProject,
            projectPath = testProjectRoot.path,
            testDataDirectory = testDataDirectoryProvider.testDataDirectory(),
            testLineMarkers = !highlightingConfig.hideLineMarkers,
            severityLevel = highlightingConfig.hideHighlightsBelow,
            correspondingFilePostfix = ""
        ).invokeOnAllModules()
    }
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
    get() = writeAccess.getConfiguration(HighlightingCheckTestFeature)
