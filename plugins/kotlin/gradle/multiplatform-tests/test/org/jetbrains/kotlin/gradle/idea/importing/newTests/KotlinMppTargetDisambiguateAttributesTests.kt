// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.idea.importing.newTests

import org.jetbrains.kotlin.gradle.newTests.AbstractKotlinMppGradleImportingTest
import org.jetbrains.kotlin.gradle.newTests.TestConfigurationDslScope
import org.jetbrains.kotlin.gradle.newTests.testFeatures.checkers.orderEntries.OrderEntriesChecker
import org.jetbrains.kotlin.test.TestMetadata
import org.junit.Test

@TestMetadata("newMppTests/features/attributesDisambiguate")
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
    fun testJsSimilarTargets() {
        doTest()
    }

    @Test
    fun testIosSimilarTargets() {
        doTest()
    }
}