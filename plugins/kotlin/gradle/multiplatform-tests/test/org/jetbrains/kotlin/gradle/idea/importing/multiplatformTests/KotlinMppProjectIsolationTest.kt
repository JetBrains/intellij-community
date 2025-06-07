// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.idea.importing.multiplatformTests

import org.jetbrains.kotlin.gradle.multiplatformTests.AbstractKotlinMppGradleImportingTest
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.NoErrorEventsDuringImportFeature
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.highlighting.HighlightingChecker
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.plugins.gradle.tooling.annotation.PluginTargetVersions
import org.junit.Ignore
import org.junit.Test

@TestMetadata("multiplatform/core/features/projectIsolation")
class KotlinMppProjectIsolationTest : AbstractKotlinMppGradleImportingTest() {
    @Test
    @PluginTargetVersions(pluginVersion = "2.1.20-dev-3305+", gradleVersion = "8.10+")
    fun testJvmOnly() {
        doTest {
            onlyCheckers(
                NoErrorEventsDuringImportFeature,
                HighlightingChecker
            )
        }
    }

    @Test
    @PluginTargetVersions(pluginVersion = "2.1.20-dev-3305+", gradleVersion = "8.10+")
    fun testJvmDependsOnJvm() {
        doTest {
            onlyCheckers(
                NoErrorEventsDuringImportFeature,
                HighlightingChecker
            )
        }
    }

    @Test
    @PluginTargetVersions(pluginVersion = "2.1.20-dev-3305+", gradleVersion = "8.10+")
    fun testJvmAndKapt() {
        doTest {
            onlyCheckers(
                NoErrorEventsDuringImportFeature,
                HighlightingChecker
            )
        }
    }

    @Test
    @PluginTargetVersions(pluginVersion = "2.1.20-dev-3305+", gradleVersion = "8.10+")
    fun testJvmSharedResources() {
        doTest {
            onlyCheckers(
                NoErrorEventsDuringImportFeature,
                HighlightingChecker
            )
        }
    }

    @Test
    @PluginTargetVersions(pluginVersion = "2.1.20-dev-3305+", gradleVersion = "8.10+")
    fun testJvmIncludeBuild() {
        doTest {
            onlyCheckers(
                NoErrorEventsDuringImportFeature,
                HighlightingChecker
            )
        }
    }

    @Test
    @PluginTargetVersions(pluginVersion = "2.2.0-dev-231+", gradleVersion = "8.10+")
    fun testSimpleMultiTargetProject() {
        doTest {
            onlyCheckers(
                NoErrorEventsDuringImportFeature,
                HighlightingChecker
            )
        }
    }


    @Test
    @PluginTargetVersions(pluginVersion = "2.2.0-dev-231+", gradleVersion = "8.10+")
    fun testMultiTargetIndependentProject() {
        doTest {
            onlyCheckers(
                NoErrorEventsDuringImportFeature,
                HighlightingChecker
            )
        }
    }

    @Test
    @PluginTargetVersions(pluginVersion = "2.2.0-dev-231+", gradleVersion = "8.10+")
    fun testKmpSharedResourcesAndroidIOS() {
        doTest {
            onlyCheckers(
                NoErrorEventsDuringImportFeature,
                HighlightingChecker
            )
        }
    }

    @Test
    @PluginTargetVersions(pluginVersion = "2.2.0-dev-745+", gradleVersion = "8.10+")
    fun testJvmMultiplatformTransitiveDependency() {
        doTest {
            onlyCheckers(
                NoErrorEventsDuringImportFeature,
                HighlightingChecker
            )
        }
    }

    @Test
    @PluginTargetVersions(pluginVersion = "2.2.0-dev-231+", gradleVersion = "8.10+")
    fun testKmpWithCinteropLib() {
        doTest {
            onlyCheckers(
                NoErrorEventsDuringImportFeature,
                HighlightingChecker
            )
        }
    }

    @Ignore("Check and unmute when KTIJ-33118 is fixed")
    @Test
    @PluginTargetVersions(pluginVersion = "2.2.0-dev-231+", gradleVersion = "8.10+")
    fun testKmpIncludeBuild() {
        doTest {
            onlyCheckers(
                NoErrorEventsDuringImportFeature,
                HighlightingChecker
            )
        }
    }

    @Ignore("Check and unmute when IDEA-368207 is fixed")
    @Test
    @PluginTargetVersions(pluginVersion = "2.2.0-dev-745+", gradleVersion = "8.10+")
    fun testKmpDependsOnAndroidKMPLibrary() {
        doTest {
            onlyCheckers(
                NoErrorEventsDuringImportFeature,
                HighlightingChecker
            )
        }
    }

    @Test
    @PluginTargetVersions(pluginVersion = "2.2.0-dev-745+", gradleVersion = "8.11+")
    fun testKmpShareConfigurationViaBuildSrc() {
        doTest {
            onlyCheckers(
                NoErrorEventsDuringImportFeature,
                HighlightingChecker
            )
        }
    }

    @Test
    @PluginTargetVersions(pluginVersion = "2.2.0-dev-745+", gradleVersion = "8.10+")
    fun testKmpShareConfigurationViaIncludeBuild() {
        doTest {
            onlyCheckers(
                NoErrorEventsDuringImportFeature,
                HighlightingChecker
            )
        }
    }

    @Test
    @PluginTargetVersions(pluginVersion = "2.2.0-dev-745+", gradleVersion = "8.10+")
    fun testKmpTestDependenciesAcrossModules() {
        doTest {
            onlyCheckers(
                NoErrorEventsDuringImportFeature,
                HighlightingChecker
            )
        }
    }
}
