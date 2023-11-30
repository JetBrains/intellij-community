// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.dsl.inspections

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.codeInspection.toml.UnusedVersionCatalogEntryInspection
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.junit.jupiter.params.ParameterizedTest

class GradleUnusedVersionCatalogInspectionTest : GradleCodeInsightTestCase() {

  private fun runTest(gradleVersion: GradleVersion, buildGradleText: String, versionCatalogText: String) {
    testEmptyProject(gradleVersion) {
      codeInsightFixture.enableInspections(UnusedVersionCatalogEntryInspection::class.java)
      writeTextAndCommit("build.gradle", buildGradleText)
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
  fun testNoUsageOfBundle(gradleVersion: GradleVersion) {
    runTest(gradleVersion, "", """
        [bundles]
        <warning>ui</warning> = []
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
  fun testUsageOfLibraryInBundle(gradleVersion: GradleVersion) {
    runTest(gradleVersion, "", """
        [libraries]
        groovy-core = "aa:bb:2.0.0"   
        [bundles]
        <warning>aa</warning> = [ "groovy-core" ]
    """.trimIndent())
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testUsageOfBundle(gradleVersion: GradleVersion) {
    runTest(gradleVersion, "libs.bundles.ui", """
        [bundles]
        ui = []
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