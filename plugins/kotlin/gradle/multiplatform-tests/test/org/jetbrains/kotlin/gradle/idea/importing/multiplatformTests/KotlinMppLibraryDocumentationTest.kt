// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.idea.importing.multiplatformTests

import org.jetbrains.kotlin.gradle.multiplatformTests.AbstractKotlinMppGradleImportingTest
import org.jetbrains.kotlin.gradle.multiplatformTests.TestConfigurationDslScope
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.DocumentationChecker
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.plugins.gradle.tooling.annotation.PluginTargetVersions
import kotlin.test.Test


@TestMetadata("multiplatform/core/features/documentation")
class KotlinMppLibraryDocumentationTest : AbstractKotlinMppGradleImportingTest() {
    override fun TestConfigurationDslScope.defaultTestConfiguration() {
        downloadSources = true
        skipHighlighting = true
        onlyCheckers(DocumentationChecker)
    }

    @Test
    @PluginTargetVersions(pluginVersion = "1.8.22+")
    fun testExpectDocumentationInPublishedMppLibrary() {
        doTest {
            publish("lib")
        }
    }
}