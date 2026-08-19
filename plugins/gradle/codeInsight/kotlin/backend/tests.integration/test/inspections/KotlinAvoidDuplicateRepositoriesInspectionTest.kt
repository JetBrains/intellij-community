// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.codeInsight.kotlin.backend.tests.integration.inspections

import com.intellij.gradle.codeInsight.backend.inspections.declarations.GradleAvoidDuplicateRepositoriesInspection
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.K2GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.testFramework.util.DEPENDENCY_RESOLUTION_MANAGEMENT_SUPPORTED_VERSIONS
import org.jetbrains.plugins.gradle.testFramework.util.assertThatKotlinDslScriptsModelImportIsSupported
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.jupiter.params.ParameterizedTest

class KotlinAvoidDuplicateRepositoriesInspectionTest : K2GradleCodeInsightTestCase() {

  private fun runTest(
    gradleVersion: GradleVersion,
    test: () -> Unit,
  ) {
    assertThatKotlinDslScriptsModelImportIsSupported(gradleVersion)
    testKotlinDslEmptyProject(gradleVersion) {
      codeInsightFixture.enableInspections(GradleAvoidDuplicateRepositoriesInspection::class.java)
      test()
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  @TargetVersions("6.0+")
  fun `test single repository`(gradleVersion: GradleVersion) {
    runTest(gradleVersion) {
      testHighlighting(
        """
        repositories {
            mavenCentral()
        }
        """.trimIndent()
      )
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  @TargetVersions("6.0+")
  fun `test different repositories`(gradleVersion: GradleVersion) {
    runTest(gradleVersion) {
      testHighlighting(
        """
        repositories {
            mavenCentral()
            maven { url = uri("https://repo1.maven.org/maven2/") }
        }
        """.trimIndent()
      )
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  @TargetVersions("6.0+")
  fun `test different repository configurations`(gradleVersion: GradleVersion) {
    runTest(gradleVersion) {
      testHighlighting(
        """
        repositories {
            maven { url = uri("https://repo1.maven.org/maven2/") }
            maven { url = uri("https://some.other.repo/") }
        }
        """.trimIndent()
      )
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  @TargetVersions("6.0+")
  fun `test simple same repository`(gradleVersion: GradleVersion) {
    runTest(gradleVersion) {
      testHighlighting(
        """
        repositories {
            <weak_warning descr="Repository 'mavenCentral()' is declared multiple times">mavenCentral</weak_warning>()
            <weak_warning descr="Repository 'mavenCentral()' is declared multiple times">mavenCentral</weak_warning>()
        }
        """.trimIndent()
      )
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  @TargetVersions("6.0+")
  fun `test long same repository`(gradleVersion: GradleVersion) {
    runTest(gradleVersion) {
      testHighlighting(
        """
        repositories {
            <weak_warning descr="Repository 'maven{url=uri(\"https...' is declared multiple times">maven</weak_warning> { url = uri("https://repo1.maven.org/maven2/") }
            <weak_warning descr="Repository 'maven{url=uri(\"https...' is declared multiple times">maven</weak_warning> { url = uri("https://repo1.maven.org/maven2/") }
        }
        """.trimIndent()
      )
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  @TargetVersions("6.0+")
  fun `test same repository different white space`(gradleVersion: GradleVersion) {
    runTest(gradleVersion) {
      testHighlighting(
        """
        repositories {
            <weak_warning>maven</weak_warning> { url = uri("https://repo1.maven.org/maven2/") }
            <weak_warning>maven</weak_warning> {
                url = uri("https://repo1.maven.org/maven2/")
            }
        }
        """.trimIndent()
      )
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  @TargetVersions("6.0+")
  fun `test same repository different comments`(gradleVersion: GradleVersion) {
    runTest(gradleVersion) {
      testHighlighting(
        """
        repositories {
            <weak_warning>ivy</weak_warning> {
                url = uri("https://repo.spring.io/milestone")
                // some comment
            }
            <weak_warning>ivy</weak_warning> {
                /* some other comment */
                url = uri("https://repo.spring.io/milestone")
            }
        }
        """.trimIndent()
      )
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  @TargetVersions("6.0+")
  fun `test ignore non repositories`(gradleVersion: GradleVersion) {
    runTest(gradleVersion) {
      testHighlighting(
        """
        repositories {
            println("Hello world!")
            println("Hello world!")
        }
        """.trimIndent()
      )
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  @TargetVersions("6.0+")
  fun `test plugin repositories`(gradleVersion: GradleVersion) {
    runTest(gradleVersion) {
      testHighlighting(
        """
        buildscript {
            repositories {
                <weak_warning>gradlePluginPortal</weak_warning>()
                <weak_warning>gradlePluginPortal</weak_warning>()
            }
        }
        """.trimIndent()
      )
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  @TargetVersions(DEPENDENCY_RESOLUTION_MANAGEMENT_SUPPORTED_VERSIONS)
  fun `test dependency resolution management repositories`(gradleVersion: GradleVersion) {
    runTest(gradleVersion) {
      testHighlighting(
        relativePath = "settings.gradle.kts",
        """
        dependencyResolutionManagement {
            repositories {
                <weak_warning>google</weak_warning>()
                <weak_warning>google</weak_warning>()
            }
        }
        """.trimIndent()
      )
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  @TargetVersions("6.0+")
  fun `test plugin management repositories`(gradleVersion: GradleVersion) {
    runTest(gradleVersion) {
      testHighlighting(
        relativePath = "settings.gradle.kts",
        """
        pluginManagement {
            repositories {
                <weak_warning>gradlePluginPortal</weak_warning>()
                <weak_warning>gradlePluginPortal</weak_warning>()
            }
        }
        """.trimIndent()
      )
    }
  }
}