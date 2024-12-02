// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.idea.importing.multiplatformTests

import org.jetbrains.kotlin.gradle.multiplatformTests.AbstractKotlinMppGradleImportingTest
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.NoErrorEventsDuringImportFeature
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.highlighting.HighlightingChecker
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.plugins.gradle.tooling.annotation.PluginTargetVersions
import org.junit.Test

@TestMetadata("multiplatform/core/features/projectIsolation")
class KotlinMppProjectIsolationTest : AbstractKotlinMppGradleImportingTest() {
    @Test
    fun testJvmOnly() {
        doTest {
            onlyCheckers(
                NoErrorEventsDuringImportFeature,
                HighlightingChecker
            )
        }
    }

    @Test
    @PluginTargetVersions(pluginVersion = "2.1.20-dev-10000+")
    fun testSimpleMultiTargetProject() {
        doTest {
            onlyCheckers(
                NoErrorEventsDuringImportFeature,
                HighlightingChecker
            )
        }
    }

}
