// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.codeInsight.kotlin.backend.tests.integration.inspections

import com.intellij.gradle.codeInsight.backend.inspections.declarations.GradleAvoidApplyPluginMethodInspection
import com.intellij.gradle.codeInsight.kotlin.testFramework.checkGradleScriptHighlighting
import com.intellij.gradle.codeInsight.kotlin.testFramework.checkGradleScriptIntention
import com.intellij.gradle.codeInsight.kotlin.testFramework.checkNoGradleScriptIntentions
import com.intellij.gradle.codeInsight.kotlin.testFramework.fixtures.simpleGradleCodeInsightFixture
import com.intellij.gradle.testFramework.annotations.AllGradleVersionsSource
import com.intellij.gradle.testFramework.annotations.BaseGradleVersionSource
import com.intellij.gradle.testFramework.annotations.TargetVersions
import com.intellij.gradle.testFramework.fixtures.application.GradleImmutableProjectTestApplication
import com.intellij.gradle.testFramework.util.KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.FieldSource
import org.junit.jupiter.params.provider.ValueSource

@GradleImmutableProjectTestApplication
class KotlinAvoidApplyPluginMethodInspectionTest {

  @AfterEach
  fun tearDown() {
    KotlinSdkType.removeKotlinSdkInTests()
  }

