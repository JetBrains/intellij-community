// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.codeInsight.kotlin.backend.tests.integration.inspections

import com.intellij.gradle.codeInsight.backend.inspections.declarations.GradleRedundantKotlinStdLibInspection
import com.intellij.gradle.codeInsight.kotlin.testFramework.checkGradleScriptHighlighting
import com.intellij.gradle.codeInsight.kotlin.testFramework.checkGradleScriptIntention
import com.intellij.gradle.codeInsight.kotlin.testFramework.disableBuiltInKotlinHighlighters
import com.intellij.gradle.codeInsight.kotlin.testFramework.fixtures.customConfigurationsGradleCodeInsightFixture
import com.intellij.gradle.codeInsight.kotlin.testFramework.isMissingDeprecationAnnotationAddedToKotlinDsl
import com.intellij.gradle.testFramework.annotations.AllGradleVersionsSource
import com.intellij.gradle.testFramework.annotations.TargetVersions
import com.intellij.gradle.testFramework.fixtures.application.GradleImmutableProjectTestApplication
import com.intellij.gradle.testFramework.fixtures.gradleCodeInsightFixture
import com.intellij.gradle.testFramework.projectInfo.buildFile
import com.intellij.gradle.testFramework.projectInfo.file
import com.intellij.gradle.testFramework.projectInfo.simpleSettingsFile
import com.intellij.gradle.testFramework.util.KOTLIN_DSL_DELEGATING_PROPERTY_SUPPORTED_VERSIONS
import com.intellij.gradle.testFramework.util.KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS
import com.intellij.gradle.testFramework.util.VERSION_CATALOGS_SUPPORTED_VERSIONS
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass

@GradleImmutableProjectTestApplication
class RedundantKotlinStdLibInspectionTest {

  @AfterEach
  fun tearDown() {
    KotlinSdkType.removeKotlinSdkInTests()
  }

