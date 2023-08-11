// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.idea.importing.multiplatformTests

import org.jetbrains.kotlin.config.KotlinFacetSettings
import org.jetbrains.kotlin.gradle.multiplatformTests.AbstractKotlinMppGradleImportingTest
import org.jetbrains.kotlin.gradle.multiplatformTests.TestConfigurationDslScope
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.facets.KotlinFacetSettingsChecker
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.plugins.gradle.tooling.annotation.PluginTargetVersions
import org.junit.Test

@TestMetadata("multiplatform/core/features/compilerArgsImporting")
class KotlinMppCompilerArgumentsImportingTests : AbstractKotlinMppGradleImportingTest() {

    override fun TestConfigurationDslScope.defaultTestConfiguration() {
        hideStdlib = true
        hideKotlinTest = true
        hideKotlinNativeDistribution = true

        onlyCheckers(KotlinFacetSettingsChecker)
        onlyFacetFields(
            KotlinFacetSettings::languageLevel,
            KotlinFacetSettings::apiLevel,
            KotlinFacetSettings::compilerSettings
        )
        hideLineMarkers = true
        hideResourceRoots = true
    }

    @PluginTargetVersions(pluginVersion = "1.9.20-dev-6845+")
    @Test
    fun testCompilerOptionsInCompilationTask() {
        doTest()
    }

    @PluginTargetVersions(pluginVersion = "1.9.20-dev-6845+")
    @Test
    fun testCompilerOptionsInTargetCompilations() {
        doTest()
    }

    @PluginTargetVersions(pluginVersion = "1.9.20-dev-6845+")
    @Test
    fun testFreeCompilerArgsInKotlinOptions() {
        doTest()
    }

    @PluginTargetVersions(pluginVersion = "1.9.20-dev-6845+")
    @Test
    fun testKotlinOptions() {
        doTest()
    }

    @PluginTargetVersions(pluginVersion = "1.9.20-dev-6845+")
    @Test
    fun testLanguageSettings() {
        doTest()
    }

    @PluginTargetVersions(pluginVersion = "1.9.20-dev-9107+")
    @Test
    fun testSingleTargetConfiguration() {
        doTest()
    }

    @PluginTargetVersions(pluginVersion = "1.8.20-Beta+")
    @Test
    fun testKotlinOptionsInAndroid() {
        doTest()
    }

    @PluginTargetVersions(pluginVersion = "1.8.20-Beta+")
    @Test
    fun testCompilerOptionsInCompilationTaskKJvm() {
        doTest {
            onlyFacetFields(
                KotlinFacetSettings::languageLevel,
                KotlinFacetSettings::apiLevel,
                KotlinFacetSettings::compilerSettings,
                KotlinFacetSettings::targetPlatform
            )
        }
    }

    @Test
    @PluginTargetVersions(pluginVersion = "1.9.0-dev-3619+")
    fun testCompilerOptionsProjectLevelKJvm() {
        doTest()
    }

    @PluginTargetVersions(pluginVersion = "1.9.20-dev-6845+")
    @Test
    fun testMixedCompilerOptionsKotlinDsl() {
        doTest()
    }

    @PluginTargetVersions(pluginVersion = "1.9.20-dev-6845+")
    @Test
    fun testMixedCompilerOptionsWithTasks() {
        doTest()
    }

    @PluginTargetVersions(pluginVersion = "1.9.20-dev-9107+")
    @Test
    fun testCompilerOptionsInProjectAndTarget() {
        doTest()
    }
}
