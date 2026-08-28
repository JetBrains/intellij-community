// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.codeInsight.kotlin.backend.tests.integration.inspections

import com.intellij.gradle.codeInsight.backend.inspections.declarations.AvoidRepositoriesInBuildGradleInspection
import com.intellij.gradle.codeInsight.kotlin.testFramework.checkGradleScriptHighlighting
import com.intellij.gradle.codeInsight.kotlin.testFramework.checkNoGradleScriptIntentions
import com.intellij.gradle.codeInsight.kotlin.testFramework.configureGradleScript
import com.intellij.gradle.codeInsight.kotlin.testFramework.fixtures.simpleGradleCodeInsightFixture
import com.intellij.gradle.testFramework.annotations.AllGradleVersionsSource
import com.intellij.gradle.testFramework.annotations.TargetVersions
import com.intellij.gradle.testFramework.fixtures.application.GradleImmutableProjectTestApplication
import com.intellij.gradle.testFramework.fixtures.gradleCodeInsightFixture
import com.intellij.gradle.testFramework.projectInfo.buildFile
import com.intellij.gradle.testFramework.projectInfo.simpleSettingsFile
import com.intellij.gradle.testFramework.util.DEPENDENCY_RESOLUTION_MANAGEMENT_SUPPORTED_VERSIONS
import com.intellij.gradle.testFramework.util.KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS
import com.intellij.gradle.testFramework.util.getProjectFile
import com.intellij.gradle.testFramework.util.withSettingsFile
import com.intellij.gradle.testFramework.util.writeTextAndCommit
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass

@GradleImmutableProjectTestApplication
class KotlinAvoidRepositoriesInBuildGradleInspectionTest {

  @AfterEach
  fun tearDown() {
    KotlinSdkType.removeKotlinSdkInTests()
  }

