// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.inspections

import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.testFramework.runInEdtAndWait
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.scripting.k2.K2GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.codeInspection.AvoidRepositoriesInBuildGradleInspection
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.testFramework.util.assumeThatGradleIsAtLeast
import org.jetbrains.plugins.gradle.testFramework.util.withBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.withSettingsFile
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.junit.jupiter.params.ParameterizedTest

class KotlinAvoidRepositoriesInBuildGradleInspectionTest : K2GradleCodeInsightTestCase() {

    private fun runTest(
        gradleVersion: GradleVersion,
        projectFixture: GradleTestFixtureBuilder,
        test: () -> Unit
    ) {
        assumeThatGradleIsAtLeast(gradleVersion, "9.0.0") { "Best practice added in Gradle 9.0.0" }
        test(gradleVersion, projectFixture) {
            codeInsightFixture.enableInspections(AvoidRepositoriesInBuildGradleInspection::class.java)
            (codeInsightFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)
            test()
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testRepositoriesInBuildGradleHighlighted(gradleVersion: GradleVersion) {
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
                    repositoriesMode = RepositoriesMode.PREFER_PROJECT
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
                    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
                    repositories {
                        gradlePluginPortal()
                    }
                }
                """.trimIndent(),
                """
                rootProject.name = "test-project"

                dependencyResolutionManagement {
                    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
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
                    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
                    repositories {
                        mavenCentral()
                    }
                }
                """.trimIndent(),
                """
                dependencyResolutionManagement {
                    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
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
                    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
                    repositories {
                        mavenCentral()
                        google()
                    }
                }
                """.trimIndent(),
                """
                dependencyResolutionManagement {
                    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
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
                    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
                    repositories {
                        mavenCentral()
                        google()
                    }
                }
                """.trimIndent(),
                """
                dependencyResolutionManagement {
                    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
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
                    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
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
                    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
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
                    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
                    repositories {
                        mavenCentral()
                        google()
                        myRepo()
                    }
                }
                """.trimIndent(),
                """
                dependencyResolutionManagement {
                    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
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
                    repositoriesMode = RepositoriesMode.PREFER_PROJECT
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
                    repositoriesMode = RepositoriesMode.PREFER_PROJECT
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
                    repositoriesMode = RepositoriesMode.PREFER_PROJECT
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
                    repositoriesMode = RepositoriesMode.PREFER_PROJECT
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
                    repositoriesMode = RepositoriesMode.PREFER_PROJECT
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
                    repositoriesMode = RepositoriesMode.PREFER_PROJECT
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
                    repositoriesMode = RepositoriesMode.PREFER_PROJECT
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
                plugins {
                    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
                }
                rootProject.name = "empty-project-with-only-build-file"
                dependencyResolutionManagement {
                    repositoriesMode = RepositoriesMode.PREFER_PROJECT
                    repositories {
                        mavenCentral()
                    }
                }
                """.trimIndent(),
                false
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
                plugins {
                    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
                }
                rootProject.name = "empty-project-with-only-build-file"
                """.trimIndent(),
                true
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testNoQuickFixWithGroovySettingsFile(gradleVersion: GradleVersion) {
        runTest(gradleVersion, EMPTY_PROJECT_WITH_GROOVY_SETTINGS_FILE) {
            testHighlighting("<weak_warning>repositories</weak_warning> { mavenCentral() }")
            testNoIntentions(
                "repositories<caret> { mavenCentral() }",
                "Move repositories to the Gradle settings file"
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
        isForPlugins: Boolean
    ) {
        checkCaret(buildBefore)
        writeTextAndCommit(GradleConstants.KOTLIN_DSL_SCRIPT_NAME, buildBefore)
        if (settingsBefore != null) writeTextAndCommit(GradleConstants.KOTLIN_DSL_SETTINGS_FILE_NAME, settingsBefore)
        else gradleFixture.fileFixture.snapshot(GradleConstants.KOTLIN_DSL_SETTINGS_FILE_NAME)
        runInEdtAndWait {
            codeInsightFixture.configureFromExistingVirtualFile(getFile(GradleConstants.KOTLIN_DSL_SCRIPT_NAME))
            val repositoriesParentBlockInSettingsName = if (isForPlugins) "pluginManagement" else "dependencyResolutionManagement"
            val intentionName =
                if (settingsBefore != null) "Move repositories to '$repositoriesParentBlockInSettingsName' in the Gradle settings file"
                else "Create a 'settings.gradle.kts' file and move repositories to '$repositoriesParentBlockInSettingsName'"
            val intention = codeInsightFixture.findSingleIntention(intentionName)
            codeInsightFixture.launchAction(intention)
            codeInsightFixture.checkResult(buildAfter)
            codeInsightFixture.configureFromExistingVirtualFile(getFile(GradleConstants.KOTLIN_DSL_SETTINGS_FILE_NAME))
            codeInsightFixture.checkResult(settingsAfter)
            gradleFixture.fileFixture.rollback(GradleConstants.KOTLIN_DSL_SCRIPT_NAME)
            gradleFixture.fileFixture.rollback(GradleConstants.KOTLIN_DSL_SETTINGS_FILE_NAME)
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
    }
}