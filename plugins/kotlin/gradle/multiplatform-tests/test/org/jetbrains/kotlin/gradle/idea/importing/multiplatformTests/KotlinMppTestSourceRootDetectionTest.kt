// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.gradle.idea.importing.multiplatformTests

import org.jetbrains.kotlin.gradle.multiplatformTests.AbstractKotlinMppGradleImportingTest
import org.jetbrains.kotlin.gradle.multiplatformTests.TestConfigurationDslScope
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.contentRoots.ContentRootsChecker
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.plugins.gradle.tooling.annotation.PluginTargetVersions
import org.junit.Test

@TestMetadata("multiplatform/core/features/sourceSetTypeClassification")
class KotlinMppTestSourceRootDetectionTest : AbstractKotlinMppGradleImportingTest() {
    override fun TestConfigurationDslScope.defaultTestConfiguration() {
        onlyCheckers(
            ContentRootsChecker,
        )
        hideResourceRoots = true
    }

    @Test
    fun testIosAndroidLayoutV1() {
        doTest()
    }

    @Test
    @PluginTargetVersions(pluginVersion = "1.8.0+")
    fun testIosAndroidLayoutV2() {
        doTest()
    }

    @Test
    fun testJvmNativeWithAdditionalCompilations() {
        doTest()
    }
}
