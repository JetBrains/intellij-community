// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.idea.importing.multiplatformTests

import com.intellij.lang.annotation.HighlightSeverity
import org.jetbrains.kotlin.gradle.multiplatformTests.AbstractKotlinMppGradleImportingTest
import org.jetbrains.kotlin.gradle.multiplatformTests.TestConfigurationDslScope
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.highlighting.HighlightingChecker
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import org.jetbrains.plugins.gradle.tooling.annotation.PluginTargetVersions
import org.junit.Test

@TestMetadata("multiplatform/core/tier0")
class KotlinMppTierZeroCasesImportingTests : AbstractKotlinMppGradleImportingTest() {
    override fun TestConfigurationDslScope.defaultTestConfiguration() {
        hideHighlightsBelow = HighlightSeverity.ERROR
    }

    @Test
    @PluginTargetVersions(pluginVersion = "1.8.20+")
    fun testKmmApplication() {
        doTest()
    }

    @Test
    fun testKmmLibrary() {
        doTest {
            /* Code Highlighting requires 1.9, because of native opt-in annotation in source files */
            if (kotlinPluginVersion < KotlinToolingVersion("1.9.20-dev-6845")) {
                disableCheckers(HighlightingChecker)
            }
        }
    }
}
