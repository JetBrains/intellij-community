// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.idea.importing.multiplatformTests.k2

import org.jetbrains.kotlin.gradle.multiplatformTests.AbstractKotlinMppGradleImportingTest
import org.jetbrains.kotlin.gradle.multiplatformTests.TestConfigurationDslScope
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.facets.KotlinFacetSettingsChecker
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.plugins.gradle.tooling.annotation.PluginTargetVersions
import org.junit.Test

@TestMetadata("mppConventionPluginsImportingTest")
class KotlinMppConventionPluginImportingTest : AbstractKotlinMppGradleImportingTest() {

    override fun TestConfigurationDslScope.defaultTestConfiguration() {
        onlyCheckers(KotlinFacetSettingsChecker)
    }

    @Test
    @PluginTargetVersions(pluginVersion = "1.5.31+", gradleVersion = "6.8+")
    fun testKotlinMPPFacetInModuleFromBuildSrcConventionPlugin() {
        doTest()
    }

    @Test
    @PluginTargetVersions(pluginVersion = "1.5.31+", gradleVersion = "6.8+")
    fun testKotlinMPPFacetInModuleFromIncludeBuildConventionPlugin() {
        doTest()
    }

    @Test
    @PluginTargetVersions(pluginVersion = "1.5.31+", gradleVersion = "6.8+")
    fun testKotlinMPPFacetInIncludeBuildFromIncludeBuildConventionPlugin() {
        doTest()
    }
}
