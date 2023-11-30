// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.idea.importing.multiplatformTests

import org.jetbrains.kotlin.gradle.multiplatformTests.AbstractKotlinMppGradleImportingTest
import org.jetbrains.kotlin.gradle.multiplatformTests.TestConfigurationDslScope
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.highlighting.HighlightingChecker
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.orderEntries.OrderEntriesChecker
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.plugins.gradle.tooling.annotation.PluginTargetVersions
import org.junit.Test

@TestMetadata("multiplatform/core/features/compositeBuild")
class KotlinMppCompositeBuildImportingTest : AbstractKotlinMppGradleImportingTest() {

    override fun TestConfigurationDslScope.defaultTestConfiguration() {
        hideResourceRoots = true
        hideStdlib = true
        hideKotlinTest = true
        hideKotlinNativeDistribution = true
    }

    @Test
    @PluginTargetVersions(pluginVersion = "1.9.20-dev-6845+")
    fun testJvmAndNative() {
        doTest {
            onlyCheckers(OrderEntriesChecker)
            linkProject("consumerBuild")

            // only interested in dependencies from consumerBuild to producerBuild
            onlyDependencies(from = ".*consumerBuild.*", to = ".*producerBuild.*")
        }
    }

    @Test
    @PluginTargetVersions(pluginVersion = "1.8.20-dev-3309+", gradleVersion = "7.5.1+") // Indirectly limit to AGP 7.4.1+, see: KTIJ-25679
    fun testJvmAndAndroid() {
        doTest {
            onlyCheckers(OrderEntriesChecker)
            linkProject("consumerBuild")

            // only interested in dependencies from consumerBuild to producerBuild
            onlyDependencies(from = ".*consumerBuild.*", to = ".*producerBuild.*")
        }
    }

    @Test
    @PluginTargetVersions(pluginVersion = "1.9.0-dev-1778+")
    fun testDependenciesInMppCompositeBuild() {
        doTest {
            onlyCheckers(OrderEntriesChecker)
            onlyModules(""".*includedBuild.consumer.*""")
        }
    }

    @Test
    @PluginTargetVersions(pluginVersion = "1.9.20-dev-6845+")
    fun testLibraryWithRootProjectName() {
        doTest {
            onlyCheckers(OrderEntriesChecker, HighlightingChecker)
            // only interested in dependencies to included library
            excludeDependencies(".*includedApp.*")
        }
    }
}
