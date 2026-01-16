// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.inspections

import com.intellij.testFramework.runInEdtAndWait
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.scripting.k2.K2GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.codeInspection.AvoidRepositoriesInBuildGradleInspection
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.testFramework.util.*
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.junit.jupiter.params.ParameterizedTest

class KotlinAvoidRepositoriesInBuildGradleInspectionTest : K2GradleCodeInsightTestCase() {

    private fun runTest(
        gradleVersion: GradleVersion,
        projectFixture: GradleTestFixtureBuilder,
        test: () -> Unit
    ) {
        assumeThatKotlinDslScriptsModelImportIsSupported(gradleVersion)
        test(gradleVersion, projectFixture) {
            codeInsightFixture.enableInspections(AvoidRepositoriesInBuildGradleInspection::class.java)
            test()
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testRepositoriesHighlighted(gradleVersion: GradleVersion) {
        assumeThatDependencyResolutionManagementIsSupported(gradleVersion)
        runTest(gradleVersion, KOTLIN_DSL_EMPTY_PROJECT) {
            testHighlighting(
                """
                <weak_warning>repositories</weak_warning> {
                    mavenCentral()
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testNoDependencyResolutionManagement(gradleVersion: GradleVersion) {
        assumeThatGradleIsOlderThan(gradleVersion, "6.8") { "dependencyResolutionManagement was added in Gradle 6.8" }
        runTest(gradleVersion, KOTLIN_DSL_EMPTY_PROJECT) {
            testHighlighting("repositories { mavenCentral() }")
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testRepositoriesInBuildscriptHighlighted(gradleVersion: GradleVersion) {
        runTest(gradleVersion, KOTLIN_DSL_EMPTY_PROJECT) {
            testHighlighting(
                """
                buildscript {
                    <weak_warning>repositories</weak_warning> {
                        gradlePluginPortal()
                    }
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testRepositoriesMoveToSettingsFile(gradleVersion: GradleVersion) {
        assumeThatDependencyResolutionManagementIsSupported(gradleVersion)
        runTest(gradleVersion, KOTLIN_DSL_EMPTY_PROJECT) {
            testMyIntention(
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
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testRepositoriesMoveFromBuildscriptToSettingsFile(gradleVersion: GradleVersion) {
        runTest(gradleVersion, KOTLIN_DSL_EMPTY_PROJECT) {
            testMyIntention(
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
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testRepositoriesMoveToExistingDependencyResolutionManagement(gradleVersion: GradleVersion) {
        assumeThatDependencyResolutionManagementIsSupported(gradleVersion)
        runTest(gradleVersion, KOTLIN_DSL_EMPTY_PROJECT) {
            testMyIntention(
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
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testRepositoriesMoveToExistingPluginManagement(gradleVersion: GradleVersion) {
        runTest(gradleVersion, KOTLIN_DSL_EMPTY_PROJECT) {
            testMyIntention(
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
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testRepositoriesMergeToExistingDependencyResolutionManagement(gradleVersion: GradleVersion) {
        assumeThatDependencyResolutionManagementIsSupported(gradleVersion)
        runTest(gradleVersion, KOTLIN_DSL_EMPTY_PROJECT) {
            testMyIntention(
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
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testRepositoriesMergeToExistingDependencyResolutionManagementOverlap(gradleVersion: GradleVersion) {
        assumeThatDependencyResolutionManagementIsSupported(gradleVersion)
        runTest(gradleVersion, KOTLIN_DSL_EMPTY_PROJECT) {
            testMyIntention(
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
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testRepositoriesMergeToExistingPluginManagement(gradleVersion: GradleVersion) {
        runTest(gradleVersion, KOTLIN_DSL_EMPTY_PROJECT) {
            testMyIntention(
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
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testRepositoriesMergeToExistingEmptyDependencyResolutionManagement(gradleVersion: GradleVersion) {
        assumeThatDependencyResolutionManagementIsSupported(gradleVersion)
        runTest(gradleVersion, KOTLIN_DSL_EMPTY_PROJECT) {
            testMyIntention(
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
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testRepositoriesMergeToExistingDependencyResolutionManagementEmptyRepositories(gradleVersion: GradleVersion) {
        assumeThatDependencyResolutionManagementIsSupported(gradleVersion)
        runTest(gradleVersion, KOTLIN_DSL_EMPTY_PROJECT) {
            testMyIntention(
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
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testRepositoriesMergeWithComments(gradleVersion: GradleVersion) {
        assumeThatDependencyResolutionManagementIsSupported(gradleVersion)
        runTest(gradleVersion, KOTLIN_DSL_EMPTY_PROJECT) {
            testMyIntention(
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
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testRepositoriesMergePrefixMatch(gradleVersion: GradleVersion) {
        assumeThatDependencyResolutionManagementIsSupported(gradleVersion)
        runTest(gradleVersion, KOTLIN_DSL_EMPTY_PROJECT) {
            testMyIntention(
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
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testRepositoriesMergeTotalMatch(gradleVersion: GradleVersion) {
        assumeThatDependencyResolutionManagementIsSupported(gradleVersion)
        runTest(gradleVersion, KOTLIN_DSL_EMPTY_PROJECT) {
            testMyIntention(
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
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testRepositoriesMoveToExistingEmptyPluginManagement(gradleVersion: GradleVersion) {
        runTest(gradleVersion, KOTLIN_DSL_EMPTY_PROJECT) {
            testMyIntention(
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
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testRepositoriesMoveToExistingPluginManagementEmptyRepositories(gradleVersion: GradleVersion) {
        runTest(gradleVersion, KOTLIN_DSL_EMPTY_PROJECT) {
            testMyIntention(
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
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testRepositoriesWithMultipleStatements(gradleVersion: GradleVersion) {
        assumeThatDependencyResolutionManagementIsSupported(gradleVersion)
        runTest(gradleVersion, KOTLIN_DSL_EMPTY_PROJECT) {
            testHighlighting(
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
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testMultipleRepositoriesWithComplexContent(gradleVersion: GradleVersion) {
        assumeThatDependencyResolutionManagementIsSupported(gradleVersion)
        runTest(gradleVersion, KOTLIN_DSL_EMPTY_PROJECT) {
            testMyIntention(
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
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testNestedRepositoriesNotHighlighted(gradleVersion: GradleVersion) {
        runTest(gradleVersion, EMPTY_PROJECT_WITH_PUBLISHING_PLUGIN) {
            testHighlighting(
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

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testMultipleRepositoriesBlocks(gradleVersion: GradleVersion) {
        assumeThatDependencyResolutionManagementIsSupported(gradleVersion)
        runTest(gradleVersion, KOTLIN_DSL_EMPTY_PROJECT) {
            testHighlighting(
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
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testDependencyResolutionManagementAfterPluginManagement(gradleVersion: GradleVersion) {
        assumeThatDependencyResolutionManagementIsSupported(gradleVersion)
        runTest(gradleVersion, KOTLIN_DSL_EMPTY_PROJECT) {
            testMyIntention(
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
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testDependencyResolutionManagementAfterPluginsBlock(gradleVersion: GradleVersion) {
        assumeThatDependencyResolutionManagementIsSupported(gradleVersion)
        runTest(gradleVersion, KOTLIN_DSL_EMPTY_PROJECT) {
            testMyIntention(
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
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testDependencyResolutionManagementAfterBothPluginManagementAndPlugins(gradleVersion: GradleVersion) {
        assumeThatDependencyResolutionManagementIsSupported(gradleVersion)
        runTest(gradleVersion, KOTLIN_DSL_EMPTY_PROJECT) {
            testMyIntention(
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
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testPluginManagementOrderingWithBuildscriptRepositories(gradleVersion: GradleVersion) {
        runTest(gradleVersion, KOTLIN_DSL_EMPTY_PROJECT) {
            testMyIntention(
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

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testCorrectOrderingWithAllTopLevelBlocks(gradleVersion: GradleVersion) {
        assumeThatDependencyResolutionManagementIsSupported(gradleVersion)
        runTest(gradleVersion, KOTLIN_DSL_EMPTY_PROJECT) {
            testMyIntention(
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
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testEmptySettingsFile(gradleVersion: GradleVersion) {
        assumeThatDependencyResolutionManagementIsSupported(gradleVersion)
        runTest(gradleVersion, KOTLIN_DSL_EMPTY_PROJECT) {
            testMyIntention(
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
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testBuildCacheDoesNotAffectOrdering(gradleVersion: GradleVersion) {
        assumeThatDependencyResolutionManagementIsSupported(gradleVersion)
        runTest(gradleVersion, KOTLIN_DSL_EMPTY_PROJECT) {
            testMyIntention(
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

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testMoveRepositoriesWithoutSettingsFile(gradleVersion: GradleVersion) {
        assumeThatDependencyResolutionManagementIsSupported(gradleVersion)
        runTest(gradleVersion, EMPTY_PROJECT_WITH_ONLY_BUILD_FILE) {
            testHighlighting(
                """
                <weak_warning>repositories</weak_warning> {
                    mavenCentral()
                }
                """.trimIndent()
            )
            testMyIntention(
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

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testMoveRepositoriesInBuildscriptWithoutSettingsFile(gradleVersion: GradleVersion) {
        runTest(gradleVersion, EMPTY_PROJECT_WITH_ONLY_BUILD_FILE) {
            testHighlighting(
                """
                buildscript {
                    <weak_warning>repositories</weak_warning> { 
                        mavenCentral()
                    }
                }
                """.trimIndent()
            )
            testMyIntention(
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

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testNoQuickFixWithGroovySettingsFile(gradleVersion: GradleVersion) {
        assumeThatDependencyResolutionManagementIsSupported(gradleVersion)
        runTest(gradleVersion, EMPTY_PROJECT_WITH_GROOVY_SETTINGS_FILE) {
            testHighlighting("<weak_warning>repositories</weak_warning> { mavenCentral() }")
            testNoIntentions(
                "repositories<caret> { mavenCentral() }",
                "Move repositories to the Gradle settings file"
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testSubprojectCanFindSettingsFile(gradleVersion: GradleVersion) {
        runTest(gradleVersion, EMPTY_MULTI_MODULE_PROJECT) {
            testHighlighting(
                "subproject/build.gradle.kts",
                """
                buildscript {
                    <weak_warning>repositories</weak_warning> { mavenCentral() }
                }
                """.trimIndent()
            )
            testMyIntention(
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
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testSubprojectOutsideRootCanFindSettingsFile(gradleVersion: GradleVersion) {
        runTest(gradleVersion, EMPTY_MULTI_MODULE_PROJECT_WITH_SUBPROJECT_OUTSIDE_ROOT) {
            testHighlighting(
                "../subproject/build.gradle.kts",
                """
                buildscript {
                    <weak_warning>repositories</weak_warning> { mavenCentral() }
                }
                """.trimIndent()
            )
            testMyIntention(
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
                project(":subproject").projectDir = file("../subproject")
                """.trimIndent(),
                """
                pluginManagement {
                    repositories {
                        mavenCentral()
                    }
                }
                
                include("subproject")
                project(":subproject").projectDir = file("../subproject")
                """.trimIndent(),
                isForPlugins = true,
                relativeBuildFilePath = "../subproject/build.gradle.kts"
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testIncludedBuildCanFindSettingsFile(gradleVersion: GradleVersion) {
        runTest(gradleVersion, COMPOSITE_PROJECT_WITH_SETTINGS_FILES) {
            testHighlighting(
                "included/build.gradle.kts",
                """
                buildscript {
                    <weak_warning>repositories</weak_warning> { 
                        mavenCentral()
                    }
                }
                """.trimIndent()
            )
            testMyIntention(
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
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testSubprojectInIncludedBuildCanFindSettingsFile(gradleVersion: GradleVersion) {
        runTest(gradleVersion, COMPOSITE_PROJECT_WITH_SUBPROJECT) {
            testHighlighting(
                "included/subproject/build.gradle.kts",
                """
                buildscript {
                    <weak_warning>repositories</weak_warning> { 
                        mavenCentral()
                    }
                }
                """.trimIndent()
            )
            testMyIntention(
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

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testIncludedBuildCreatesSettingsFileInItsOwnRoot(gradleVersion: GradleVersion) {
        runTest(gradleVersion, COMPOSITE_PROJECT_WITHOUT_INCLUDED_SETTINGS_FILE) {
            testHighlighting(
                "included/build.gradle.kts",
                """
                buildscript {
                    <weak_warning>repositories</weak_warning> { 
                        mavenCentral()
                    }
                }
                """.trimIndent()
            )
            testMyIntention(
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


    /**
     * tests intention effect on build.gradle.kts and settings.gradle.kts files
     * @param settingsBefore if null, then no settings.gradle.kts file will exist in the project before test
     */
    private fun testMyIntention(
        buildBefore: String, buildAfter: String,
        settingsBefore: String?, settingsAfter: String,
        isForPlugins: Boolean,
        relativeBuildFilePath: String = GradleConstants.KOTLIN_DSL_SCRIPT_NAME,
        relativeSettingsFilePath: String = GradleConstants.KOTLIN_DSL_SETTINGS_FILE_NAME
    ) {
        checkCaret(buildBefore)
        writeTextAndCommit(relativeBuildFilePath, buildBefore)
        if (settingsBefore != null) writeTextAndCommit(relativeSettingsFilePath, settingsBefore)
        else gradleFixture.fileFixture.snapshot(relativeSettingsFilePath)
        runInEdtAndWait {
            codeInsightFixture.configureFromExistingVirtualFile(getFile(relativeBuildFilePath))
            val repositoriesParentBlockInSettingsName = if (isForPlugins) "pluginManagement" else "dependencyResolutionManagement"
            val intentionName =
                if (settingsBefore != null) "Move repositories to the '$repositoriesParentBlockInSettingsName' block in the 'settings.gradle.kts' file"
                else "Create a 'settings.gradle.kts' file and move repositories to the '$repositoriesParentBlockInSettingsName' block"
            val intention = codeInsightFixture.findSingleIntention(intentionName)
            codeInsightFixture.launchAction(intention)
            codeInsightFixture.checkResult(buildAfter)
            codeInsightFixture.configureFromExistingVirtualFile(getFile(relativeSettingsFilePath))
            codeInsightFixture.checkResult(settingsAfter)
            gradleFixture.fileFixture.rollback(relativeBuildFilePath)
            gradleFixture.fileFixture.rollback(relativeSettingsFilePath)
        }
    }

    companion object {
        private val EMPTY_PROJECT_WITH_PUBLISHING_PLUGIN =
            GradleTestFixtureBuilder.create("empty-project-with-publishing-plugin") { gradleVersion ->
                withSettingsFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
                    setProjectName("empty-project-with-publishing-plugin")
                }
                withBuildFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
                    withPlugin("publishing")
                }
            }

        private val EMPTY_PROJECT_WITH_ONLY_BUILD_FILE =
            GradleTestFixtureBuilder.create("empty-project-with-only-build-file") { gradleVersion ->
                withBuildFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {}
            }

        private val EMPTY_PROJECT_WITH_GROOVY_SETTINGS_FILE =
            GradleTestFixtureBuilder.create("empty-project-with-groovy-settings-file") { gradleVersion ->
                withSettingsFile(gradleVersion, gradleDsl = GradleDsl.GROOVY) {
                    setProjectName("empty-project-with-groovy-settings-file")
                }
                withBuildFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {}
            }

        private val EMPTY_MULTI_MODULE_PROJECT =
            GradleTestFixtureBuilder.create("empty-multi-module-project") { gradleVersion ->
                withSettingsFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
                    setProjectName("empty-multi-module-project")
                    include("subproject")
                }
                withBuildFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {}
                withBuildFile(gradleVersion, relativeModulePath = "subproject", gradleDsl = GradleDsl.KOTLIN) {}
            }

        private val EMPTY_MULTI_MODULE_PROJECT_WITH_SUBPROJECT_OUTSIDE_ROOT =
            GradleTestFixtureBuilder.create("empty-multi-module-project-with-subproject-outside-root") { gradleVersion ->
                withSettingsFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
                    setProjectName("empty-multi-module-project-with-subproject-outside-root")
                    include("subproject")
                    setProjectDir(":subproject", "../subproject")
                }
                withBuildFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {}
                withBuildFile(gradleVersion, relativeModulePath = "../subproject", gradleDsl = GradleDsl.KOTLIN) {}
            }

        private val COMPOSITE_PROJECT_WITH_SETTINGS_FILES =
            GradleTestFixtureBuilder.create("composite-project-with-settings-files") { gradleVersion ->
                withSettingsFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
                    setProjectName("composite-project-with-settings-files")
                    includeBuild("included")
                }
                withBuildFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {}
                withSettingsFile(gradleVersion, relativeModulePath = "included", gradleDsl = GradleDsl.KOTLIN) {
                    setProjectName("included")
                }
                withBuildFile(gradleVersion, relativeModulePath = "included", gradleDsl = GradleDsl.KOTLIN) {}
            }

        private val COMPOSITE_PROJECT_WITH_SUBPROJECT =
            GradleTestFixtureBuilder.create("composite-project-with-subproject") { gradleVersion ->
                withSettingsFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
                    setProjectName("composite-project-with-subproject")
                    includeBuild("included")
                }
                withBuildFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {}
                withSettingsFile(gradleVersion, relativeModulePath = "included", gradleDsl = GradleDsl.KOTLIN) {
                    setProjectName("included")
                    include("subproject")
                }
                withBuildFile(gradleVersion, relativeModulePath = "included/subproject", gradleDsl = GradleDsl.KOTLIN) {}
            }

        private val COMPOSITE_PROJECT_WITHOUT_INCLUDED_SETTINGS_FILE =
            GradleTestFixtureBuilder.create("composite-project-without-included-settings-file") { gradleVersion ->
                withSettingsFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
                    setProjectName("composite-project-without-included-settings-file")
                    includeBuild("included")
                }
                withBuildFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {}
                withBuildFile(gradleVersion, relativeModulePath = "included", gradleDsl = GradleDsl.KOTLIN) {}
            }

        private fun repositoriesModeText(gradleVersion: GradleVersion, mode: String) =
            if (gradleVersion >= GradleVersion.version("8.2")) "repositoriesMode = RepositoriesMode.$mode"
            else "repositoriesMode.set(RepositoriesMode.$mode)"

        private fun assumeThatDependencyResolutionManagementIsSupported(gradleVersion: GradleVersion) {
            assumeThatGradleIsAtLeast(gradleVersion, "6.8") { "dependencyResolutionManagement was added in Gradle 6.8" }
        }
    }
}