  @Nested
  @ParameterizedClass
  @AllGradleVersionsSource
  @TargetVersions(DEPENDENCY_RESOLUTION_MANAGEMENT_SUPPORTED_VERSIONS)
  inner class DependencyResolutionManagementSupported(private val gradleVersion: GradleVersion) {

    private val codeInsightFixture by simpleGradleCodeInsightFixture(gradleVersion)

    @BeforeEach
    fun enableInspection() {
      codeInsightFixture.enableInspections(AvoidRepositoriesInBuildGradleInspection::class.java)
    }

    @Test
    fun testRepositoriesHighlighted() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        <weak_warning>repositories</weak_warning> {
            mavenCentral()
        }
        """.trimIndent()
      )
    }

    @Test
    fun testRepositoriesMoveToSettingsFile() {
      codeInsightFixture.checkMoveRepositoriesIntention(
        """
        repositories<caret> {
            mavenCentral()
            gradlePluginPortal()
        }
        """.trimIndent(),
        "",
        """
        rootProject.name = "test-project"
        """.trimIndent(),
        """
        dependencyResolutionManagement {
            ${repositoriesModeText(gradleVersion, "PREFER_PROJECT")}
            repositories {
                mavenCentral()
                gradlePluginPortal()
            }
        }
        
        rootProject.name = "test-project"
        """.trimIndent(),
        isForPlugins = false
      )
    }

    @Test
    fun testRepositoriesMoveToExistingDependencyResolutionManagement() {
      codeInsightFixture.checkMoveRepositoriesIntention(
        """
        repositories<caret> {
            mavenCentral()
        }
        """.trimIndent(),
        "",
        """
        rootProject.name = "test-project"

        dependencyResolutionManagement {
            ${repositoriesModeText(gradleVersion, "FAIL_ON_PROJECT_REPOS")}
            repositories {
                gradlePluginPortal()
            }
        }
        """.trimIndent(),
        """
        rootProject.name = "test-project"

        dependencyResolutionManagement {
            ${repositoriesModeText(gradleVersion, "FAIL_ON_PROJECT_REPOS")}
            repositories {
                gradlePluginPortal()
                mavenCentral()
            }
        }
        """.trimIndent(),
        isForPlugins = false
      )
    }

    @Test
    fun testRepositoriesMergeToExistingDependencyResolutionManagement() {
      codeInsightFixture.checkMoveRepositoriesIntention(
        """
        repositories<caret> {
            google()
            mavenCentral()
        }
        """.trimIndent(),
        "",
        """
        dependencyResolutionManagement {
            ${repositoriesModeText(gradleVersion, "FAIL_ON_PROJECT_REPOS")}
            repositories {
                mavenCentral()
            }
        }
        """.trimIndent(),
        """
        dependencyResolutionManagement {
            ${repositoriesModeText(gradleVersion, "FAIL_ON_PROJECT_REPOS")}
            repositories {
                mavenCentral()
                google()
                mavenCentral()
            }
        }
        """.trimIndent(),
        isForPlugins = false
      )
    }

    @Test
    fun testRepositoriesMergeToExistingDependencyResolutionManagementOverlap() {
      codeInsightFixture.checkMoveRepositoriesIntention(
        """
        repositories<caret> {
            mavenCentral()
            google()
            myRepo()
        }
        """.trimIndent(),
        "",
        """
        dependencyResolutionManagement {
            ${repositoriesModeText(gradleVersion, "FAIL_ON_PROJECT_REPOS")}
            repositories {
                mavenCentral()
                google()
            }
        }
        """.trimIndent(),
        """
        dependencyResolutionManagement {
            ${repositoriesModeText(gradleVersion, "FAIL_ON_PROJECT_REPOS")}
            repositories {
                mavenCentral()
                google()
                myRepo()
            }
        }
        """.trimIndent(),
        isForPlugins = false
      )
    }

    @Test
    fun testRepositoriesMergeToExistingEmptyDependencyResolutionManagement() {
      codeInsightFixture.checkMoveRepositoriesIntention(
        """
        repositories<caret> {
            google()
            mavenCentral()
        }
        """.trimIndent(),
        "",
        """
        dependencyResolutionManagement {}
        """.trimIndent(),
        """
        dependencyResolutionManagement {
            repositories {
                google()
                mavenCentral()
            }
        }
        """.trimIndent(),
        isForPlugins = false
      )
    }

    @Test
    fun testRepositoriesMergeToExistingDependencyResolutionManagementEmptyRepositories() {
      codeInsightFixture.checkMoveRepositoriesIntention(
        """
        repositories<caret> {
            google()
            mavenCentral()
        }
        """.trimIndent(),
        "",
        """
        dependencyResolutionManagement {
            repositories {}
        }
        """.trimIndent(),
        """
        dependencyResolutionManagement {
            repositories {
                google()
                mavenCentral()
            }
        }
        """.trimIndent(),
        isForPlugins = false
      )
    }

    @Test
    fun testRepositoriesMergeWithComments() {
      codeInsightFixture.checkMoveRepositoriesIntention(
        """
        repositories<caret> {
            // first comment
            mavenCentral()
            // second comment
            google()
            myRepo()
        }
        """.trimIndent(),
        "",
        """
        dependencyResolutionManagement {
            ${repositoriesModeText(gradleVersion, "FAIL_ON_PROJECT_REPOS")}
            repositories {
                mavenCentral()
                google()
            }
        }
        """.trimIndent(),
        """
        dependencyResolutionManagement {
            ${repositoriesModeText(gradleVersion, "FAIL_ON_PROJECT_REPOS")}
            repositories {
                mavenCentral()
                google()
                // first comment
                mavenCentral()
                // second comment
                google()
                myRepo()
            }
        }
        """.trimIndent(),
        isForPlugins = false
      )
    }

    @Test
    fun testRepositoriesMergePrefixMatch() {
      codeInsightFixture.checkMoveRepositoriesIntention(
        """
        repositories<caret> {
            mavenCentral()
            google()
            myRepo()
        }
        """.trimIndent(),
        "",
        """
        dependencyResolutionManagement {
            ${repositoriesModeText(gradleVersion, "FAIL_ON_PROJECT_REPOS")}
            repositories {
                mavenCentral()
                google()
                myRepo()
                anotherRepo()
            }
        }
        """.trimIndent(),
        """
        dependencyResolutionManagement {
            ${repositoriesModeText(gradleVersion, "FAIL_ON_PROJECT_REPOS")}
            repositories {
                mavenCentral()
                google()
                myRepo()
                anotherRepo()
            }
        }
        """.trimIndent(),
        isForPlugins = false
      )
    }

    @Test
    fun testRepositoriesMergeTotalMatch() {
      codeInsightFixture.checkMoveRepositoriesIntention(
        """
        repositories<caret> {
            mavenCentral()
            google()
            myRepo()
        }
        """.trimIndent(),
        "",
        """
        dependencyResolutionManagement {
            ${repositoriesModeText(gradleVersion, "FAIL_ON_PROJECT_REPOS")}
            repositories {
                mavenCentral()
                google()
                myRepo()
            }
        }
        """.trimIndent(),
        """
        dependencyResolutionManagement {
            ${repositoriesModeText(gradleVersion, "FAIL_ON_PROJECT_REPOS")}
            repositories {
                mavenCentral()
                google()
                myRepo()
            }
        }
        """.trimIndent(),
        isForPlugins = false
      )
    }

    @Test
    fun testRepositoriesWithMultipleStatements() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        <weak_warning>repositories</weak_warning> {
            mavenCentral()
            gradlePluginPortal()
            maven {
                url = uri("https://repo.spring.io/release")
            }
        }
        """.trimIndent()
      )
    }

    @Test
    fun testMultipleRepositoriesWithComplexContent() {
      codeInsightFixture.checkMoveRepositoriesIntention(
        """
        repositories<caret> {
            mavenCentral()
            maven {
                url = uri("https://repo.spring.io/release")
            }
        }
        """.trimIndent(),
        "",
        """
        rootProject.name = "test-project"
        """.trimIndent(),
        """
        dependencyResolutionManagement {
            ${repositoriesModeText(gradleVersion, "PREFER_PROJECT")}
            repositories {
                mavenCentral()
                maven {
                    url = uri("https://repo.spring.io/release")
                }
            }
        }
        
        rootProject.name = "test-project"
        """.trimIndent(),
        isForPlugins = false
      )
    }

    @Test
    fun testMultipleRepositoriesBlocks() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        <weak_warning>repositories</weak_warning> {
            mavenCentral()
        }
        
        dependencies {
            //implementation("org.example:lib:1.0")
        }
        
        <weak_warning>repositories</weak_warning> {
            gradlePluginPortal()
        }
        """.trimIndent()
      )
    }

    @Test
    fun testDependencyResolutionManagementAfterPluginManagement() {
      codeInsightFixture.checkMoveRepositoriesIntention(
        """
        repositories<caret> {
            mavenCentral()
        }
        """.trimIndent(),
        "",
        """
        pluginManagement {
            repositories {
                gradlePluginPortal()
            }
        }

        rootProject.name = "test-project"
        """.trimIndent(),
        """
        pluginManagement {
            repositories {
                gradlePluginPortal()
            }
        }

        dependencyResolutionManagement {
            ${repositoriesModeText(gradleVersion, "PREFER_PROJECT")}
            repositories {
                mavenCentral()
            }
        }

        rootProject.name = "test-project"
        """.trimIndent(),
        isForPlugins = false
      )
    }

    @Test
    fun testDependencyResolutionManagementAfterPluginsBlock() {
      codeInsightFixture.checkMoveRepositoriesIntention(
        """
        repositories<caret> {
            maven {
                url = uri("https://jitpack.io")
            }
        }
        """.trimIndent(),
        "",
        """
        plugins {
            id("org.springframework.boot") version "3.2.0"
        }

        rootProject.name = "test-project"
        """.trimIndent(),
        """
        plugins {
            id("org.springframework.boot") version "3.2.0"
        }

        dependencyResolutionManagement {
            ${repositoriesModeText(gradleVersion, "PREFER_PROJECT")}
            repositories {
                maven {
                    url = uri("https://jitpack.io")
                }
            }
        }

        rootProject.name = "test-project"
        """.trimIndent(),
        isForPlugins = false
      )
    }

    @Test
    fun testDependencyResolutionManagementAfterBothPluginManagementAndPlugins() {
      codeInsightFixture.checkMoveRepositoriesIntention(
        """
        repositories<caret> {
            mavenCentral()
            maven {
                url = uri("https://repo.spring.io/milestone")
            }
        }
        """.trimIndent(),
        "",
        """
        pluginManagement {
            repositories {
                gradlePluginPortal()
                mavenCentral()
            }
            plugins {
                id("org.jetbrains.kotlin.jvm") version "1.9.20"
            }
        }

        plugins {
            id("org.springframework.boot") version "3.2.0"
            id("io.spring.dependency-management") version "1.1.4"
        }

        rootProject.name = "test-project"
        """.trimIndent(),
        """
        pluginManagement {
            repositories {
                gradlePluginPortal()
                mavenCentral()
            }
            plugins {
                id("org.jetbrains.kotlin.jvm") version "1.9.20"
            }
        }

        plugins {
            id("org.springframework.boot") version "3.2.0"
            id("io.spring.dependency-management") version "1.1.4"
        }

        dependencyResolutionManagement {
            ${repositoriesModeText(gradleVersion, "PREFER_PROJECT")}
            repositories {
                mavenCentral()
                maven {
                    url = uri("https://repo.spring.io/milestone")
                }
            }
        }

        rootProject.name = "test-project"
        """.trimIndent(),
        isForPlugins = false
      )
    }

    @Test
    fun testCorrectOrderingWithAllTopLevelBlocks() {
      codeInsightFixture.checkMoveRepositoriesIntention(
        """
        repositories<caret> {
            mavenCentral()
            mavenLocal()
        }
        """.trimIndent(),
        "",
        """
        pluginManagement {
            repositories {
                gradlePluginPortal()
            }
        }

        plugins {
            id("java-library")
        }

        rootProject.name = "test-project"

        include("subproject1", "subproject2")
        """.trimIndent(),
        """
        pluginManagement {
            repositories {
                gradlePluginPortal()
            }
        }

        plugins {
            id("java-library")
        }

        dependencyResolutionManagement {
            ${repositoriesModeText(gradleVersion, "PREFER_PROJECT")}
            repositories {
                mavenCentral()
                mavenLocal()
            }
        }

        rootProject.name = "test-project"

        include("subproject1", "subproject2")
        """.trimIndent(),
        isForPlugins = false
      )
    }

    @Test
    fun testEmptySettingsFile() {
      codeInsightFixture.checkMoveRepositoriesIntention(
        """
        repositories<caret> {
            google()
            mavenCentral()
        }
        """.trimIndent(),
        "",
        "",
        """
        dependencyResolutionManagement {
            ${repositoriesModeText(gradleVersion, "PREFER_PROJECT")}
            repositories {
                google()
                mavenCentral()
            }
        }
        """.trimIndent(),
        isForPlugins = false
      )
    }

    @Test
    fun testBuildCacheDoesNotAffectOrdering() {
      codeInsightFixture.checkMoveRepositoriesIntention(
        """
        repositories<caret> {
            mavenCentral()
        }
        """.trimIndent(),
        "",
        """
        buildCache {
            local {
                directory = File(rootDir, "build-cache")
            }
        }
        
        rootProject.name = "test-project"
        """.trimIndent(),
        """
        dependencyResolutionManagement {
            ${repositoriesModeText(gradleVersion, "PREFER_PROJECT")}
            repositories {
                mavenCentral()
            }
        }
        
        buildCache {
            local {
                directory = File(rootDir, "build-cache")
            }
        }
        
        rootProject.name = "test-project"
        """.trimIndent(),
        isForPlugins = false
      )
    }
  }

  @Nested
  @ParameterizedClass
  @AllGradleVersionsSource
  @TargetVersions(
    KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS, "<6.8",
    reason = "dependencyResolutionManagement was added in Gradle 6.8"
  )
  inner class DependencyResolutionManagementNotSupported(gradleVersion: GradleVersion) {

    private val codeInsightFixture by simpleGradleCodeInsightFixture(gradleVersion)

    @BeforeEach
    fun enableInspection() {
      codeInsightFixture.enableInspections(AvoidRepositoriesInBuildGradleInspection::class.java)
    }

    @Test
    fun testNoDependencyResolutionManagement() {
      codeInsightFixture.checkGradleScriptHighlighting("repositories { mavenCentral() }")
    }
  }

  @Nested
  @ParameterizedClass
  @AllGradleVersionsSource
  @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
  inner class AllGradleVersions(gradleVersion: GradleVersion) {

    private val codeInsightFixture by simpleGradleCodeInsightFixture(gradleVersion)

    @BeforeEach
    fun enableInspection() {
      codeInsightFixture.enableInspections(AvoidRepositoriesInBuildGradleInspection::class.java)
    }

    @Test
    fun testRepositoriesInBuildscriptHighlighted() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        buildscript {
            <weak_warning>repositories</weak_warning> {
                gradlePluginPortal()
            }
        }
        """.trimIndent()
      )
    }

    @Test
    fun testRepositoriesMoveFromBuildscriptToSettingsFile() {
      codeInsightFixture.checkMoveRepositoriesIntention(
        """
        buildscript {
            repositories<caret> {
                gradlePluginPortal()
                mavenCentral()
            }
        }
        """.trimIndent(),
        """
        buildscript {
        }
        """.trimIndent(),
        """
        rootProject.name = "test-project"
        """.trimIndent(),
        """
        pluginManagement {
            repositories {
                gradlePluginPortal()
                mavenCentral()
            }
        }
        
        rootProject.name = "test-project"
        """.trimIndent(),
        isForPlugins = true
      )
    }

    @Test
    fun testRepositoriesMoveToExistingPluginManagement() {
      codeInsightFixture.checkMoveRepositoriesIntention(
        """
        buildscript {
            repositories<caret> {
                gradlePluginPortal()
            }
        }
        """.trimIndent(),
        """
        buildscript {
        }
        """.trimIndent(),
        """
        rootProject.name = "test-project"

        pluginManagement {
            repositories {
                mavenCentral()
            }
        }
        """.trimIndent(),
        """
        rootProject.name = "test-project"

        pluginManagement {
            repositories {
                mavenCentral()
                gradlePluginPortal()
            }
        }
        """.trimIndent(),
        isForPlugins = true
      )
    }

    @Test
    fun testRepositoriesMergeToExistingPluginManagement() {
      codeInsightFixture.checkMoveRepositoriesIntention(
        """
        buildscript {
            repositories<caret> {
                gradlePluginPortal()
                mavenCentral()
            }
        }
        """.trimIndent(),
        """
        buildscript {
        }
        """.trimIndent(),
        """
        pluginManagement {
            repositories {
                mavenCentral()
            }
        }
        """.trimIndent(),
        """
        pluginManagement {
            repositories {
                mavenCentral()
                gradlePluginPortal()
                mavenCentral()
            }
        }
        """.trimIndent(),
        isForPlugins = true
      )
    }

    @Test
    fun testRepositoriesMoveToExistingEmptyPluginManagement() {
      codeInsightFixture.checkMoveRepositoriesIntention(
        """
        buildscript {
            repositories<caret> {
                mavenCentral()
                gradlePluginPortal()
            }
        }
        """.trimIndent(),
        """
        buildscript {
        }
        """.trimIndent(),
        """
        pluginManagement {}
        """.trimIndent(),
        """
        pluginManagement {
            repositories {
                mavenCentral()
                gradlePluginPortal()
            }
        }
        """.trimIndent(),
        isForPlugins = true
      )
    }

    @Test
    fun testRepositoriesMoveToExistingPluginManagementEmptyRepositories() {
      codeInsightFixture.checkMoveRepositoriesIntention(
        """
        buildscript {
            repositories<caret> {
                mavenCentral()
                gradlePluginPortal()
            }
        }
        """.trimIndent(),
        """
        buildscript {
        }
        """.trimIndent(),
        """
        pluginManagement {
            repositories {}
        }
        """.trimIndent(),
        """
        pluginManagement {
            repositories {
                mavenCentral()
                gradlePluginPortal()
            }
        }
        """.trimIndent(),
        isForPlugins = true
      )
    }

    @Test
    fun testPluginManagementOrderingWithBuildscriptRepositories() {
      codeInsightFixture.checkMoveRepositoriesIntention(
        """
        buildscript {
            repositories<caret> {
                gradlePluginPortal()
                maven {
                    url = uri("https://plugins.gradle.org/m2/")
                }
            }
        }
        """.trimIndent(),
        """
        buildscript {
        }
        """.trimIndent(),
        """
        plugins {
            id("org.springframework.boot") version "3.2.0"
        }

        rootProject.name = "test-project"
        """.trimIndent(),
        """
        pluginManagement {
            repositories {
                gradlePluginPortal()
                maven {
                    url = uri("https://plugins.gradle.org/m2/")
                }
            }
        }

        plugins {
            id("org.springframework.boot") version "3.2.0"
        }

        rootProject.name = "test-project"
        """.trimIndent(),
        isForPlugins = true
      )
    }
  }

  @Nested
  @ParameterizedClass
  @AllGradleVersionsSource
  @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
  inner class PublishingPlugin(gradleVersion: GradleVersion) {

    private val codeInsightFixture by publishingPluginCodeInsightFixture(gradleVersion)

    @BeforeEach
    fun enableInspection() {
      codeInsightFixture.enableInspections(AvoidRepositoriesInBuildGradleInspection::class.java)
    }

    @Test
    fun testNestedRepositoriesNotHighlighted() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        publishing {
            repositories {
                maven {
                    url = uri("https://repo.example.com")
                }
            }
        }
        """.trimIndent()
      )
    }
  }

  @Nested
  @ParameterizedClass
  @AllGradleVersionsSource
  @TargetVersions(DEPENDENCY_RESOLUTION_MANAGEMENT_SUPPORTED_VERSIONS)
  inner class OnlyBuildFileDependencyResolutionManagementSupported(private val gradleVersion: GradleVersion) {

    private val codeInsightFixture by onlyBuildFileCodeInsightFixture(gradleVersion)

    @BeforeEach
    fun enableInspection() {
      codeInsightFixture.enableInspections(AvoidRepositoriesInBuildGradleInspection::class.java)
    }

    @Test
    fun testMoveRepositoriesWithoutSettingsFile() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        <weak_warning>repositories</weak_warning> {
            mavenCentral()
        }
        """.trimIndent()
      )
      codeInsightFixture.checkMoveRepositoriesIntention(
        """
        <caret>repositories {
            mavenCentral()
        }
        """.trimIndent(),
        "",
        null,
        """
        dependencyResolutionManagement {
            ${repositoriesModeText(gradleVersion, "PREFER_PROJECT")}
            repositories {
                mavenCentral()
            }
        }
        """.trimIndent(),
        isForPlugins = false
      )
    }
  }

  @Nested
  @ParameterizedClass
  @AllGradleVersionsSource
  @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
  inner class OnlyBuildFile(gradleVersion: GradleVersion) {

    private val codeInsightFixture by onlyBuildFileCodeInsightFixture(gradleVersion)

    @BeforeEach
    fun enableInspection() {
      codeInsightFixture.enableInspections(AvoidRepositoriesInBuildGradleInspection::class.java)
    }

    @Test
    fun testMoveRepositoriesInBuildscriptWithoutSettingsFile() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        buildscript {
            <weak_warning>repositories</weak_warning> { 
                mavenCentral()
            }
        }
        """.trimIndent()
      )
      codeInsightFixture.checkMoveRepositoriesIntention(
        """
        buildscript {
            <caret>repositories {
                mavenCentral()
            }
        }
        """.trimIndent(),
        """
        buildscript {
        }
        """.trimIndent(),
        null,
        """
        pluginManagement {
            repositories {
                mavenCentral()
            }
        }
        """.trimIndent(),
        isForPlugins = true
      )
    }
  }

  @Nested
  @ParameterizedClass
  @AllGradleVersionsSource
  @TargetVersions(DEPENDENCY_RESOLUTION_MANAGEMENT_SUPPORTED_VERSIONS)
  inner class GroovySettingsFile(gradleVersion: GradleVersion) {

    private val codeInsightFixture by groovySettingsFileCodeInsightFixture(gradleVersion)

    @BeforeEach
    fun enableInspection() {
      codeInsightFixture.enableInspections(AvoidRepositoriesInBuildGradleInspection::class.java)
    }

    @Test
    fun testNoQuickFixWithGroovySettingsFile() {
      codeInsightFixture.checkGradleScriptHighlighting("<weak_warning>repositories</weak_warning> { mavenCentral() }")
      codeInsightFixture.checkNoGradleScriptIntentions(
        "repositories<caret> { mavenCentral() }",
        "Move repositories to the Gradle settings file"
      )
    }
  }

  @Nested
  @ParameterizedClass
  @AllGradleVersionsSource
  @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
  inner class MultiModule(gradleVersion: GradleVersion) {

    private val codeInsightFixture by multiModuleCodeInsightFixture(gradleVersion)

    @BeforeEach
    fun enableInspection() {
      codeInsightFixture.enableInspections(AvoidRepositoriesInBuildGradleInspection::class.java)
    }

    @Test
    fun testSubprojectCanFindSettingsFile() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        buildscript {
            <weak_warning>repositories</weak_warning> { mavenCentral() }
        }
        """.trimIndent(),
        relativePath = "subproject/build.gradle.kts",
      )
      codeInsightFixture.checkMoveRepositoriesIntention(
        """
        buildscript {
            repositories<caret> { mavenCentral() }
        }
        """.trimIndent(),
        """
        buildscript {
        }
        """.trimIndent(),
        """
        include("subproject")
        """.trimIndent(),
        """
        pluginManagement {
            repositories {
                mavenCentral()
            }
        }
        
        include("subproject")
        """.trimIndent(),
        isForPlugins = true,
        relativeBuildFilePath = "subproject/build.gradle.kts"
      )
    }

    @Test
    fun testSubprojectOutsideRootCanFindSettingsFile() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        buildscript {
            <weak_warning>repositories</weak_warning> { mavenCentral() }
        }
        """.trimIndent(),
        relativePath = "../subproject-outside-root/build.gradle.kts",
      )
      codeInsightFixture.checkMoveRepositoriesIntention(
        """
        buildscript {
            repositories<caret> { mavenCentral() }
        }
        """.trimIndent(),
        """
        buildscript {
        }
        """.trimIndent(),
        """
        include("subproject-outside-root")
        project(":subproject-outside-root").projectDir = file("../subproject-outside-root")
        """.trimIndent(),
        """
        pluginManagement {
            repositories {
                mavenCentral()
            }
        }
        
        include("subproject-outside-root")
        project(":subproject-outside-root").projectDir = file("../subproject-outside-root")
        """.trimIndent(),
        isForPlugins = true,
        relativeBuildFilePath = "../subproject-outside-root/build.gradle.kts"
      )
    }
  }

  @Nested
  @ParameterizedClass
  @AllGradleVersionsSource
  @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
  inner class CompositeWithSettingsFiles(gradleVersion: GradleVersion) {

    private val codeInsightFixture by compositeWithSettingsFilesCodeInsightFixture(gradleVersion)

    @BeforeEach
    fun enableInspection() {
      codeInsightFixture.enableInspections(AvoidRepositoriesInBuildGradleInspection::class.java)
    }

    @Test
    fun testIncludedBuildCanFindSettingsFile() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        buildscript {
            <weak_warning>repositories</weak_warning> { 
                mavenCentral()
            }
        }
        """.trimIndent(),
        relativePath = "included/build.gradle.kts",
      )
      codeInsightFixture.checkMoveRepositoriesIntention(
        """
        buildscript {
            repositories<caret> { 
                mavenCentral() 
            }
        }
        """.trimIndent(),
        """
        buildscript {
        }
        """.trimIndent(),
        """
        rootProject.name = "included"
        """.trimIndent(),
        """
        pluginManagement {
            repositories {
                mavenCentral()
            }
        }
        
        rootProject.name = "included"
        """.trimIndent(),
        isForPlugins = true,
        relativeBuildFilePath = "included/build.gradle.kts",
        relativeSettingsFilePath = "included/settings.gradle.kts"
      )
    }

    @Test
    fun testSubprojectInIncludedBuildCanFindSettingsFile() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        buildscript {
            <weak_warning>repositories</weak_warning> { 
                mavenCentral()
            }
        }
        """.trimIndent(),
        relativePath = "included/subproject/build.gradle.kts",
      )
      codeInsightFixture.checkMoveRepositoriesIntention(
        """
        buildscript {
            repositories<caret> { 
                mavenCentral() 
            }
        }
        """.trimIndent(),
        """
        buildscript {
        }
        """.trimIndent(),
        """
        rootProject.name = "included"
        include("subproject")
        """.trimIndent(),
        """
        pluginManagement {
            repositories {
                mavenCentral()
            }
        }
        
        rootProject.name = "included"
        include("subproject")
        """.trimIndent(),
        isForPlugins = true,
        relativeBuildFilePath = "included/subproject/build.gradle.kts",
        relativeSettingsFilePath = "included/settings.gradle.kts"
      )
    }
  }

  @Nested
  @ParameterizedClass
  @AllGradleVersionsSource
  @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
  inner class CompositeWithoutIncludedSettingsFile(gradleVersion: GradleVersion) {

    private val codeInsightFixture by compositeWithoutIncludedSettingsFileCodeInsightFixture(gradleVersion)

    @BeforeEach
    fun enableInspection() {
      codeInsightFixture.enableInspections(AvoidRepositoriesInBuildGradleInspection::class.java)
    }

    @Test
    fun testIncludedBuildCreatesSettingsFileInItsOwnRoot() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        buildscript {
            <weak_warning>repositories</weak_warning> { 
                mavenCentral()
            }
        }
        """.trimIndent(),
        relativePath = "included/build.gradle.kts",
      )
      codeInsightFixture.checkMoveRepositoriesIntention(
        """
        buildscript {
            repositories<caret> { 
                mavenCentral() 
            }
        }
        """.trimIndent(),
        """
        buildscript {
        }
        """.trimIndent(),
        null,
        """
        pluginManagement {
            repositories { 
                mavenCentral() 
            }
        }
        """.trimIndent(),
        isForPlugins = true,
        relativeBuildFilePath = "included/build.gradle.kts",
        relativeSettingsFilePath = "included/settings.gradle.kts"
      )
    }
  }

  companion object {
    private fun publishingPluginCodeInsightFixture(gradleVersion: GradleVersion) =
      gradleCodeInsightFixture(gradleVersion, "empty-project-with-publishing-plugin") {
        simpleSettingsFile()
        buildFile {
          withPlugin("publishing")
        }
      }

    private fun onlyBuildFileCodeInsightFixture(gradleVersion: GradleVersion) =
      gradleCodeInsightFixture(gradleVersion, "empty-project-with-only-build-file") {
        buildFile { }
      }

    private fun groovySettingsFileCodeInsightFixture(gradleVersion: GradleVersion) =
      gradleCodeInsightFixture(gradleVersion, "empty-project-with-groovy-settings-file") {
        files.withSettingsFile(gradleVersion, gradleDsl = GradleDsl.GROOVY) {
          setProjectName(projectName)
        }
        buildFile { }
      }

    private fun multiModuleCodeInsightFixture(gradleVersion: GradleVersion) =
      gradleCodeInsightFixture(gradleVersion, "empty-multi-module-project") {
        simpleSettingsFile {
          include("subproject")
          include("subproject-outside-root")
          setProjectDir(":subproject-outside-root", "../subproject-outside-root")
        }
        buildFile { }
        moduleInfo("$projectName.subproject", "subproject") { buildFile { } }
        moduleInfo("$projectName.subproject-outside-root", "../subproject-outside-root") { buildFile { } }
      }

    private fun compositeWithSettingsFilesCodeInsightFixture(gradleVersion: GradleVersion) =
      gradleCodeInsightFixture(gradleVersion, "composite-project-with-settings-files") {
        simpleSettingsFile {
          includeBuild("included")
        }
        buildFile { }
        compositeInfo("included", "included") {
          simpleSettingsFile {
            include("subproject")
          }
          buildFile { }
          moduleInfo("included.subproject", "subproject") { buildFile { } }
        }
      }

    private fun compositeWithoutIncludedSettingsFileCodeInsightFixture(gradleVersion: GradleVersion) =
      gradleCodeInsightFixture(gradleVersion, "cmpst-prjct-without-included-settings") {
        simpleSettingsFile {
          includeBuild("included")
        }
        buildFile { }
        compositeInfo("included", "included") {
          buildFile { }
        }
      }

    /**
     * Applies the intention that moves the repositories of the build script at [relativeBuildFilePath] to the
     * settings script at [relativeSettingsFilePath], and checks both scripts.
     *
     * @param settingsBefore the settings script before the intention, or null when the project has no settings script
     */
    private fun JavaCodeInsightTestFixture.checkMoveRepositoriesIntention(
      buildBefore: String, buildAfter: String,
      settingsBefore: String?, settingsAfter: String,
      isForPlugins: Boolean,
      relativeBuildFilePath: String = GradleConstants.KOTLIN_DSL_SCRIPT_NAME,
      relativeSettingsFilePath: String = GradleConstants.KOTLIN_DSL_SETTINGS_FILE_NAME,
    ) {
      if (settingsBefore != null) {
        project.writeTextAndCommit(relativeSettingsFilePath, settingsBefore)
      }
      configureGradleScript(buildBefore, relativeBuildFilePath) {
        val repositoriesParentBlockInSettingsName = if (isForPlugins) "pluginManagement" else "dependencyResolutionManagement"
        val intentionName =
          if (settingsBefore != null) "Move repositories to the '$repositoriesParentBlockInSettingsName' block in the 'settings.gradle.kts' file"
          else "Create a 'settings.gradle.kts' file and move repositories to the '$repositoriesParentBlockInSettingsName' block"
        val intention = findSingleIntention(intentionName)
        launchAction(intention)
        checkResult(buildAfter)
        // The intention changes the settings script in memory. Save it before it is configured from the disk.
        FileDocumentManager.getInstance().saveAllDocuments()
        configureFromExistingVirtualFile(project.getProjectFile(relativeSettingsFilePath))
        checkResult(settingsAfter)
      }
    }

    private fun repositoriesModeText(gradleVersion: GradleVersion, mode: String) =
      if (gradleVersion >= GradleVersion.version("8.2")) "repositoriesMode = RepositoriesMode.$mode"
      else "repositoriesMode.set(RepositoriesMode.$mode)"
  }
}
