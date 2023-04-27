// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.newTests.testFeatures.checkers.highlighting

import com.intellij.lang.annotation.HighlightSeverity
import org.jetbrains.kotlin.gradle.newTests.TestConfigurationDslScope
import org.jetbrains.kotlin.gradle.newTests.writeAccess

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