  @Nested
  @ParameterizedClass
  @AllGradleVersionsSource
  @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
  inner class CustomConfigurations(private val gradleVersion: GradleVersion) {

    private val codeInsightFixture by customConfigurationsGradleCodeInsightFixture(gradleVersion)

    @BeforeEach
    fun setUp() {
      codeInsightFixture.enableInspections(GradleRedundantKotlinStdLibInspection::class.java)
      if (isMissingDeprecationAnnotationAddedToKotlinDsl(gradleVersion)) {
        codeInsightFixture.disableBuiltInKotlinHighlighters()
      }
    }

    @Test
    fun testSameVersion() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
        dependencies { 
            <warning>api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</warning>
        }
        """.trimIndent()
      )
    }

    @Test
    fun testDifferentVersion() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
        dependencies { 
            api("org.jetbrains.kotlin:kotlin-stdlib:2.1.0")
        }
        """.trimIndent()
      )
    }

    @Test
    fun testKotlinMethod() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
        dependencies { 
            <warning>api(kotlin("stdlib", "2.2.0"))</warning>
        }
        """.trimIndent()
      )
    }

    @Test
    fun testKotlinWithVersionInFirstArgMethod() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
        dependencies { 
            <warning>api(kotlin("stdlib:2.2.0"))</warning>
        }
        """.trimIndent()
      )
    }

    @Test
    fun testKotlinMethodNoVersion() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
        dependencies { 
            api(kotlin("stdlib"))
        }
        """.trimIndent()
      )
    }

    @Test
    fun testKotlinMethodDifferentVersion() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
        dependencies { 
            api(kotlin("stdlib", "2.1.0"))
        }
        """.trimIndent()
      )
    }

    @Test
    fun testDependencyNoVersion() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
        dependencies { 
            api("org.jetbrains.kotlin:kotlin-stdlib")
        }
        """.trimIndent()
      )
    }

    @Test
    fun testDependencyNamedArguments() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
        dependencies { 
            <warning>api(group = "org.jetbrains.kotlin", name = "kotlin-stdlib", version = "2.2.0")</warning>
        }
        """.trimIndent()
      )
    }

    @Test
    fun testDependencyNamedArgumentsNoVersion() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
        dependencies { 
            api(group = "org.jetbrains.kotlin", name = "kotlin-stdlib")
        }
        """.trimIndent()
      )
    }

    @Test
    fun testDependencyPositionalArguments() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
        dependencies { 
            <warning>api("org.jetbrains.kotlin", "kotlin-stdlib", "2.2.0")</warning>
        }
        """.trimIndent()
      )
    }

    @Test
    fun testDifferentConfiguration() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
        dependencies { 
            <warning>implementation("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</warning>
        }
        """.trimIndent()
      )
    }

    // should not warn as the overriding kotlin-stdlib dependency is a bit different
    @Test
    fun testDependencyWithExtraArgumentsInClosure() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
        dependencies { 
            api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0") {
                exclude(group = "org.jetbrains", module = "annotations")
            }
        }
        """.trimIndent()
      )
    }

    // should not warn as the overriding kotlin-stdlib dependency is a bit different
    @Test
    fun testDependencyWithExtraArgumentsInMap() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
        dependencies { 
            api(group = "org.jetbrains.kotlin", name = "kotlin-stdlib", version = "2.2.0", ext = "why not")
        }
        """.trimIndent()
      )
    }

    @Test
    fun testSingeStringFalsePositive() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
        dependencies { 
            api("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.2.0")
        }
        """.trimIndent()
      )
    }

    @Test
    fun testKotlinMethodFalsePositive() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
        dependencies { 
            api(kotlin("stdlib-jdk8", "2.2.0"))
        }
        """.trimIndent()
      )
    }

    @Test
    fun testNamedArgumentsFalsePositive() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
        dependencies { 
            api(group = "org.jetbrains.kotlin", name = "kotlin-stdlib-jdk8", version = "2.2.0")
        }
        """.trimIndent()
      )
    }

    @Test
    fun testFalsePositives() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
        dependencies { 
            <warning>api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</warning>
            api("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.2.0")
            api("org.example:some:1.0.0")
            api("org.jetbrains.kotlin:kotlin-stdlib:2.1.0")
        }
        """.trimIndent()
      )
    }

    @Test
    fun testCompileOnly() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
        dependencies { 
            compileOnly("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
        }
        """.trimIndent()
      )
    }

    // PLUGIN DETECTION TESTS

    @Test
    fun testNoPlugin() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        dependencies {
            api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
        }
        """.trimIndent()
      )
    }

    @Test
    fun testDifferentPlugin() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        plugins { id("java") }
        dependencies {
            api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
        }
        """.trimIndent()
      )
    }

    @Test
    fun testPluginIdNoVersion() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        plugins { id("org.jetbrains.kotlin.jvm") }
        dependencies {
            api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
        }
        """.trimIndent()
      )
    }

    @Test
    fun testPluginFromKotlinMethod() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        plugins { kotlin("jvm").version("2.2.0") }
        dependencies {
            <warning>api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</warning>
        }
        """.trimIndent()
      )
    }

    @Test
    fun testPluginFromKotlinMethodBinary() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        plugins { kotlin("jvm") version "2.2.0" }
        dependencies {
            <warning>api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</warning>
        }
        """.trimIndent()
      )
    }

    @Test
    fun testPluginFromKotlinMethodNotApplied() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        plugins { kotlin("jvm").version("2.2.0").apply(false) }
        dependencies {
            api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
        }
        """.trimIndent()
      )
    }

    @Test
    fun testPluginFromKotlinMethodNotAppliedBinary() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        plugins { kotlin("jvm") version "2.2.0" apply false }
        dependencies {
            api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
        }
        """.trimIndent()
      )
    }

    @Test
    fun testPluginFromKotlinMethodNotAppliedDotBinary() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        plugins { kotlin("jvm").version("2.2.0") apply false }
        dependencies {
            api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
        }
        """.trimIndent()
      )
    }

    @Test
    fun testPluginIdNotApplied() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        plugins {
            id("org.jetbrains.kotlin.jvm").version("2.2.0").apply(false)
        }
        dependencies {
            api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
        }
        """.trimIndent()
      )
    }

    @Test
    fun testPluginIdApplied() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        plugins {
            id("org.jetbrains.kotlin.jvm").version("2.2.0").apply(true)
        }
        dependencies {
            <warning>api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</warning>
        }
        """.trimIndent()
      )
    }

    @Test
    fun testPluginIdMethodBinary() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        plugins { id("org.jetbrains.kotlin.jvm") version "2.2.0" }
        dependencies {
            <warning>api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</warning>
        }
        """.trimIndent()
      )
    }

    @Test
    fun testPluginIdMethodNotAppliedBinary() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        plugins { id("org.jetbrains.kotlin.jvm") version "2.2.0" apply false }
        dependencies {
            api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
        }
        """.trimIndent()
      )
    }

    @Test
    fun testPluginIdMethodNotAppliedDotBinary() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") apply false }
        dependencies {
            api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
        }
        """.trimIndent()
      )
    }

    // STRING RESOLVING

    @Test
    fun testSameVal() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
        val coordinates = "org.jetbrains.kotlin:kotlin-stdlib:2.2.0"
        dependencies {
            <warning>api(coordinates)</warning>
        }
        """.trimIndent()
      )
    }

    @Test
    fun testDifferentVal() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
        val coordinates = "org.jetbrains.kotlin:kotlin-stdlib:2.1.0"
        dependencies {
            api(coordinates)
        }
        """.trimIndent()
      )
    }

    @Test
    fun testSameGroupVal() {
      codeInsightFixture.checkGradleScriptHighlighting(
        $$"""
        plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
        val group = "org.jetbrains.kotlin"
        dependencies {
            <warning>api("$group:kotlin-stdlib:2.2.0")</warning>
        }
        """.trimIndent()
      )
    }

    @Test
    fun testDifferentGroupVal() {
      codeInsightFixture.checkGradleScriptHighlighting(
        $$"""
        plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
        val group = "org.other.kotlin"
        dependencies {
            api("$group:kotlin-stdlib:2.2.0")
        }
        """.trimIndent()
      )
    }

    @Test
    fun testSameNameVal() {
      codeInsightFixture.checkGradleScriptHighlighting(
        $$"""
        plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
        val name = "kotlin-stdlib"
        dependencies {
            <warning>api("org.jetbrains.kotlin:$name:2.2.0")</warning>
        }
        """.trimIndent()
      )
    }

    @Test
    fun testDifferentNameVal() {
      codeInsightFixture.checkGradleScriptHighlighting(
        $$"""
        plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
        val name = "kotlin-stdlib-jdk8"
        dependencies {
            api("org.jetbrains.kotlin:$name:2.2.0")
        }
        """.trimIndent()
      )
    }

    @Test
    fun testSameVersionVal() {
      codeInsightFixture.checkGradleScriptHighlighting(
        $$"""
        plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
        val version = "2.2.0"
        dependencies {
            <warning>api("org.jetbrains.kotlin:kotlin-stdlib:$version")</warning>
        }
        """.trimIndent()
      )
    }

    @Test
    fun testDifferentVersionVal() {
      codeInsightFixture.checkGradleScriptHighlighting(
        $$"""
        plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
        val version = "2.1.0"
        dependencies {
            api("org.jetbrains.kotlin:kotlin-stdlib:$version")
        }
        """.trimIndent()
      )
    }

    @Test
    fun testSameGroupValNamedArguments() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
        val group = "org.jetbrains.kotlin"
        dependencies {
            <warning>api(group = group, name = "kotlin-stdlib", version = "2.2.0")</warning>
        }
        """.trimIndent()
      )
    }

    @Test
    fun testDifferentGroupValNamedArguments() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
        val group = "org.other.kotlin"
        dependencies {
            api(group = group, name = "kotlin-stdlib", version = "2.2.0")
        }
        """.trimIndent()
      )
    }

    @Test
    fun testSameNameValNamedArguments() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
        val name = "kotlin-stdlib"
        dependencies {
            <warning>api(group = "org.jetbrains.kotlin", name = name, version = "2.2.0")</warning>
        }
        """.trimIndent()
      )
    }

    @Test
    fun testDifferentNameValNamedArguments() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
        val name = "kotlin-stdlib-jdk8"
        dependencies {
            api(group = "org.jetbrains.kotlin", name = name, version = "2.2.0")
        }
        """.trimIndent()
      )
    }

    @Test
    fun testSameVersionValNamedArguments() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
        val version = "2.2.0"
        dependencies {
            <warning>api(group = "org.jetbrains.kotlin", name = "kotlin-stdlib", version = version)</warning>
        }
        """.trimIndent()
      )
    }

    @Test
    fun testDifferentVersionValNamedArguments() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
        val version = "2.1.0"
        dependencies {
            api(group = "org.jetbrains.kotlin", name = "kotlin-stdlib", version = version)
        }
        """.trimIndent()
      )
    }

    @Test
    fun testKotlinMethodSameNameVal() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
        val kotlinStdlib = "stdlib"
        dependencies {
            <warning>api(kotlin(kotlinStdlib, "2.2.0"))</warning>
        }
        """.trimIndent()
      )
    }

    @Test
    fun testKotlinMethodSameVersionVal() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
        val version = "2.2.0"
        dependencies {
            <warning>api(kotlin("stdlib", version))</warning>
        }
        """.trimIndent()
      )
    }

    @Test
    fun testKotlinMethodDifferentVersionVal() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
        val version = "2.1.0"
        dependencies {
            api(kotlin("stdlib", version))
        }
        """.trimIndent()
      )
    }

    @Test
    fun testQuickFixRemove() {
      codeInsightFixture.checkGradleScriptIntention(
        """
        plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
        dependencies {
            <caret>api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
        }
        """.trimIndent(),
        """
        plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
        dependencies {
        }
        """.trimIndent(),
        "Remove dependency"
      )
    }
  }

  @Nested
  @ParameterizedClass
  @AllGradleVersionsSource
  @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS, KOTLIN_DSL_DELEGATING_PROPERTY_SUPPORTED_VERSIONS)
  inner class CustomConfigurationsWithDelegatingProperty(private val gradleVersion: GradleVersion) {

    private val codeInsightFixture by customConfigurationsGradleCodeInsightFixture(gradleVersion)

    @BeforeEach
    fun setUp() {
      codeInsightFixture.enableInspections(GradleRedundantKotlinStdLibInspection::class.java)
      if (isMissingDeprecationAnnotationAddedToKotlinDsl(gradleVersion)) {
        codeInsightFixture.disableBuiltInKotlinHighlighters()
      }
    }

    @Test
    fun testCustomConfigurationString() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
        val customConf by configurations.creating {}
        dependencies { 
            <warning>"customConf"("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</warning>
        }
        """.trimIndent()
      )
    }

    @Test
    fun testCustomConfiguration() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
        val customConf by configurations.creating {}
        dependencies { 
            <warning>customConf("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</warning>
        }
        """.trimIndent()
      )
    }

    @Test
    fun testCompileOnlyCustomSourceSet() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
        val customSourceSet by sourceSets.creating {}
        dependencies { 
            "customSourceSetCompileOnly"("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
        }
        """.trimIndent()
      )
    }
  }

  @Nested
  @ParameterizedClass
  @AllGradleVersionsSource
  @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
  inner class DisabledDefaultStdlib(private val gradleVersion: GradleVersion) {

    private val codeInsightFixture by disabledDefaultStdlibCodeInsightFixture(gradleVersion)

    @BeforeEach
    fun setUp() {
      codeInsightFixture.enableInspections(GradleRedundantKotlinStdLibInspection::class.java)
      if (isMissingDeprecationAnnotationAddedToKotlinDsl(gradleVersion)) {
        codeInsightFixture.disableBuiltInKotlinHighlighters()
      }
    }

    @Test
    fun testDisabledDefaultStdLib() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
        dependencies { 
            api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
        }
        """.trimIndent()
      )
    }
  }

  @Nested
  @ParameterizedClass
  @AllGradleVersionsSource
  @TargetVersions(VERSION_CATALOGS_SUPPORTED_VERSIONS)
  inner class VersionCatalogs(private val gradleVersion: GradleVersion) {

    private val codeInsightFixture by customConfigurationsGradleCodeInsightFixture(gradleVersion)

    @BeforeEach
    fun setUp() {
      codeInsightFixture.enableInspections(GradleRedundantKotlinStdLibInspection::class.java)
      if (isMissingDeprecationAnnotationAddedToKotlinDsl(gradleVersion)) {
        codeInsightFixture.disableBuiltInKotlinHighlighters()
      }
    }

    // VERSION CATALOG RESOLVING TESTS

    @Test
    fun testDependencyFromVersionCatalog() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
        dependencies {
            <warning>api(libs.kotlin.std.lib.simple)</warning>
        }
        """.trimIndent()
      )
    }

    @Test
    fun testDependencyFromVersionCatalogNoVersion() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
        dependencies {
            api(libs.kotlin.std.lib.noVersion)
        }
        """.trimIndent()
      )
    }

    @Test
    fun testDependencyFromVersionCatalogModuleAndVersion() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
        dependencies {
            <warning>api(libs.kotlin.std.lib.moduleVersion)</warning>
        }
        """.trimIndent()
      )
    }

    @Test
    fun testDependencyFromVersionCatalogModuleAndVersionReference() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
        dependencies {
            <warning>api(libs.kotlin.std.lib.moduleVersionRef)</warning>
        }
        """.trimIndent()
      )
    }

    @Test
    fun testDependencyFromVersionCatalogFull() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
        dependencies {
            <warning>api(libs.kotlin.std.lib.groupNameVersion)</warning>
        }
        """.trimIndent()
      )
    }

    @Test
    fun testDependencyFromVersionCatalogFullVersionReference() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
        dependencies {
            <warning>api(libs.kotlin.std.lib.groupNameVersionRef)</warning>
        }
        """.trimIndent()
      )
    }

    @Test
    fun testDependencyFromVersionCatalogMultilineString() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
        dependencies {
            <warning>api(libs.kotlin.std.lib.multiline)</warning>
        }
        """.trimIndent()
      )
    }

    @Test
    fun testPluginFromVersionCatalog() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        plugins { alias(${libsInPlugins(gradleVersion)}.plugins.kotlinJvm) }
        dependencies {
            <warning>api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</warning>
        }
        """.trimIndent()
      )
    }

    @Test
    fun testPluginFromVersionCatalogFull() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        plugins { alias(${libsInPlugins(gradleVersion)}.plugins.kotlinJvmFull) }
        dependencies {
            <warning>api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</warning>
        }
        """.trimIndent()
      )
    }

    @Test
    fun testPluginFromVersionCatalogFullWithVersionReference() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        plugins { alias(${libsInPlugins(gradleVersion)}.plugins.kotlinJvmFullRef) }
        dependencies {
            <warning>api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</warning>
        }
        """.trimIndent()
      )
    }

    @Test
    fun testPluginFromVersionCatalogNotApplied() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        plugins {
            alias(${libsInPlugins(gradleVersion)}.plugins.kotlinJvm).apply(false)
        }
        dependencies {
            api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
        }
        """.trimIndent()
      )
    }

    @Test
    fun testPluginFromVersionCatalogNotAppliedBinary() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        plugins {
            alias(${libsInPlugins(gradleVersion)}.plugins.kotlinJvm) apply false
        }
        dependencies {
            api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
        }
        """.trimIndent()
      )
    }
  }

  @Nested
  @ParameterizedClass
  @AllGradleVersionsSource
  @TargetVersions(VERSION_CATALOGS_SUPPORTED_VERSIONS, KOTLIN_DSL_DELEGATING_PROPERTY_SUPPORTED_VERSIONS)
  inner class VersionCatalogsWithDelegatingProperty(private val gradleVersion: GradleVersion) {

    private val codeInsightFixture by customConfigurationsGradleCodeInsightFixture(gradleVersion)

    @BeforeEach
    fun setUp() {
      codeInsightFixture.enableInspections(GradleRedundantKotlinStdLibInspection::class.java)
      if (isMissingDeprecationAnnotationAddedToKotlinDsl(gradleVersion)) {
        codeInsightFixture.disableBuiltInKotlinHighlighters()
      }
    }

    @Test
    fun testCustomConfigurationVersionCatalog() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
        val customConf by configurations.creating {}
        dependencies {
            <warning>customConf(libs.kotlin.std.lib.simple)</warning>
        }
        """.trimIndent()
      )
    }
  }

  companion object {
    private fun disabledDefaultStdlibCodeInsightFixture(gradleVersion: GradleVersion) =
      gradleCodeInsightFixture(gradleVersion, "disabled-default-stdlib") {
        simpleSettingsFile()
        buildFile {
          withKotlinJvmPlugin()
        }
        file("gradle.properties", "kotlin.stdlib.default.dependency=false")
      }

    /**
     * Cause: https://github.com/gradle/gradle/issues/22797
     */
    private fun libsInPlugins(gradleVersion: GradleVersion) =
      if (gradleVersion < GradleVersion.version("8.1")) "<error>libs</error>"
      else "libs"
  }
}
