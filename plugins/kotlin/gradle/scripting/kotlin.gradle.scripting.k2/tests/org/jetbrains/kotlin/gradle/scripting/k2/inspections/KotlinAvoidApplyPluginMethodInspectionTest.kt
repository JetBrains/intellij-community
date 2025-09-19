// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.inspections

import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.scripting.k2.K2GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.codeInspection.GradleAvoidApplyPluginMethodInspection
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.jetbrains.plugins.gradle.testFramework.util.withBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.withSettingsFile
import org.jetbrains.plugins.gradle.tooling.VersionMatcherRule
import org.jetbrains.plugins.gradle.util.GradleConstants.GRADLE_CORE_PLUGIN_SHORT_NAMES
import org.junit.jupiter.params.ParameterizedTest
import org.junitpioneer.jupiter.cartesian.ArgumentSets
import org.junitpioneer.jupiter.cartesian.CartesianTest

class KotlinAvoidApplyPluginMethodInspectionTest : K2GradleCodeInsightTestCase() {

    private fun runTest(
        gradleVersion: GradleVersion,
        test: () -> Unit
    ) {
        test(gradleVersion, EMPTY_PROJECT_WITH_BUILD_FILE) {
            codeInsightFixture.enableInspections(GradleAvoidApplyPluginMethodInspection::class.java)
            test()
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testSimple(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting("<weak_warning>apply(plugin = \"org.hi.mark\")</weak_warning>")
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testNoQuickFix(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testNoIntentions("apply(plugin = \"org.hi.mark\")<caret>", "Move plugin to the plugins block")
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testCorePlugin(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting("<weak_warning>apply(plugin = \"java\")</weak_warning>")
            testIntention(
                """
                apply(plugin = "java")<caret>
                """.trimIndent(),
                """
                plugins {
                    id("java")
                }
                
                
                """.trimIndent(),
                "Move plugin to the plugins block"
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testCorePluginWithExistingPluginsBlock(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                """
                plugins {
                    id("org.some.other.plugin") version "1.0"
                }
                
                <weak_warning>apply(plugin = "java")</weak_warning>
                """.trimIndent()
            )
            testIntention(
                """
                plugins {
                    id("org.some.other.plugin") version "1.0"
                }
                
                apply(plugin = "java")<caret>
                """.trimIndent(),
                """
                plugins {
                    id("org.some.other.plugin") version "1.0"
                    id("java")
                }
                
                
                """.trimIndent(),
                "Move plugin to the plugins block"
            )
        }
    }

    @CartesianTest
    @CartesianTest.MethodFactory("corePluginNamesFactory")
    fun testAllCorePlugins(gradleVersion: GradleVersion, pluginName: String) {
        runTest(gradleVersion) {
            testHighlighting("<weak_warning>apply(plugin = \"$pluginName\")</weak_warning>")
            testIntention(
                """
                apply(plugin = "$pluginName")<caret>
                """.trimIndent(),
                """
                plugins {
                    id("$pluginName")
                }
                
                
                """.trimIndent(),
                "Move plugin to the plugins block"
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testExternalPluginNoPluginsBlock(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                """
                buildscript {
                    repositories {
                        gradlePluginPortal()
                    }
                    
                    dependencies {
                        classpath("org.real.plugin:org.real.plugin.gradle.plugin:1.0")
                    }
                }
                <weak_warning>apply(plugin = "org.real.plugin")</weak_warning>
                """.trimIndent()
            )
            testIntention(
                """
                buildscript {
                    repositories {
                        gradlePluginPortal()
                    }
                    
                    dependencies {
                        classpath("org.real.plugin:org.real.plugin.gradle.plugin:1.0")
                    }
                }
                apply(plugin = "org.real.plugin")<caret>
                """.trimIndent(),
                """
                plugins {
                    id("org.real.plugin") version "1.0"
                }
                
                
                """.trimIndent(),
                "Move plugin to the plugins block"
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testExternalPluginPluginsBlockExists(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                """
                plugins {
                    id("org.some.other.plugin") version "1.0"
                }
                
                buildscript {
                    repositories {
                        gradlePluginPortal()
                    }
                    
                    dependencies {
                        classpath("org.real.plugin:org.real.plugin.gradle.plugin:1.0")
                    }
                }
                <weak_warning>apply(plugin = "org.real.plugin")</weak_warning>
                """.trimIndent()
            )
            testIntention(
                """
                plugins {
                    id("org.some.other.plugin") version "1.0"
                }
                
                buildscript {
                    repositories {
                        gradlePluginPortal()
                    }
                    
                    dependencies {
                        classpath("org.real.plugin:org.real.plugin.gradle.plugin:1.0")
                    }
                }
                apply(plugin = "org.real.plugin")<caret>
                """.trimIndent(),
                """
                plugins {
                    id("org.some.other.plugin") version "1.0"
                    id("org.real.plugin") version "1.0"
                }
                
                
                """.trimIndent(),
                "Move plugin to the plugins block"
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testExternalPluginOnlyDependenciesBlockRemoved(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                """
                buildscript {
                    repositories {
                        gradlePluginPortal()
                        mavenCentral()
                    }
                    
                    dependencies {
                        classpath("org.real.plugin:org.real.plugin.gradle.plugin:1.0")
                    }
                    def a = 5
                }
                <weak_warning>apply(plugin = "org.real.plugin")</weak_warning>
                """.trimIndent()
            )
            testIntention(
                """
                buildscript {
                    repositories {
                        gradlePluginPortal()
                    }
                    
                    dependencies {
                        classpath("org.real.plugin:org.real.plugin.gradle.plugin:1.0")
                    }
                    def a = 5
                }
                apply(plugin = "org.real.plugin")<caret>
                """.trimIndent(),
                """
                plugins {
                    id("org.real.plugin") version "1.0"
                }
                
                buildscript {
                    repositories {
                        gradlePluginPortal()
                    }
                
                    def a = 5
                }
                
                """.trimIndent(),
                "Move plugin to the plugins block"
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testExternalPluginMultipleDependencies(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                """
                buildscript {
                    repositories {
                        gradlePluginPortal()
                    }
                    
                    dependencies {
                        classpath("org.real.plugin:org.real.plugin.gradle.plugin:1.0")
                        classpath("com.other:other-plugin.gradle.plugin:2.0")
                    }
                }
                <weak_warning>apply(plugin = "org.real.plugin")</weak_warning>
                """.trimIndent()
            )
            testIntention(
                """
                buildscript {
                    repositories {
                        gradlePluginPortal()
                    }
                    
                    dependencies {
                        classpath("org.real.plugin:org.real.plugin.gradle.plugin:1.0")
                        classpath("com.other:other-plugin.gradle.plugin:2.0")
                    }
                }
                apply(plugin = "org.real.plugin")<caret>
                """.trimIndent(),
                """
                plugins {
                    id("org.real.plugin") version "1.0"
                }
                
                buildscript {
                    repositories {
                        gradlePluginPortal()
                    }
                    
                    dependencies {
                        classpath("com.other:other-plugin.gradle.plugin:2.0")
                    }
                }
                
                """.trimIndent(),
                "Move plugin to the plugins block"
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testExternalPluginNamedArgumentsDependency(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                """
                buildscript {
                    repositories {
                        gradlePluginPortal()
                    }
                    
                    dependencies {
                        classpath(group = "org.real.plugin", name = "org.real.plugin.gradle.plugin", version = "1.0")
                    }
                }
                <weak_warning>apply(plugin = "org.real.plugin")</weak_warning>
                """.trimIndent()
            )
            testIntention(
                """
                buildscript {
                    repositories {
                        gradlePluginPortal()
                    }
                    
                    dependencies {
                        classpath(group = "org.real.plugin", name = "org.real.plugin.gradle.plugin", version = "1.0")
                    }
                }
                apply(plugin = "org.real.plugin")<caret>
                """.trimIndent(),
                """
                plugins {
                    id("org.real.plugin") version "1.0"
                }
                
                
                """.trimIndent(),
                "Move plugin to the plugins block"
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testNoIntentionWithMultipleRepositories(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                """
                buildscript {
                    repositories {
                        gradlePluginPortal()
                        mavenCentral()
                    }
                    
                    dependencies {
                        classpath("org.real.plugin:org.real.plugin.gradle.plugin:1.0")
                    }
                }
                <weak_warning>apply(plugin = "org.real.plugin")</weak_warning>
                """.trimIndent()
            )
            testNoIntentions(
                """
                buildscript {
                    repositories {
                        gradlePluginPortal()
                        mavenCentral()
                    }
                    
                    dependencies {
                        classpath("org.real.plugin:org.real.plugin.gradle.plugin:1.0")
                    }
                }
                apply(plugin = "org.real.plugin")<caret>
                """.trimIndent(),
                "Move plugin to the plugins block"
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testNoIntentionWithoutBuildscript(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting("<weak_warning>apply(plugin = \"org.real.plugin\")</weak_warning>")
            testNoIntentions("apply(plugin = \"org.real.plugin\")<caret>", "Move plugin to the plugins block")
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testNoIntentionWithoutMatchingClasspath(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                """
                buildscript {
                    repositories {
                        gradlePluginPortal()
                    }
                    
                    dependencies {
                        classpath("com.other:other-plugin.gradle.plugin:1.0")
                    }
                }
                <weak_warning>apply(plugin = "org.real.plugin")</weak_warning>
                """.trimIndent()
            )
            testNoIntentions(
                """
                buildscript {
                    repositories {
                        gradlePluginPortal()
                    }
                    
                    dependencies {
                        classpath("com.other:other-plugin.gradle.plugin:1.0")
                    }
                }
                apply(plugin = "org.real.plugin")<caret>
                """.trimIndent(),
                "Move plugin to the plugins block"
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testNoIntentionWithoutRepositoriesBlock(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                """
                buildscript {
                    dependencies {
                        classpath("org.real.plugin:org.real.plugin.gradle.plugin:1.0")
                    }
                }
                <weak_warning>apply(plugin = "org.real.plugin")</weak_warning>
                """.trimIndent()
            )
            testNoIntentions(
                """
                buildscript {
                    dependencies {
                        classpath("org.real.plugin:org.real.plugin.gradle.plugin:1.0")
                    }
                }
                apply(plugin = "org.real.plugin")<caret>
                """.trimIndent(),
                "Move plugin to the plugins block"
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testNoIntentionWithWrongRepository(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                """
                buildscript {
                    repositories {
                        mavenCentral()
                    }
                    
                    dependencies {
                        classpath("org.real.plugin:org.real.plugin.gradle.plugin:1.0")
                    }
                }
                <weak_warning>apply(plugin = "org.real.plugin")</weak_warning>
                """.trimIndent()
            )
            testNoIntentions(
                """
                buildscript {
                    repositories {
                        mavenCentral()
                    }
                    
                    dependencies {
                        classpath("org.real.plugin:org.real.plugin.gradle.plugin:1.0")
                    }
                }
                apply(plugin = "org.real.plugin")<caret>
                """.trimIndent(),
                "Move plugin to the plugins block"
            )
        }
    }

    companion object {
        private val EMPTY_PROJECT_WITH_BUILD_FILE = GradleTestFixtureBuilder.create("empty-project-with-build-file") { gradleVersion ->
            withSettingsFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
                setProjectName("empty-project-with-build-file")
            }
            withBuildFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {}
        }

        @JvmStatic
        @Suppress("unused") // used by testAllCorePlugins test
        private fun corePluginNamesFactory(): ArgumentSets =
            ArgumentSets.argumentsForFirstParameter(GradleVersion.version(VersionMatcherRule.BASE_GRADLE_VERSION))
                .argumentsForNextParameter(GRADLE_CORE_PLUGIN_SHORT_NAMES)
    }
}