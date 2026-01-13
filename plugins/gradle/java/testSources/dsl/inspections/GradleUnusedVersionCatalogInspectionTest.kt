// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.dsl.inspections

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.codeInspection.toml.UnusedVersionCatalogEntryInspection
import org.jetbrains.plugins.gradle.dsl.versionCatalogs.GradleVersionCatalogFixtures.BASE_VERSION_CATALOG_FIXTURE
import org.jetbrains.plugins.gradle.dsl.versionCatalogs.GradleVersionCatalogFixtures.VERSION_CATALOG_COMPOSITE_BUILD_FIXTURE
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.junit.jupiter.params.ParameterizedTest

class GradleUnusedVersionCatalogInspectionTest : GradleCodeInsightTestCase() {

  internal enum class ProjectType { SIMPLE, COMPOSITE }

  private fun runTest(gradleVersion: GradleVersion, projectType: String, buildGradleText: String, versionCatalogText: String) {
    val fixtureBuilder: GradleTestFixtureBuilder
    var buildScriptPath: String
    var tomlCatalogPath: String
    when (ProjectType.valueOf(projectType)) {
      ProjectType.SIMPLE -> {
        fixtureBuilder = BASE_VERSION_CATALOG_FIXTURE
        buildScriptPath = "build.gradle"
        tomlCatalogPath = "gradle/libs.versions.toml"
      }

      ProjectType.COMPOSITE -> {
        fixtureBuilder = VERSION_CATALOG_COMPOSITE_BUILD_FIXTURE
        buildScriptPath = "includedBuild1/build.gradle"
        tomlCatalogPath = "includedBuild1/gradle/libs.versions.toml"
      }
    }

    test(gradleVersion, fixtureBuilder) {
      codeInsightFixture.enableInspections(UnusedVersionCatalogEntryInspection::class.java)
      writeTextAndCommit(buildScriptPath, buildGradleText)
      testHighlighting(tomlCatalogPath, versionCatalogText)
    }
  }


  @ParameterizedTest(name = "[{index}] {0}, project type={1}")
  @BaseGradleVersionSource("SIMPLE, COMPOSITE")
  fun testNoUsageOfLibrary(gradleVersion: GradleVersion, projectType: String) {
    runTest(gradleVersion, projectType, "", """
        [libraries]
        <warning>groovy-core</warning> = "aa:bb:2.0.0"      
    """.trimIndent())
  }

  @ParameterizedTest(name = "[{index}] {0}, project type={1}")
  @BaseGradleVersionSource("SIMPLE, COMPOSITE")
  fun testNoUsageOfBundle(gradleVersion: GradleVersion, projectType: String) {
    runTest(gradleVersion, projectType, "", """
        [bundles]
        <warning>ui</warning> = []
    """.trimIndent())
  }

  @ParameterizedTest(name = "[{index}] {0}, project type={1}")
  @BaseGradleVersionSource("SIMPLE, COMPOSITE")
  fun testNoUsageOfPlugin(gradleVersion: GradleVersion, projectType: String) {
    runTest(gradleVersion, projectType, "", """
        [plugins]
        <warning>groovy-core</warning> = "aa:bb:2.0.0"      
    """.trimIndent())
  }

  @ParameterizedTest(name = "[{index}] {0}, project type={1}")
  @BaseGradleVersionSource("SIMPLE, COMPOSITE")
  fun testUsageOfLibrary(gradleVersion: GradleVersion, projectType: String) {
    runTest(gradleVersion, projectType, "libs.groovy.core", """
        [libraries]
        groovy-core = "aa:bb:2.0.0"      
    """.trimIndent())
  }

  @ParameterizedTest(name = "[{index}] {0}, project type={1}")
  @BaseGradleVersionSource("SIMPLE, COMPOSITE")
  fun testUsageOfLibraryInBundle(gradleVersion: GradleVersion, projectType: String) {
    runTest(gradleVersion, projectType, "", """
        [libraries]
        groovy-core = "aa:bb:2.0.0"   
        [bundles]
        <warning>aa</warning> = [ "groovy-core" ]
    """.trimIndent())
  }

