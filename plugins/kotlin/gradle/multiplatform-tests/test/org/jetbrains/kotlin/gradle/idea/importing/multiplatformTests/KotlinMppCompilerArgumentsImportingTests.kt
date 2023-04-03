// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.idea.importing.multiplatformTests

import org.jetbrains.kotlin.config.KotlinFacetSettings
import org.jetbrains.kotlin.gradle.multiplatformTests.AbstractKotlinMppGradleImportingTest
import org.jetbrains.kotlin.gradle.multiplatformTests.TestConfigurationDslScope
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.facets.KotlinFacetSettingsChecker
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.plugins.gradle.tooling.annotation.PluginTargetVersions
import org.junit.Test

@TestMetadata("multiplatform/features/compilerArgsImporting")
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

    @Test
    fun testCompilerOptionsInCompilationTask() {
        doTest()
    }

    @Test
    fun testCompilerOptionsInTargetCompilations() {
        doTest()
    }

    @Test
    fun testFreeCompilerArgsInKotlinOptions() {
        doTest()
    }

    @Test
    fun testKotlinOptions() {
        doTest()
    }

    @Test
    fun testLanguageSettings() {
        doTest()
    }

    @Test
    fun testSingleTargetConfiguration() {
        doTest()
    }

    @Test
    fun testKotlinOptionsInAndroid() {
        doTest()
    }

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

    @Test
    fun testMixedCompilerOptionsKotlinDsl() {
        doTest()
    }

    @Test
    fun testMixedCompilerOptionsWithTasks() {
        doTest()
    }
}
