// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.idea.importing.multiplatformTests.k2

import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
import org.jetbrains.kotlin.config.IKotlinFacetSettings
import org.jetbrains.kotlin.gradle.multiplatformTests.AbstractKotlinMppGradleImportingTest
import org.jetbrains.kotlin.gradle.multiplatformTests.TestConfigurationDslScope
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.facets.KotlinFacetSettingsChecker
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.plugins.gradle.tooling.annotation.PluginTargetVersions
import org.junit.Test


@TestMetadata("compilerPlugins")
class CompilerPluginTest : AbstractKotlinMppGradleImportingTest() {
    override fun TestConfigurationDslScope.defaultTestConfiguration() {
        onlyCheckers(KotlinFacetSettingsChecker)
        onlyFacetFields(IKotlinFacetSettings::compilerArguments)
        onlyCompilerArguments(CommonCompilerArguments::pluginClasspaths)
    }

    @Test
    @PluginTargetVersions(pluginVersion = "2.0.0+")
    fun testCompilerPluginsJvm() {
        doTest()
    }

    @Test
    @PluginTargetVersions(pluginVersion = "2.0.0+")
    fun testCompilerPluginsKmp() {
        doTest()
    }
}