  @ParameterizedTest(name = "[{index}] {0}, project type={1}")
  @BaseGradleVersionSource("SIMPLE, COMPOSITE")
  fun testUsageOfBundle(gradleVersion: GradleVersion, projectType: String) {
    runTest(gradleVersion, projectType, "libs.bundles.ui", """
        [bundles]
        ui = []
    """.trimIndent())
  }

  @ParameterizedTest(name = "[{index}] {0}, project type={1}")
  @BaseGradleVersionSource("SIMPLE, COMPOSITE")
  fun testUsageOfPlugin(gradleVersion: GradleVersion, projectType: String) {
    runTest(gradleVersion, projectType, "libs.plugins.groovy.core", """
        [plugins]
        groovy-core = "10.0.0"      
    """.trimIndent())
  }

  @ParameterizedTest(name = "[{index}] {0}, project type={1}")
  @BaseGradleVersionSource("SIMPLE, COMPOSITE")
  fun testNoUsageOfVersion(gradleVersion: GradleVersion, projectType: String) {
    runTest(gradleVersion, projectType, "", """
        [versions]
        <warning>groovy-core</warning> = "10.0.0"      
    """.trimIndent())
  }


  @ParameterizedTest(name = "[{index}] {0}, project type={1}")
  @BaseGradleVersionSource("SIMPLE, COMPOSITE")
  fun testUsageOfVersionInBuildscript(gradleVersion: GradleVersion, projectType: String) {
    runTest(gradleVersion, projectType, "libs.versions.groovy.core", """
        [versions]
        groovy-core = "10.0.0"      
    """.trimIndent())
  }

  @ParameterizedTest(name = "[{index}] {0}, project type={1}")
  @BaseGradleVersionSource("SIMPLE, COMPOSITE")
  fun testUsageOfVersionInToml(gradleVersion: GradleVersion, projectType: String) {
    runTest(gradleVersion, projectType, "", """
        [versions]
        groovy-core = "10.0.0" 
        [libraries]
        <warning>aaa-bbb</warning> = { group = "org.apache.groovy", name = "groovy", version.ref = "groovy-core" }
    """.trimIndent())
  }


  @ParameterizedTest(name = "[{index}] {0}, project type={1}")
  @BaseGradleVersionSource("SIMPLE, COMPOSITE")
  fun testNoHighlightingForSuppressedUnusedLibrary(gradleVersion: GradleVersion, projectType: String) {
    runTest(gradleVersion, projectType, "", """
        [libraries]
        # noinspection UnusedVersionCatalogEntry
        groovy-core = "aa:bb:2.0.0"      
    """.trimIndent())
  }


  @ParameterizedTest(name = "[{index}] {0}, project type={1}")
  @BaseGradleVersionSource("SIMPLE, COMPOSITE")
  fun testNoHighlightingForSuppressedUnusedBundle(gradleVersion: GradleVersion, projectType: String) {
    runTest(gradleVersion, projectType, "", """
        [bundles]
        # noinspection UnusedVersionCatalogEntry
        ui = []
    """.trimIndent())
  }


  @ParameterizedTest(name = "[{index}] {0}, project type={1}")
  @BaseGradleVersionSource("SIMPLE, COMPOSITE")
  fun testNoHighlightingForSuppressedUnusedPlugin(gradleVersion: GradleVersion, projectType: String) {
    runTest(gradleVersion, projectType, "", """
        [plugins]
        # noinspection UnusedVersionCatalogEntry
        groovy-core = "aa:bb:2.0.0"      
    """.trimIndent())
  }

  @ParameterizedTest(name = "[{index}] {0}, project type={1}")
  @BaseGradleVersionSource("SIMPLE, COMPOSITE")
  fun testNoHighlightingForSuppressedUnusedVersion(gradleVersion: GradleVersion, projectType: String) {
    runTest(gradleVersion, projectType, "", """
        [versions]
        #noinspection UnusedVersionCatalogEntry
        groovy-core = "10.0.0"      
    """.trimIndent())
  }
}