// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.inspections

import com.intellij.testFramework.runInEdtAndWait
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.scripting.k2.K2GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.codeInspection.AvoidRepositoriesInBuildGradleInspection
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
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
        test(gradleVersion, projectFixture) {
            codeInsightFixture.enableInspections(AvoidRepositoriesInBuildGradleInspection::class.java)
            test()
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testRepositoriesInBuildGradleHighlighted(gradleVersion: GradleVersion) {
        runTest(gradleVersion, EMPTY_PROJECT_WITH_BUILD_FILE) {
            testHighlighting(
                """
                <weak_warning>repositories {
                    mavenCentral()
                }</weak_warning>
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testRepositoriesInBuildscriptHighlighted(gradleVersion: GradleVersion) {
        runTest(gradleVersion, EMPTY_PROJECT_WITH_BUILD_FILE) {
            testHighlighting(
                """
                buildscript {
                    <weak_warning>repositories {
                        gradlePluginPortal()
                    }</weak_warning>
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testRepositoriesMoveToSettingsFile(gradleVersion: GradleVersion) {
        runTest(gradleVersion, EMPTY_PROJECT_WITH_BUILD_FILE) {
            testMyIntention(
                """
                repositories {
                    mavenCentral()
                    gradlePluginPortal()
                }<caret>
                """.trimIndent(),
                "",
                """
                rootProject.name = "test-project"
                """.trimIndent(),
                """
                dependencyResolutionManagement {
                    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
                    repositories {
                        mavenCentral()
                        gradlePluginPortal()
                    }
                }
                
                rootProject.name = "test-project"
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testRepositoriesMoveFromBuildscriptToSettingsFile(gradleVersion: GradleVersion) {
        runTest(gradleVersion, EMPTY_PROJECT_WITH_BUILD_FILE) {
            testMyIntention(
                """
                buildscript {
                    repositories {
                        gradlePluginPortal()
                        mavenCentral()
                    }<caret>
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
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testRepositoriesMoveToExistingDependencyResolutionManagement(gradleVersion: GradleVersion) {
        runTest(gradleVersion, EMPTY_PROJECT_WITH_BUILD_FILE) {
            testMyIntention(
                """
                repositories {
                    mavenCentral()
                }<caret>
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
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testRepositoriesMoveToExistingPluginManagement(gradleVersion: GradleVersion) {
        runTest(gradleVersion, EMPTY_PROJECT_WITH_BUILD_FILE) {
            testMyIntention(
                """
                buildscript {
                    repositories {
                        gradlePluginPortal()
                    }<caret>
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
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testRepositoriesWithMultipleStatements(gradleVersion: GradleVersion) {
        runTest(gradleVersion, EMPTY_PROJECT_WITH_BUILD_FILE) {
            testHighlighting(
                """
                <weak_warning>repositories {
                    mavenCentral()
                    gradlePluginPortal()
                    maven {
                        url = uri("https://repo.spring.io/release")
                    }
                }</weak_warning>
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testMultipleRepositoriesWithComplexContent(gradleVersion: GradleVersion) {
        runTest(gradleVersion, EMPTY_PROJECT_WITH_BUILD_FILE) {
            testMyIntention(
                """
                repositories {
                    mavenCentral()
                    maven {
                        url = uri("https://repo.spring.io/release")
                    }
                }<caret>
                """.trimIndent(),
                "",
                """
                rootProject.name = "test-project"
                """.trimIndent(),
                """
                dependencyResolutionManagement {
                    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
                    repositories {
                        mavenCentral()
                        maven {
                            url = uri("https://repo.spring.io/release")
                        }
                    }
                }
                
                rootProject.name = "test-project"
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testNestedRepositoriesNotHighlighted(gradleVersion: GradleVersion) {
        runTest(gradleVersion, EMPTY_PROJECT_WITH_BUILD_FILE) {
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
    @BaseGradleVersionSource
    fun testMultipleRepositoriesBlocks(gradleVersion: GradleVersion) {
        runTest(gradleVersion, EMPTY_PROJECT_WITH_BUILD_FILE) {
            testHighlighting(
                """
                <weak_warning>repositories {
                    mavenCentral()
                }</weak_warning>
                
                dependencies {
                    implementation("org.example:lib:1.0")
                }
                
                <weak_warning>repositories {
                    gradlePluginPortal()
                }</weak_warning>
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testDependencyResolutionManagementAfterPluginManagement(gradleVersion: GradleVersion) {
        runTest(gradleVersion, EMPTY_PROJECT_WITH_BUILD_FILE) {
            testMyIntention(
                """
                repositories {
                    mavenCentral()
                }<caret>
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
                    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
                    repositories {
                        mavenCentral()
                    }
                }

                rootProject.name = "test-project"
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testDependencyResolutionManagementAfterPluginsBlock(gradleVersion: GradleVersion) {
        runTest(gradleVersion, EMPTY_PROJECT_WITH_BUILD_FILE) {
            testMyIntention(
                """
                repositories {
                    maven {
                        url = uri("https://jitpack.io")
                    }
                }<caret>
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
                    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
                    repositories {
                        maven {
                            url = uri("https://jitpack.io")
                        }
                    }
                }

                rootProject.name = "test-project"
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testDependencyResolutionManagementAfterBothPluginManagementAndPlugins(gradleVersion: GradleVersion) {
        runTest(gradleVersion, EMPTY_PROJECT_WITH_BUILD_FILE) {
            testMyIntention(
                """
                repositories {
                    mavenCentral()
                    maven {
                        url = uri("https://repo.spring.io/milestone")
                    }
                }<caret>
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
                    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
                    repositories {
                        mavenCentral()
                        maven {
                            url = uri("https://repo.spring.io/milestone")
                        }
                    }
                }

                rootProject.name = "test-project"
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testPluginManagementOrderingWithBuildscriptRepositories(gradleVersion: GradleVersion) {
        runTest(gradleVersion, EMPTY_PROJECT_WITH_BUILD_FILE) {
            testMyIntention(
                """
                buildscript {
                    repositories {
                        gradlePluginPortal()
                        maven {
                            url = uri("https://plugins.gradle.org/m2/")
                        }
                    }<caret>
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
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testCorrectOrderingWithAllTopLevelBlocks(gradleVersion: GradleVersion) {
        runTest(gradleVersion, EMPTY_PROJECT_WITH_BUILD_FILE) {
            testMyIntention(
                """
                repositories {
                    mavenCentral()
                    mavenLocal()
                }<caret>
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
                    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
                    repositories {
                        mavenCentral()
                        mavenLocal()
                    }
                }

                rootProject.name = "test-project"

                include("subproject1", "subproject2")
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testEmptySettingsFileGetsCorrectOrder(gradleVersion: GradleVersion) {
        runTest(gradleVersion, EMPTY_PROJECT_WITH_BUILD_FILE) {
            testMyIntention(
                """
                repositories {
                    google()
                    mavenCentral()
                }<caret>
                """.trimIndent(),
                "",
                "",
                """
                dependencyResolutionManagement {
                    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
                    repositories {
                        google()
                        mavenCentral()
                    }
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testBuildCacheDoesNotAffectOrdering(gradleVersion: GradleVersion) {
        runTest(gradleVersion, EMPTY_PROJECT_WITH_BUILD_FILE) {
            testMyIntention(
                """
                repositories {
                    mavenCentral()
                }<caret>
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
                    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
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
                """.trimIndent()
            )
        }
    }

    // A project without a settings file does not need to centralize its repositories
    @ParameterizedTest
    @BaseGradleVersionSource
    fun testNoInspectionWithoutSettingsFile(gradleVersion: GradleVersion) {
        runTest(gradleVersion, EMPTY_PROJECT_ONLY_BUILD_FILE) {
            testHighlighting("repositories { mavenCentral() }")
            testNoIntentions(
                "repositories { mavenCentral() }<caret>",
                "Move repositories to the Gradle settings file"
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testNoQuickFixWithGroovySettingsFile(gradleVersion: GradleVersion) {
        runTest(gradleVersion, EMPTY_PROJECT_WITH_GROOVY_BUILD_FILE) {
            testHighlighting("<weak_warning>repositories { mavenCentral() }</weak_warning>")
            testNoIntentions(
                "repositories { mavenCentral() }<caret>",
                "Move repositories to the Gradle settings file"
            )
        }
    }

    /**
     * tests intention effect on build.gradle.kts and settings.gradle.kts files
     * @param settingsBefore if null, then no settings.gradle.kts file will exist in the project before test
     */
    private fun testMyIntention(buildBefore: String, buildAfter: String, settingsBefore: String?, settingsAfter: String) {
        checkCaret(buildBefore)
        writeTextAndCommit(GradleConstants.KOTLIN_DSL_SCRIPT_NAME, buildBefore)
        if (settingsBefore != null) writeTextAndCommit(GradleConstants.KOTLIN_DSL_SETTINGS_FILE_NAME, settingsBefore)
        runInEdtAndWait {
            codeInsightFixture.configureFromExistingVirtualFile(getFile(GradleConstants.KOTLIN_DSL_SCRIPT_NAME))
            val intention = codeInsightFixture.findSingleIntention("Move repositories to the Gradle settings file")
            codeInsightFixture.launchAction(intention)
            codeInsightFixture.checkResult(buildAfter, false)
            codeInsightFixture.configureFromExistingVirtualFile(getFile(GradleConstants.KOTLIN_DSL_SETTINGS_FILE_NAME))
            codeInsightFixture.checkResult(settingsAfter, false)
            gradleFixture.fileFixture.rollback(GradleConstants.KOTLIN_DSL_SCRIPT_NAME)
            gradleFixture.fileFixture.rollback(GradleConstants.KOTLIN_DSL_SETTINGS_FILE_NAME)
        }
    }

    companion object {
        private val EMPTY_PROJECT_WITH_BUILD_FILE = GradleTestFixtureBuilder.create("empty-project-with-build-file") { gradleVersion ->
            withSettingsFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
                setProjectName("empty-project-with-build-file")
            }
            withBuildFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {}
        }

        private val EMPTY_PROJECT_ONLY_BUILD_FILE = GradleTestFixtureBuilder.create("empty-project-only-build-file") { gradleVersion ->
            withBuildFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {}
        }

        private val EMPTY_PROJECT_WITH_GROOVY_BUILD_FILE =
            GradleTestFixtureBuilder.create("empty-project-with-groovy-settings-file") { gradleVersion ->
                withSettingsFile(gradleVersion, gradleDsl = GradleDsl.GROOVY) {
                    setProjectName("empty-project-with-groovy-settings-file")
                }
                withBuildFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {}
            }
    }
}