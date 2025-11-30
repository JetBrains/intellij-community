// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.inspections

import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.scripting.k2.K2GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.codeInspection.GradleAvoidDuplicateRepositoriesInspection
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.testFramework.util.assumeThatDependencyResolutionManagementIsSupported
import org.jetbrains.plugins.gradle.testFramework.util.assumeThatKotlinDslScriptsModelImportIsSupported
import org.junit.jupiter.params.ParameterizedTest

class KotlinAvoidDuplicateRepositoriesInspectionTest : K2GradleCodeInsightTestCase() {

    private fun runTest(
        gradleVersion: GradleVersion,
        test: () -> Unit
    ) {
        assumeThatKotlinDslScriptsModelImportIsSupported(gradleVersion)
        testKotlinDslEmptyProject(gradleVersion) {
            codeInsightFixture.enableInspections(GradleAvoidDuplicateRepositoriesInspection::class.java)
            test()
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
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
    fun `test dependency resolution management repositories`(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            assumeThatDependencyResolutionManagementIsSupported(gradleVersion)
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