// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.kotlin.tests.completion

import com.intellij.testFramework.TestDataPath
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.idea.test.AssertKotlinPluginMode
import org.jetbrains.kotlin.idea.test.UseK2PluginMode
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.jetbrains.plugins.gradle.testFramework.fixtures.application.GradleProjectTestApplication
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest

@UseK2PluginMode
@GradleProjectTestApplication
@AssertKotlinPluginMode
@TestDataPath($$"$CONTENT_ROOT/testData")
@TestRoot("completion/kotlin/tests/testData")
@TestMetadata("buildGradleKts/plugins")
internal class KotlinGradlePluginsCompletionTest : AbstractKotlinGradleCompletionTest() {

    @BeforeEach
    fun setup() = removeOtherCompletionContributors()

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("versionCatalogs/aliasArgumentEmptyInput")
    fun `test completion for an empty input`(gradleVersion: GradleVersion) =
        // TODO IDEA-384698 adjust sorting: catalog names should be on top
        verifyVersionCatalogCompletion(gradleVersion)

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("versionCatalogs/aliasArgumentCatalogNames")
    fun `test catalog name completion`(gradleVersion: GradleVersion) =
        // TODO IDEA-384698 adjust sorting: catalog names should be on top
        verifyVersionCatalogCompletion(gradleVersion)

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("versionCatalogs/aliasArgumentPlugins")
    fun `test plugin entry completion`(gradleVersion: GradleVersion) =
        verifyVersionCatalogCompletion(gradleVersion)

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("versionCatalogs/aliasArgumentPluginsFromCustomCatalog")
    fun `test plugin entry completion for a custom catalog`(gradleVersion: GradleVersion) =
        verifyVersionCatalogCompletion(gradleVersion)

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("versionCatalogs/aliasArgumentPluginsWhenSectionNotSpecified")
    fun `test plugin entry completion when plugins section is not specified`(gradleVersion: GradleVersion) =
        verifyVersionCatalogCompletion(gradleVersion)

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("versionCatalogs/aliasArgumentPluginsWhenCatalogNameNotSpecified")
    fun `test plugin entry completion from multiple catalogs when a catalog name is not specified`(gradleVersion: GradleVersion) =
        verifyVersionCatalogCompletion(gradleVersion)

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("versionCatalogs/librariesVersionsBundlesAreNotCompletedWhenSectionIsNotSpecified")
    fun `test libraries, versions and bundles are not completed when the section is not specified`(gradleVersion: GradleVersion) =
        verifyVersionCatalogCompletion(gradleVersion)

    @ParameterizedTest
    @BaseGradleVersionSource
    @TestMetadata("versionCatalogs/versionsAreCompletedWhenSectionIsSpecified")
    fun `test versions are completed when the section is specified`(gradleVersion: GradleVersion) =
        verifyVersionCatalogCompletion(gradleVersion)

    private fun verifyVersionCatalogCompletion(gradleVersion: GradleVersion) {
        verifyCompletion(gradleVersion, GRADLE_VERSION_CATALOGS_FIXTURE)
    }
}