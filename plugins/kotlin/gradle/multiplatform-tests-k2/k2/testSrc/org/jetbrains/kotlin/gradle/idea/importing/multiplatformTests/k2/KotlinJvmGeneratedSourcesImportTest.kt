// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.idea.importing.multiplatformTests.k2

import org.jetbrains.kotlin.gradle.multiplatformTests.AbstractKotlinMppGradleImportingTest
import org.jetbrains.kotlin.gradle.multiplatformTests.TestConfigurationDslScope
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.contentRoots.ContentRootsChecker
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.plugins.gradle.tooling.annotation.PluginTargetVersions
import org.junit.Test

@TestMetadata("kotlinGeneratedSourcesImportTest")
class KotlinJvmGeneratedSourcesImportTest : AbstractKotlinMppGradleImportingTest() {

    override val allowOnNonMac: Boolean = true

    override fun TestConfigurationDslScope.defaultTestConfiguration() {
        onlyCheckers(ContentRootsChecker)
    }

    @Test
    @PluginTargetVersions(pluginVersion = "2.3.0+", gradleVersion = "7.6+")
    fun testGeneratedInMainSourceSet() {
        doTest()
    }

    @Test
    @PluginTargetVersions(pluginVersion = "2.3.0+", gradleVersion = "7.6+")
    fun testGeneratedInTestSourceSet() {
        doTest()
    }

    @Test
    @PluginTargetVersions(pluginVersion = "2.3.0+", gradleVersion = "7.6+")
    fun testGeneratedWithIdeaPlugin() {
        doTest()
    }
}
