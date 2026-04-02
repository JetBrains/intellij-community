// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.inspections

import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.K2GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.codeInspection.GradleAvoidApplyPluginMethodInspection
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.testFramework.util.assumeThatKotlinDslScriptsModelImportIsSupported
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
        assumeThatKotlinDslScriptsModelImportIsSupported(gradleVersion)
        testKotlinDslEmptyProject(gradleVersion) {
            codeInsightFixture.enableInspections(GradleAvoidApplyPluginMethodInspection::class.java)
            test()
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testSimple(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting("<weak_warning>apply(plugin = \"org.hi.mark\")</weak_warning>")
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testNoQuickFix(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testNoIntentions("apply(plugin = \"org.hi.mark\")<caret>", "Use the ‘plugins’ block")
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
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
                "Use the ‘plugins’ block"
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
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
                "Use the ‘plugins’ block"
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
                "Use the ‘plugins’ block"
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
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
                "Use the ‘plugins’ block"
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
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
                "Use the ‘plugins’ block"
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
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
                    val a = 5
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
                    val a = 5
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
                
                    val a = 5
                }
                
                """.trimIndent(),
                "Use the ‘plugins’ block"
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
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
                "Use the ‘plugins’ block"
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
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
                "Use the ‘plugins’ block"
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testNoIntentionWithoutBuildscript(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting("<weak_warning>apply(plugin = \"org.real.plugin\")</weak_warning>")
            testNoIntentions("apply(plugin = \"org.real.plugin\")<caret>", "Use the ‘plugins’ block")
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
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
                "Use the ‘plugins’ block"
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
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
                "Use the ‘plugins’ block"
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
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
                "Use the ‘plugins’ block"
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testValPluginName(gradleVersion: GradleVersion) {
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
                val pluginName = "org.real.plugin"
                <weak_warning>apply(plugin = pluginName)</weak_warning>
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
                val pluginName = "org.real.plugin"
                apply(plugin = pluginName)<caret>
                """.trimIndent(),
                """
                plugins {
                    id("org.real.plugin") version "1.0"
                }
                
                val pluginName = "org.real.plugin"
                
                """.trimIndent(),
                "Use the ‘plugins’ block"
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testValClasspath(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                """
                buildscript {
                    repositories {
                        gradlePluginPortal()
                    }
                    
                    val classpathString = "org.real.plugin:org.real.plugin.gradle.plugin:1.0"
                    dependencies {
                        classpath(classpathString)
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
                    
                    val classpathString = "org.real.plugin:org.real.plugin.gradle.plugin:1.0"
                    dependencies {
                        classpath(classpathString)
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
                    
                    val classpathString = "org.real.plugin:org.real.plugin.gradle.plugin:1.0"
                }
                
                """.trimIndent(),
                "Use the ‘plugins’ block"
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testValClasspathGroup(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                $$"""
                buildscript {
                    repositories {
                        gradlePluginPortal()
                    }
                    
                    val group = "org.real.plugin"
                    dependencies {
                        classpath("$group:$group.gradle.plugin:1.0")
                    }
                }
                <weak_warning>apply(plugin = "org.real.plugin")</weak_warning>
                """.trimIndent()
            )
            testIntention(
                $$"""
                buildscript {
                    repositories {
                        gradlePluginPortal()
                    }
                    
                    val group = "org.real.plugin"
                    dependencies {
                        classpath("$group:$group.gradle.plugin:1.0")
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
                    
                    val group = "org.real.plugin"
                }
                
                """.trimIndent(),
                "Use the ‘plugins’ block"
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testValClasspathVersion(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                $$"""
                buildscript {
                    repositories {
                        gradlePluginPortal()
                    }
                    
                    val version = "1.0"
                    dependencies {
                        classpath("org.real.plugin:org.real.plugin.gradle.plugin:$version")
                    }
                }
                <weak_warning>apply(plugin = "org.real.plugin")</weak_warning>
                """.trimIndent()
            )
            testIntention(
                $$"""
                buildscript {
                    repositories {
                        gradlePluginPortal()
                    }
                    
                    val version = "1.0"
                    dependencies {
                        classpath("org.real.plugin:org.real.plugin.gradle.plugin:$version")
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
                    
                    val version = "1.0"
                }
                
                """.trimIndent(),
                "Use the ‘plugins’ block"
            )
        }
    }


    @ParameterizedTest
    @AllGradleVersionsSource("allprojects,subprojects")
    fun testApplyInsideBlock(gradleVersion: GradleVersion, blockName: String) {
        runTest(gradleVersion) {
            testHighlighting(
                """
                $blockName {
                    <weak_warning>apply(plugin = "java")</weak_warning>
                }
                """.trimIndent()
            )
            testNoIntentions(
                """
                $blockName {
                    apply(plugin = "java")<caret>
                }
                """.trimIndent(),
                "Use the ‘plugins’ block"
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testApplyNotOnTopLevel(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting("if (true) <weak_warning>apply(plugin = \"java\")</weak_warning>")
            testNoIntentions("if (true) apply(plugin = \"java\")<caret>", "Use the ‘plugins’ block")
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testCoreDuplicateIdInPlugins(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                """
                plugins {
                    id("java")
                }
                
                <weak_warning>apply(plugin = "java")</weak_warning>
                """.trimIndent()
            )
            testIntention(
                """
                plugins {
                    id("java")
                }
                
                apply(plugin = "java")<caret>
                """.trimIndent(),
                """
                plugins {
                    id("java")
                }
                
                <caret>
                """.trimIndent(),
                "Use the ‘plugins’ block"
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testCoreDuplicateBareInPlugins(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                """
                plugins {
                    java
                }
                
                <weak_warning>apply(plugin = "java")</weak_warning>
                """.trimIndent()
            )
            testIntention(
                """
                plugins {
                    java
                }
                
                apply(plugin = "java")<caret>
                """.trimIndent(),
                """
                plugins {
                    java
                }
                
                <caret>
                """.trimIndent(),
                "Use the ‘plugins’ block"
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testCoreDuplicateBackTicksInPlugins(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                """
                plugins {
                    `java`
                }
                
                <weak_warning>apply(plugin = "java")</weak_warning>
                """.trimIndent()
            )
            testIntention(
                """
                plugins {
                    `java`
                }
                
                apply(plugin = "java")<caret>
                """.trimIndent(),
                """
                plugins {
                    `java`
                }
                
                <caret>
                """.trimIndent(),
                "Use the ‘plugins’ block"
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testDuplicateKotlinInPlugins(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                """
                plugins {
                    kotlin("jvm") version "2.3.0"
                }
                
                buildscript {
                    repositories {
                        gradlePluginPortal()
                    }
                    dependencies {
                        classpath("org.jetbrains.kotlin.jvm:org.jetbrains.kotlin.jvm.gradle.plugin:2.3.0")
                    }
                }
                
                <weak_warning>apply(plugin = "org.jetbrains.kotlin.jvm")</weak_warning>
                """.trimIndent()
            )
            testIntention(
                """
                plugins {
                    kotlin("jvm") version "2.3.0"
                }
                
                buildscript {
                    repositories {
                        gradlePluginPortal()
                    }
                    dependencies {
                        classpath("org.jetbrains.kotlin.jvm:org.jetbrains.kotlin.jvm.gradle.plugin:2.3.0")
                    }
                }
                
                apply(plugin = "org.jetbrains.kotlin.jvm")<caret>
                """.trimIndent(),
                """
                plugins {
                    kotlin("jvm") version "2.3.0"
                }
                
                <caret>
                """.trimIndent(),
                "Use the ‘plugins’ block"
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testCoreDuplicateIdInPluginsVals(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                """
                val java1 = "java"
                plugins {
                    id(java1)
                }
                
                val java2 = "java"
                <weak_warning>apply(plugin = java2)</weak_warning>
                """.trimIndent()
            )
            testIntention(
                """
                val java1 = "java"
                plugins {
                    id(java1)
                }
                
                val java2 = "java"
                apply(plugin = java2)<caret>
                """.trimIndent(),
                """
                val java1 = "java"
                plugins {
                    id(java1)
                }
                
                val java2 = "java"
                <caret>
                """.trimIndent(),
                "Use the ‘plugins’ block"
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testCoreDifferentIdInPluginsVals(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                """
                val java1 = "java-library"
                plugins {
                    id(java1)
                }
                
                val java2 = "java"
                <weak_warning>apply(plugin = java2)</weak_warning>
                """.trimIndent()
            )
            testIntention(
                """
                val java1 = "java-library"
                plugins {
                    id(java1)
                }
                
                val java2 = "java"
                apply(plugin = java2)<caret>
                """.trimIndent(),
                """
                val java1 = "java-library"
                plugins {
                    id(java1)
                    id("java")
                }
                
                val java2 = "java"
                <caret>
                """.trimIndent(),
                "Use the ‘plugins’ block"
            )
        }
    }


    companion object {
        @JvmStatic
        @Suppress("unused") // used by testAllCorePlugins test
        private fun corePluginNamesFactory(): ArgumentSets =
            ArgumentSets.argumentsForFirstParameter(GradleVersion.version(VersionMatcherRule.BASE_GRADLE_VERSION))
                .argumentsForNextParameter(GRADLE_CORE_PLUGIN_SHORT_NAMES)
    }
}