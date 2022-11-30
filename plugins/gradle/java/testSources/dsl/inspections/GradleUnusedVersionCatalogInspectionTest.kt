// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.dsl.inspections

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.codeInspection.toml.UnusedVersionCatalogEntryInspection
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.jetbrains.plugins.gradle.testFramework.util.withSettingsFile
import org.junit.jupiter.params.ParameterizedTest

class GradleUnusedVersionCatalogInspectionTest : GradleCodeInsightTestCase() {

  private fun runTest(gradleVersion: GradleVersion, buildGradleText: String, versionCatalogText: String) {
    test(gradleVersion, BASE_VERSION_CATALOG_FIXTURE) {
      codeInsightFixture.enableInspections(UnusedVersionCatalogEntryInspection::class.java)
      findOrCreateFile("build.gradle", buildGradleText)
      testHighlighting("gradle/libs.versions.toml", versionCatalogText)
    }
  }


  @ParameterizedTest
  @BaseGradleVersionSource
  fun testNoUsageOfLibrary(gradleVersion: GradleVersion) {
    runTest(gradleVersion, "", """
        [libraries]
        <warning>groovy-core</warning> = "aa:bb:2.0.0"      
    """.trimIndent())
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testNoUsageOfPlugin(gradleVersion: GradleVersion) {
    runTest(gradleVersion, "", """
        [plugins]
        <warning>groovy-core</warning> = "aa:bb:2.0.0"      
    """.trimIndent())
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testUsageOfLibrary(gradleVersion: GradleVersion) {
    runTest(gradleVersion, "libs.groovy.core", """
        [libraries]
        groovy-core = "aa:bb:2.0.0"      
    """.trimIndent())
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testUsageOfPlugin(gradleVersion: GradleVersion) {
    runTest(gradleVersion, "libs.plugins.groovy.core", """
        [plugins]
        groovy-core = "10.0.0"      
    """.trimIndent())
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testNoUsageOfVersion(gradleVersion: GradleVersion) {
    runTest(gradleVersion, "", """
        [versions]
        <warning>groovy-core</warning> = "10.0.0"      
    """.trimIndent())
  }


  @ParameterizedTest
  @BaseGradleVersionSource
  fun testUsageOfVersionInBuildscript(gradleVersion: GradleVersion) {
    runTest(gradleVersion, "libs.versions.groovy.core", """
        [versions]
        groovy-core = "10.0.0"      
    """.trimIndent())
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testUsageOfVersionInToml(gradleVersion: GradleVersion) {
    runTest(gradleVersion, "", """
        [versions]
        groovy-core = "10.0.0" 
        [libraries]
        <warning>aaa-bbb</warning> = { group = "org.apache.groovy", name = "groovy", version.ref = "groovy-core" }
    """.trimIndent())
  }
}

private val BASE_VERSION_CATALOG_FIXTURE = GradleTestFixtureBuilder
  .create("GradleVersionCatalogs-inspection") {
    withSettingsFile {
      setProjectName("GradleVersionCatalogs-inspection")
      enableFeaturePreview("VERSION_CATALOGS")
    }
  }