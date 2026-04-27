// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.idea.importing.multiplatformTests.k2

import org.jetbrains.kotlin.gradle.multiplatformTests.AbstractKotlinMppGradleImportingTest
import org.jetbrains.kotlin.gradle.multiplatformTests.TestConfigurationDslScope
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.facets.KotlinFacetSettingsChecker
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.plugins.gradle.tooling.annotation.PluginTargetVersions
import org.junit.Test

@TestMetadata("jvmConventionPluginsImportingTest")
class KotlinJvmConventionPluginImportingTest : AbstractKotlinMppGradleImportingTest() {

    override val allowOnNonMac: Boolean = true

    override fun TestConfigurationDslScope.defaultTestConfiguration() {
        onlyCheckers(KotlinFacetSettingsChecker)
    }

    @Test
    @PluginTargetVersions(pluginVersion = "2.1.0+", gradleVersion = "6.8+")
    fun testKotlinFacetInModuleFromBuildSrcConventionPlugin() {
        doTest()
    }

    @Test
    @PluginTargetVersions(pluginVersion = "2.1.0+", gradleVersion = "6.8+")
    fun testKotlinFacetInBuildSrcFromIncludeBuildConventionPlugin() {
        doTest()
    }

    @Test
    @PluginTargetVersions(pluginVersion = "2.1.0+", gradleVersion = "6.8+")
    fun testKotlinFacetInModuleFromIncludeBuildConventionPlugin() {
        doTest()
    }

    @Test
    @PluginTargetVersions(pluginVersion = "2.1.0+", gradleVersion = "6.8+")
    fun testKotlinFacetInIncludeBuildFromIncludeBuildConventionPlugin() {
        doTest()
    }
}