  @Nested
  @ParameterizedClass
  @AllGradleVersionsSource
  @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
  inner class AllGradleVersions(gradleVersion: GradleVersion) {

    private val codeInsightFixture by simpleGradleCodeInsightFixture(gradleVersion)

    @BeforeEach
    fun enableInspection() {
      codeInsightFixture.enableInspections(GradleAvoidApplyPluginMethodInspection::class.java)
    }

    @Test
    fun testSimple() {
      codeInsightFixture.checkGradleScriptHighlighting("<weak_warning>apply(plugin = \"org.hi.mark\")</weak_warning>")
    }

    @Test
    fun testNoQuickFix() {
      codeInsightFixture.checkNoGradleScriptIntentions("apply(plugin = \"org.hi.mark\")<caret>", "Use the ‘plugins’ block")
    }

    @Test
    fun testCorePlugin() {
      codeInsightFixture.checkGradleScriptHighlighting("<weak_warning>apply(plugin = \"java\")</weak_warning>")
      codeInsightFixture.checkGradleScriptIntention(
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

    @Test
    fun testCorePluginWithExistingPluginsBlock() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        plugins {
            id("org.some.other.plugin") version "1.0"
        }
        
        <weak_warning>apply(plugin = "java")</weak_warning>
        """.trimIndent()
      )
      codeInsightFixture.checkGradleScriptIntention(
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

    @Test
    fun testExternalPluginNoPluginsBlock() {
      codeInsightFixture.checkGradleScriptHighlighting(
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
      codeInsightFixture.checkGradleScriptIntention(
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

    @Test
    fun testExternalPluginPluginsBlockExists() {
      codeInsightFixture.checkGradleScriptHighlighting(
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
      codeInsightFixture.checkGradleScriptIntention(
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

    @Test
    fun testExternalPluginOnlyDependenciesBlockRemoved() {
      codeInsightFixture.checkGradleScriptHighlighting(
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
      codeInsightFixture.checkGradleScriptIntention(
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

    @Test
    fun testExternalPluginMultipleDependencies() {
      codeInsightFixture.checkGradleScriptHighlighting(
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
      codeInsightFixture.checkGradleScriptIntention(
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

    @Test
    fun testNoIntentionWithMultipleRepositories() {
      codeInsightFixture.checkGradleScriptHighlighting(
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
      codeInsightFixture.checkNoGradleScriptIntentions(
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

    @Test
    fun testNoIntentionWithoutBuildscript() {
      codeInsightFixture.checkGradleScriptHighlighting("<weak_warning>apply(plugin = \"org.real.plugin\")</weak_warning>")
      codeInsightFixture.checkNoGradleScriptIntentions("apply(plugin = \"org.real.plugin\")<caret>", "Use the ‘plugins’ block")
    }

    @Test
    fun testNoIntentionWithoutMatchingClasspath() {
      codeInsightFixture.checkGradleScriptHighlighting(
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
      codeInsightFixture.checkNoGradleScriptIntentions(
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

    @Test
    fun testNoIntentionWithoutRepositoriesBlock() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        buildscript {
            dependencies {
                classpath("org.real.plugin:org.real.plugin.gradle.plugin:1.0")
            }
        }
        <weak_warning>apply(plugin = "org.real.plugin")</weak_warning>
        """.trimIndent()
      )
      codeInsightFixture.checkNoGradleScriptIntentions(
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

    @Test
    fun testNoIntentionWithWrongRepository() {
      codeInsightFixture.checkGradleScriptHighlighting(
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
      codeInsightFixture.checkNoGradleScriptIntentions(
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

    @Test
    fun testValPluginName() {
      codeInsightFixture.checkGradleScriptHighlighting(
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
      codeInsightFixture.checkGradleScriptIntention(
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

    @Test
    fun testValClasspath() {
      codeInsightFixture.checkGradleScriptHighlighting(
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
      codeInsightFixture.checkGradleScriptIntention(
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

    @Test
    fun testValClasspathGroup() {
      codeInsightFixture.checkGradleScriptHighlighting(
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
      codeInsightFixture.checkGradleScriptIntention(
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

    @Test
    fun testValClasspathVersion() {
      codeInsightFixture.checkGradleScriptHighlighting(
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
      codeInsightFixture.checkGradleScriptIntention(
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

    @Test
    fun testApplyNotOnTopLevel() {
      codeInsightFixture.checkGradleScriptHighlighting("if (true) <weak_warning>apply(plugin = \"java\")</weak_warning>")
      codeInsightFixture.checkNoGradleScriptIntentions("if (true) apply(plugin = \"java\")<caret>", "Use the ‘plugins’ block")
    }

    @Test
    fun testCoreDuplicateIdInPlugins() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        plugins {
            id("java")
        }
        
        <weak_warning>apply(plugin = "java")</weak_warning>
        """.trimIndent()
      )
      codeInsightFixture.checkGradleScriptIntention(
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

    @Test
    fun testCoreDuplicateBareInPlugins() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        plugins {
            java
        }
        
        <weak_warning>apply(plugin = "java")</weak_warning>
        """.trimIndent()
      )
      codeInsightFixture.checkGradleScriptIntention(
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

    @Test
    fun testCoreDuplicateBackTicksInPlugins() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        plugins {
            `java`
        }
        
        <weak_warning>apply(plugin = "java")</weak_warning>
        """.trimIndent()
      )
      codeInsightFixture.checkGradleScriptIntention(
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

    @Test
    fun testDuplicateKotlinInPlugins() {
      codeInsightFixture.checkGradleScriptHighlighting(
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
      codeInsightFixture.checkGradleScriptIntention(
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

    @Test
    fun testCoreDuplicateIdInPluginsVals() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        val java1 = "java"
        plugins {
            id(java1)
        }
        
        val java2 = "java"
        <weak_warning>apply(plugin = java2)</weak_warning>
        """.trimIndent()
      )
      codeInsightFixture.checkGradleScriptIntention(
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

    @Test
    fun testCoreDifferentIdInPluginsVals() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        val java1 = "java-library"
        plugins {
            id(java1)
        }
        
        val java2 = "java"
        <weak_warning>apply(plugin = java2)</weak_warning>
        """.trimIndent()
      )
      codeInsightFixture.checkGradleScriptIntention(
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

    @ParameterizedTest
    @ValueSource(strings = ["allprojects", "subprojects"])
    fun testApplyInsideBlock(blockName: String) {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        $blockName {
            <weak_warning>apply(plugin = "java")</weak_warning>
        }
        """.trimIndent()
      )
      codeInsightFixture.checkNoGradleScriptIntentions(
        """
        $blockName {
            apply(plugin = "java")<caret>
        }
        """.trimIndent(),
        "Use the ‘plugins’ block"
      )
    }
  }

  @Nested
  @ParameterizedClass
  @BaseGradleVersionSource
  inner class BaseGradleVersion(gradleVersion: GradleVersion) {

    private val codeInsightFixture by simpleGradleCodeInsightFixture(gradleVersion)

    @BeforeEach
    fun enableInspection() {
      codeInsightFixture.enableInspections(GradleAvoidApplyPluginMethodInspection::class.java)
    }

    @ParameterizedTest
    @FieldSource("org.jetbrains.plugins.gradle.util.GradleConstants#GRADLE_CORE_PLUGIN_SHORT_NAMES")
    fun testAllCorePlugins(pluginName: String) {
      codeInsightFixture.checkGradleScriptHighlighting("<weak_warning>apply(plugin = \"$pluginName\")</weak_warning>")
      codeInsightFixture.checkGradleScriptIntention(
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
}
