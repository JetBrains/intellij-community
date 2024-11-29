// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeInsight.gradle

import com.intellij.testFramework.TestDataPath
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.AbstractGradleCodeInsightTest
import org.jetbrains.kotlin.gradle.AbstractKotlinGradleNavigationTest.Companion.GRADLE_COMPOSITE_BUILD_FIXTURE
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.idea.test.AssertKotlinPluginMode
import org.jetbrains.kotlin.idea.test.UseK2PluginMode
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.jetbrains.plugins.gradle.testFramework.fixtures.application.GradleProjectTestApplication
import org.junit.jupiter.params.ParameterizedTest

/**
 * @see org.jetbrains.kotlin.idea.gradle.versionCatalog.toml.KotlinGradleTomlVersionCatalogReferencesSearcher
 */
@UseK2PluginMode
@GradleProjectTestApplication
@AssertKotlinPluginMode
@TestRoot("idea/tests/testData/")
@TestDataPath("\$CONTENT_ROOT")
@TestMetadata("../../../idea/tests/testData/gradle/findUsages/")
class KotlinGradleCompositeBuildFindUsagesTest : AbstractGradleCodeInsightTest() {
    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("propertyFromCustomPluginUsageInBuildGradleKts.test")
    fun testPropertyFromCustomPluginUsageInBuildGradleKts(gradleVersion: GradleVersion) {
        verifyFindUsages(gradleVersion, GRADLE_COMPOSITE_BUILD_FIXTURE)
    }
}
