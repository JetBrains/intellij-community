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

    @Ignore("Check and unmute when KTIJ-32507 is fixed")
    @Test
    @PluginTargetVersions(gradleVersion = "8.10+")
    fun testJvmOnJvmJavaAndKotlin() {
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

    @Ignore("Check and unmute when KTIJ-32507 is fixed")
    @Test
    @PluginTargetVersions(gradleVersion = "8.10+")
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
    fun testJvmIncludedBuild() {
        doTest {
            onlyCheckers(
                NoErrorEventsDuringImportFeature,
                HighlightingChecker
            )
        }
    }

    @Ignore("Unmute when the latest kotlin is updated to bootstrap version 2.2.0 and bootstrap version includes KT-74727")
    @Test
    @PluginTargetVersions(gradleVersion = "8.10+")
    fun testSimpleMultiTargetProject() {
        doTest {
            onlyCheckers(
                NoErrorEventsDuringImportFeature,
                HighlightingChecker
            )
        }
    }
}
