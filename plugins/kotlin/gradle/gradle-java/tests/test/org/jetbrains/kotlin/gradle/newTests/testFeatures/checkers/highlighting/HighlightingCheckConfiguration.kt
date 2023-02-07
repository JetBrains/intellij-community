// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.newTests.testFeatures.checkers.highlighting

import com.intellij.lang.annotation.HighlightSeverity

class HighlightingCheckConfiguration {
    // NB: for now, skipping highlighting disables line markers as well
    var skipCodeHighlighting: Boolean = false
    var hideLineMarkers: Boolean = false
    var hideHighlightsBelow: HighlightSeverity = HighlightSeverity.GENERIC_SERVER_ERROR_OR_WARNING
}
