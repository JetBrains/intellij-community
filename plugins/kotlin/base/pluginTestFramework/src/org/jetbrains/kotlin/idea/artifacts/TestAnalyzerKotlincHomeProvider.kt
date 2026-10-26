// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.artifacts

import org.jetbrains.kotlin.ide.kotlinc.testFramework.AnalyzerKotlincTestHomeProvider
import java.nio.file.Path

internal class TestAnalyzerKotlincHomeProvider : AnalyzerKotlincTestHomeProvider {
    override fun kotlincHome(): Path = TestKotlinArtifacts.kotlinDistForIdeAnalyzerUnpacked
}
