// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.gradle.idea.importing.multiplatformTests.k2

import com.intellij.lang.annotation.HighlightSeverity
import org.jetbrains.kotlin.gradle.multiplatformTests.AbstractKotlinMppGradleImportingTest
import org.jetbrains.kotlin.gradle.multiplatformTests.KotlinIdePluginKind
import org.jetbrains.kotlin.gradle.multiplatformTests.TestConfigurationDslScope
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.highlighting.HighlightingChecker
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.plugins.gradle.tooling.annotation.PluginTargetVersions
import org.junit.Test

@TestMetadata("multiplatform/k2/highlighting")
class K2MppHighlightingIntegrationTest : AbstractKotlinMppGradleImportingTest(KotlinIdePluginKind.K2) {
    override val allowOnNonMac: Boolean
        get() = false

    override fun TestConfigurationDslScope.defaultTestConfiguration() {
        hideHighlightsBelow = HighlightSeverity.ERROR
        onlyCheckers(HighlightingChecker)
        hideLineMarkers = true
    }

    @Test
    @PluginTargetVersions(pluginVersion = "1.9.30-dev-0+")
    fun testMppStdlibAndCoroutinesHighlighting() {
        doTest()
    }

    // Adopted from the K1 tier 1 tests
    @Test
    @PluginTargetVersions(pluginVersion = "1.9.30-dev-0+")
    fun testTwoKmmLibrariesSource() {
        doTest()
    }
}
