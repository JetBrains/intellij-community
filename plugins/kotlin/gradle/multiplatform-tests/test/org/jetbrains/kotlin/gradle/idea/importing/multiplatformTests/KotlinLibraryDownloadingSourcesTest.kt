// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.idea.importing.multiplatformTests

import org.jetbrains.kotlin.gradle.multiplatformTests.AbstractKotlinMppGradleImportingTest
import org.jetbrains.kotlin.gradle.multiplatformTests.TestConfigurationDslScope
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.sources.LibrarySourcesChecker
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.plugins.gradle.tooling.annotation.PluginTargetVersions
import kotlin.test.Test


@TestMetadata("multiplatform/core/features/stdlibSourcesDownloading")
class KotlinLibraryDownloadingSourcesTest : AbstractKotlinMppGradleImportingTest() {
    override fun TestConfigurationDslScope.defaultTestConfiguration() {
        classifier = "downloaded-sources"
        onlyCheckers(LibrarySourcesChecker)
    }

    @Test
    @PluginTargetVersions(gradleVersion = "7.3+")
    fun testJvmOnly() {
        doTest {
        }
    }
}