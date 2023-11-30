// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.idea.importing.multiplatformTests

import org.jetbrains.kotlin.config.IKotlinFacetSettings
import org.jetbrains.kotlin.gradle.multiplatformTests.AbstractKotlinMppGradleImportingTest
import org.jetbrains.kotlin.gradle.multiplatformTests.TestConfigurationDslScope
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.facets.KotlinFacetSettingsChecker
import org.jetbrains.kotlin.test.TestMetadata
import org.junit.Test

@TestMetadata("multiplatform/core/features/precisePlatformsImporting")
class PrecisePlatformsImportingTests : AbstractKotlinMppGradleImportingTest() {
    override fun TestConfigurationDslScope.defaultTestConfiguration() {
        onlyCheckers(KotlinFacetSettingsChecker)
        onlyFacetFields(IKotlinFacetSettings::targetPlatform)
    }

    @Test
    fun testCustomAddToCompilationPlusDependsOn() {
        doTest()
    }

    @Test
    fun testDefaultSourceSetDependsOnDefaultSourceSet() {
        doTest()
    }

    @Test
    fun testDefaultSourceSetIncludedIntoAnotherCompilationDirectly() {
        doTest()
    }


    @Test
    fun testPrecisePlatformsHmpp() {
        doTest()
    }

    @Test
    fun testPrecisePlatformsWithUnrelatedModuleHmpp() {
        doTest()
    }

    @Test
    fun testSourceSetIncludedIntoCompilationDirectly() {
        doTest()
    }

    @Test
    fun testSourceSetsWithDependsOnButNotIncludedIntoCompilation() {
        doTest()
    }
}
