// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.multiplatformTests

import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.contentRoots.ContentRootsChecker
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.plugins.gradle.tooling.annotation.PluginTargetVersions
import org.junit.Test

@TestMetadata("multiplatform/core/features/generatedSources")
class KotlinMppGeneratedSourcesImportTest : AbstractKotlinMppGradleImportingTest() {

    override fun TestConfigurationDslScope.defaultTestConfiguration() {
        onlyCheckers(ContentRootsChecker)
        hideResourceRoots = true
    }

    @Test
    @PluginTargetVersions(pluginVersion = "2.3.0+", gradleVersion = "7.6+")
    fun testGeneratedInCommonMain() {
        doTest()
    }

    @Test
    @PluginTargetVersions(pluginVersion = "2.3.0+", gradleVersion = "7.6+")
    fun testGeneratedInCommonTest() {
        doTest()
    }

    @Test
    @PluginTargetVersions(pluginVersion = "2.3.0+", gradleVersion = "7.6+")
    fun testGeneratedInJvmMain() {
        doTest()
    }

    @Test
    @PluginTargetVersions(pluginVersion = "2.3.0+", gradleVersion = "7.6+")
    fun testGeneratedInJvmTest() {
        doTest()
    }
}
