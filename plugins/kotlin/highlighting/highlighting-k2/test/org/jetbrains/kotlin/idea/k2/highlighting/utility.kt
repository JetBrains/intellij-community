// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.highlighting

import com.intellij.openapi.application.PathMacros
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifactConstants
import java.io.File

/**
 * The main use case is testing Bundled Compiler Plugins.
 *
 * Since we do not use Junit 5, an alternative approach to test fixtures would be
 * logic encapsulation.
 */
internal object K2TestMetaDataWithBundledPluginsDefaultDirsMacrosHelper {
    /**
     * Test cases reference fake compiler plugins' jars which lay in the test data directory. This directory is located differently
     * in local and CI (TeamCity) environments.
     * To overcome this, we use this path macro in test cases, and it is expected to be correctly substituted
     * by [org.jetbrains.kotlin.idea.fir.extensions.KtCompilerPluginsProviderIdeImpl].
     */
    private const val TEST_DIR_PLACEHOLDER: String = "TEST_DIR"

    /**
     * We want to test the scenario for the non-yet-downloaded jars from 'kotlin-dist-for-ide' location.
     *
     * See KTIJ-32221 and [org.jetbrains.kotlin.idea.fir.extensions.FromKotlinDistForIdeByNameFallbackBundledFirCompilerPluginProvider].
     */
    private const val TEST_KOTLIN_DIST_FOR_IDE_PLACEHOLDER: String = "TEST_KOTLIN_DIST_FOR_IDE"

    fun setUpMacros(testDataDirectory: File) {
        PathMacros.getInstance().apply {
            setMacro(TEST_DIR_PLACEHOLDER, testDataDirectory.toString())
            setMacro(TEST_KOTLIN_DIST_FOR_IDE_PLACEHOLDER,
                     KotlinArtifactConstants.KOTLIN_DIST_LOCATION_PREFIX.toString())
        }
    }

    fun tearDownMacros() {
        PathMacros.getInstance().apply {
            setMacro(TEST_DIR_PLACEHOLDER, null)
            setMacro(TEST_KOTLIN_DIST_FOR_IDE_PLACEHOLDER, null)
        }
    }
}
