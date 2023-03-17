// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.idea.importing.newTests

import org.jetbrains.kotlin.config.KotlinFacetSettings
import org.jetbrains.kotlin.gradle.newTests.AbstractKotlinMppGradleImportingTest
import org.jetbrains.kotlin.gradle.newTests.TestConfigurationDslScope
import org.jetbrains.kotlin.gradle.newTests.testFeatures.checkers.facets.KotlinFacetSettingsChecker
import org.jetbrains.kotlin.test.TestMetadata
import org.junit.Test

@TestMetadata("newMppTests/features/precisePlatformsImporting")
class PrecisePlatformsImportingTests : AbstractKotlinMppGradleImportingTest() {
    override fun TestConfigurationDslScope.defaultTestConfiguration() {
        onlyCheckers(KotlinFacetSettingsChecker)
        onlyFacetFields(KotlinFacetSettings::targetPlatform)
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
