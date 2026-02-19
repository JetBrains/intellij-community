// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.testFramework

import java.nio.file.Path

/**
 * Provides implementation of getting some kotlin artifacts when running from sources and under testing.
 * The implementation is in test classes, that's why it's separated with Jvm service
 */
internal interface TestKotlinArtifactsProvider {
    fun getJpsPluginClasspath(): List<Path>

    /**
     * Returns a directory which will be under KotlinArtifactConstants.KOTLIN_DIST_LOCATION_PREFIX
     * to satisfy FromKotlinDistForIdeByNameFallbackBundledFirCompilerPluginProvider
     */
    fun getKotlincCompilerCli(): Path
}