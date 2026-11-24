// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.codeInsight.kotlin.backend.tests.integration.inspections

import com.intellij.gradle.codeInsight.backend.inspections.declarations.GradleAvoidDuplicateRepositoriesInspection
import com.intellij.gradle.codeInsight.kotlin.testFramework.checkGradleScriptHighlighting
import com.intellij.gradle.codeInsight.kotlin.testFramework.fixtures.simpleGradleCodeInsightFixture
import com.intellij.gradle.testFramework.annotations.AllGradleVersionsSource
import com.intellij.gradle.testFramework.annotations.TargetVersions
import com.intellij.gradle.testFramework.fixtures.application.GradleImmutableProjectTestApplication
import com.intellij.gradle.testFramework.util.DEPENDENCY_RESOLUTION_MANAGEMENT_SUPPORTED_VERSIONS
import com.intellij.gradle.testFramework.util.KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS
import org.gradle.util.GradleVersion
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass

@GradleImmutableProjectTestApplication
class KotlinAvoidDuplicateRepositoriesInspectionTest {

  @Nested
  @ParameterizedClass
  @AllGradleVersionsSource
  @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
  inner class AllGradleVersions(gradleVersion: GradleVersion) {

    private val codeInsightFixture by simpleGradleCodeInsightFixture(gradleVersion)

    @BeforeEach
    fun enableInspection() {
      codeInsightFixture.enableInspections(GradleAvoidDuplicateRepositoriesInspection::class.java)
    }

    @Test
    fun `test single repository`() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        repositories {
            mavenCentral()
        }
        """.trimIndent()
      )
    }

    @Test
    fun `test different repositories`() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        repositories {
            mavenCentral()
            maven { url = uri("https://repo1.maven.org/maven2/") }
        }
        """.trimIndent()
      )
    }

    @Test
    fun `test different repository configurations`() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        repositories {
            maven { url = uri("https://repo1.maven.org/maven2/") }
            maven { url = uri("https://some.other.repo/") }
        }
        """.trimIndent()
      )
    }

    @Test
    fun `test simple same repository`() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        repositories {
            <weak_warning descr="Repository 'mavenCentral()' is declared multiple times">mavenCentral</weak_warning>()
            <weak_warning descr="Repository 'mavenCentral()' is declared multiple times">mavenCentral</weak_warning>()
        }
        """.trimIndent()
      )
    }

    @Test
    fun `test long same repository`() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        repositories {
            <weak_warning descr="Repository 'maven{url=uri(\"https...' is declared multiple times">maven</weak_warning> { url = uri("https://repo1.maven.org/maven2/") }
            <weak_warning descr="Repository 'maven{url=uri(\"https...' is declared multiple times">maven</weak_warning> { url = uri("https://repo1.maven.org/maven2/") }
        }
        """.trimIndent()
      )
    }

    @Test
    fun `test same repository different white space`() {
      codeInsightFixture.checkGradleScriptHighlighting(
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

    @Test
    fun `test same repository different comments`() {
      codeInsightFixture.checkGradleScriptHighlighting(
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

    @Test
    fun `test ignore non repositories`() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        repositories {
            println("Hello world!")
            println("Hello world!")
        }
        """.trimIndent()
      )
    }

    @Test
    fun `test plugin repositories`() {
      codeInsightFixture.checkGradleScriptHighlighting(
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

    @Test
    fun `test plugin management repositories`() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        pluginManagement {
            repositories {
                <weak_warning>gradlePluginPortal</weak_warning>()
                <weak_warning>gradlePluginPortal</weak_warning>()
            }
        }
        """.trimIndent(),
        relativePath = "settings.gradle.kts",
      )
    }
  }

  @Nested
  @ParameterizedClass
  @AllGradleVersionsSource
  @TargetVersions(DEPENDENCY_RESOLUTION_MANAGEMENT_SUPPORTED_VERSIONS)
  inner class DependencyResolutionManagementRepositories(gradleVersion: GradleVersion) {

    private val codeInsightFixture by simpleGradleCodeInsightFixture(gradleVersion)

    @BeforeEach
    fun enableInspection() {
      codeInsightFixture.enableInspections(GradleAvoidDuplicateRepositoriesInspection::class.java)
    }

    @Test
    fun `test dependency resolution management repositories`() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        dependencyResolutionManagement {
            repositories {
                <weak_warning>google</weak_warning>()
                <weak_warning>google</weak_warning>()
            }
        }
        """.trimIndent(),
        relativePath = "settings.gradle.kts",
      )
    }
  }
}
