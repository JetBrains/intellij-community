// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.idea.importing.multiplatformTests.k2

import org.jetbrains.kotlin.gradle.multiplatformTests.AbstractKotlinMppGradleImportingTest
import org.jetbrains.kotlin.gradle.multiplatformTests.TestConfigurationDslScope
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.orderEntries.OrderEntriesChecker
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.plugins.gradle.tooling.annotation.PluginTargetVersions
import org.junit.Test

@TestMetadata("multiplatform/core/features/attributesDisambiguate")
class KotlinMppTargetDisambiguateAttributesTests : AbstractKotlinMppGradleImportingTest() {
    override fun TestConfigurationDslScope.defaultTestConfiguration() {
        hideStdlib = true
        hideKotlinTest = true
        hideKotlinNativeDistribution = true

        onlyCheckers(OrderEntriesChecker)
        onlyDependencies(from = ".*app.*", to = ".*lib.*")

        hideResourceRoots = true
    }

    @Test
    @PluginTargetVersions(
        /**
         * - applyHierarchyTemplate became available in 1.9.20
         * - since 2.1.0 we don't support having multiple jvm targets
         */
        pluginVersion = "1.9.20-dev-6845 <=> 2.0.99"
    )
    fun testJvmSimilarTargets() {
        doTest()
    }

    @Test
    fun testAndroidAndJvmAttributesOverwrite() {
        doTest()
    }

    @Test
    fun testAndroidAndJvmAttributesIgnoreAttribute() {
        doTest()
    }

    @Test
    @PluginTargetVersions(pluginVersion = "1.9.20-dev-6845+") // applyHierarchyTemplate used
    fun testJsSimilarTargets() {
        doTest()
    }

    @Test
    @PluginTargetVersions(pluginVersion = "1.9.20-dev-6845+") // applyHierarchyTemplate used
    fun testIosSimilarTargets() {
        doTest()
    }
}
