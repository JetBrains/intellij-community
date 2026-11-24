// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.codeInsight.kotlin.backend.tests.integration.inspections

import com.intellij.gradle.codeInsight.backend.inspections.declarations.GradleAvoidDuplicateDependenciesInspection
import com.intellij.gradle.codeInsight.kotlin.testFramework.checkGradleScriptHighlighting
import com.intellij.gradle.codeInsight.kotlin.testFramework.checkGradleScriptIntention
import com.intellij.gradle.codeInsight.kotlin.testFramework.checkNoGradleScriptIntentions
import com.intellij.gradle.codeInsight.kotlin.testFramework.disableBuiltInKotlinHighlighters
import com.intellij.gradle.codeInsight.kotlin.testFramework.fixtures.customConfigurationsGradleCodeInsightFixture
import com.intellij.gradle.codeInsight.kotlin.testFramework.isMissingDeprecationAnnotationAddedToKotlinDsl
import com.intellij.gradle.testFramework.annotations.AllGradleVersionsSource
import com.intellij.gradle.testFramework.annotations.TargetVersions
import com.intellij.gradle.testFramework.fixtures.application.GradleImmutableProjectTestApplication
import com.intellij.gradle.testFramework.fixtures.gradleCodeInsightFixture
import com.intellij.gradle.testFramework.projectInfo.buildFile
import com.intellij.gradle.testFramework.projectInfo.simpleSettingsFile
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
class KotlinAvoidDuplicateDependenciesInspectionTest {

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
      codeInsightFixture.enableInspections(GradleAvoidDuplicateDependenciesInspection::class.java)
      if (isMissingDeprecationAnnotationAddedToKotlinDsl(gradleVersion)) {
        codeInsightFixture.disableBuiltInKotlinHighlighters()
      }
    }

    @Test
    fun testSingleDependency() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        dependencies {
            api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
        }
        """.trimIndent()
      )
    }

    @Test
    fun testDifferentDependencies() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        dependencies {
            api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
            api("org.jetbrains.kotlin:kotlin-stdlib:2.1.0")
            api("org.jetbrains.kotlin:kotlin-something:2.2.0")
        }
        """.trimIndent()
      )
    }

    @Test
    fun testSimpleSameDependency() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        dependencies {
            <weak_warning>api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</weak_warning>
            <weak_warning>api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</weak_warning>
        }
        """.trimIndent()
      )
    }

    @Test
    fun testSameDependencyDifferentConfigurations() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        dependencies {
            api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
            <weak_warning>implementation("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</weak_warning>
        }
        """.trimIndent()
      )
    }

    @Test
    fun testSameDependencyInDifferentBlocks() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        dependencies {
            <weak_warning>api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</weak_warning>
        }
        
        dependencies {
            <weak_warning>api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</weak_warning>
        }
        """.trimIndent()
      )
    }

    @Test
    fun testSameDependencyConfigurationBlock() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        dependencies {
            <weak_warning>api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</weak_warning>
            <weak_warning>api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0") { exclude("something") }</weak_warning>
        }
        """.trimIndent()
      )
    }

    @Test
    fun testSameDependencyNamedArguments() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        dependencies {
            <weak_warning>api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</weak_warning>
            <weak_warning>api(group = "org.jetbrains.kotlin", name = "kotlin-stdlib", version = "2.2.0")</weak_warning>
        }
        """.trimIndent()
      )
    }

    @Test
    fun testSameDependencyNoVersion() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        dependencies {
            <weak_warning>api("org.jetbrains.kotlin:kotlin-stdlib")</weak_warning>
            <weak_warning>api(group = "org.jetbrains.kotlin", name = "kotlin-stdlib")</weak_warning>
        }
        """.trimIndent()
      )
    }

    @Test
    fun testDifferentVersions() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        dependencies {
            api("org.jetbrains.kotlin:kotlin-stdlib")
            api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
            api("org.jetbrains.kotlin:kotlin-stdlib:2.1.0")
        }
        """.trimIndent()
      )
    }

    @Test
    fun testSameVersionVal() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        dependencies {
            val versionRef = "2.2.0"
            <weak_warning>api(group = "org.jetbrains.kotlin", name = "kotlin-stdlib", version = versionRef)</weak_warning>
            <weak_warning>api(group = "org.jetbrains.kotlin", name = "kotlin-stdlib", version = versionRef)</weak_warning>
        }
        """.trimIndent()
      )
    }

    @Test
    fun testStringInterpolation() {
      codeInsightFixture.checkGradleScriptHighlighting(
        $$"""
        dependencies {
            val groupRef = "org.jetbrains.kotlin"
            val nameRef = "kotlin-stdlib"
            val versionRef = "2.2.0"
            <weak_warning>api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</weak_warning>
            <weak_warning>api("$groupRef:$nameRef:$versionRef")</weak_warning>
        }
        """.trimIndent()
      )
    }

    @Test
    fun testNamedArgumentsVals() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        dependencies {
            val groupRef = "org.jetbrains.kotlin"
            val nameRef = "kotlin-stdlib"
            val versionRef = "2.2.0"
            <weak_warning>api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</weak_warning>
            <weak_warning>api(group = groupRef, name = nameRef, version = versionRef)</weak_warning>
        }
        """.trimIndent()
      )
    }

    @Test
    fun testCoordinateVal() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        dependencies {
            val coordRef = "org.jetbrains.kotlin:kotlin-stdlib:2.2.0"
            <weak_warning>api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</weak_warning>
            <weak_warning>api(coordRef)</weak_warning>
        }
        """.trimIndent()
      )
    }

    @Test
    fun testDifferentVersionSameNameVal() {
      codeInsightFixture.checkGradleScriptHighlighting(
        $$"""
        dependencies {
            val versionRef = "2.1.0"
            api("org.jetbrains.kotlin:kotlin-stdlib:$versionRef")
        }
        dependencies {
            val versionRef = "2.2.0"
            api("org.jetbrains.kotlin:kotlin-stdlib:$versionRef")
        }
        """.trimIndent()
      )
    }

    @Test
    fun testDifferentVersionSameNameValNamedArguments() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        dependencies {
            val versionRef = "2.1.0"
            api(group = "org.jetbrains.kotlin", name = "kotlin-stdlib", version = versionRef)
        }
        dependencies {
            val versionRef = "2.2.0"
            api(group = "org.jetbrains.kotlin", name = "kotlin-stdlib", version = versionRef)
        }
        """.trimIndent()
      )
    }

    @Test
    fun testCustomConfigurationDoesNotExtendApi() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        val customConf = configurations.named("customConf").get()
        dependencies {
            api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
            customConf("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
        }
        """.trimIndent()
      )
    }

    @Test
    fun testCustomConfigurationSimple() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        val customConf = configurations.named("customConf").get()
        dependencies {
            <weak_warning>customConf("org.jetbrains.kotlin:kotlin-other:2.2.0")</weak_warning>
            <weak_warning>customConf("org.jetbrains.kotlin:kotlin-other:2.2.0")</weak_warning>
        }
        """.trimIndent()
      )
    }

    @Test
    fun testSameKotlinArg() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        dependencies {
            <weak_warning>api(kotlin("stdlib"))</weak_warning>
            <weak_warning>api(kotlin("stdlib"))</weak_warning>
        }
        """.trimIndent()
      )
    }

    @Test
    fun testDifferentKotlinArg() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        dependencies {
            api(kotlin("stdlib-jdk8"))
            api(kotlin("stdlib"))
        }
        """.trimIndent()
      )
    }

    @Test
    fun testKotlinArgVar() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        dependencies {
            var kotlinArg = "stdlib"
            api(kotlin(kotlinArg))
            api(kotlin(kotlinArg))
        }
        """.trimIndent()
      )
    }

    @Test
    fun testSameKotlinArgDifferentVals() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        dependencies {
            val kotlinRef = "stdlib"
            val kotlinRef2 = "stdlib"
            <weak_warning>api(kotlin(kotlinRef))</weak_warning>
            <weak_warning>api(kotlin(kotlinRef))</weak_warning>
        }
        """.trimIndent()
      )
    }

    @Test
    fun testSameKotlinArgWithSameVersion() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        dependencies {
            <weak_warning>api(kotlin("stdlib", "2.2.0"))</weak_warning>
            <weak_warning>api(kotlin("stdlib", "2.2.0"))</weak_warning>
        }
        """.trimIndent()
      )
    }

    @Test
    fun testSameKotlinArgWithDifferentVersion() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        dependencies {
            api(kotlin("stdlib", "2.1.0"))
            api(kotlin("stdlib", "2.2.0"))
        }
        """.trimIndent()
      )
    }

    @Test
    fun testSameKotlinArgWithSameVersionVals() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        dependencies {
            val versionRef1 = "2.2.0"
            val versionRef2 = "2.2.0"
            <weak_warning>api(kotlin("stdlib", versionRef1))</weak_warning>
            <weak_warning>api(kotlin("stdlib", versionRef2))</weak_warning>
        }
        """.trimIndent()
      )
    }

    @Test
    fun testSameKotlinArgWithDifferentVersionVals() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        dependencies {
            val versionRef1 = "2.1.0"
            val versionRef2 = "2.2.0"
            api(kotlin("stdlib", versionRef1))
            api(kotlin("stdlib", versionRef2))
        }
        """.trimIndent()
      )
    }

    @Test
    fun testEquivalentKotlinArgWithSingleString() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        dependencies {
            <weak_warning>api(kotlin("stdlib"))</weak_warning>
            <weak_warning>api("org.jetbrains.kotlin:kotlin-stdlib")</weak_warning>
        }
        """.trimIndent()
      )
    }

    @Test
    fun testEquivalentKotlinArgWithVersionInFirstArg() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        dependencies {
            <weak_warning>api(kotlin("stdlib", "2.2.0"))</weak_warning>
            <weak_warning>api(kotlin("stdlib:2.2.0"))</weak_warning>
        }
        """.trimIndent()
      )
    }

    @Test
    fun testEquivalentKotlinArgWithNamedArguments() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        dependencies {
            <weak_warning>api(kotlin("stdlib"))</weak_warning>
            <weak_warning>api("org.jetbrains.kotlin", "kotlin-stdlib")</weak_warning>
        }
        """.trimIndent()
      )
    }

    @Test
    fun testEquivalentKotlinArgWithSingleStringAndVersions() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        dependencies {
            <weak_warning>api(kotlin("stdlib", "2.2.0"))</weak_warning>
            <weak_warning>api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</weak_warning>
        }
        """.trimIndent()
      )
    }

    @Test
    fun testEquivalentKotlinArgWithNamedArgumentsAndVersions() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        dependencies {
            <weak_warning>api(kotlin("stdlib", "2.2.0"))</weak_warning>
            <weak_warning>api("org.jetbrains.kotlin", "kotlin-stdlib", "2.2.0")</weak_warning>
        }
        """.trimIndent()
      )
    }

    @Test
    fun testNotEquivalentKotlinArg() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        dependencies {
            api(kotlin("stdlib", "2.2.0"))
            api("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.2.0")
            api("org.jetbrains.kotlin:kotlin-stdlib")
            api("org.jetbrains:kotlin-stdlib:2.2.0")
            api("org.jetbrains.kotlin:kotlin-stdlib:2.1.0")
            api("org.jetbrains.kotlin:stdlib:2.2.0")
        }
        """.trimIndent()
      )
    }

    @Test
    fun testIgnoreOtherDependencyBlocks() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        buildscript {
            dependencies {
                classpath("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
            }
        }
        dependencies {
            api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
        }
        """.trimIndent()
      )
    }

    @Test
    fun testRemoveSimple() {
      codeInsightFixture.checkGradleScriptIntention(
        """
        dependencies {
            api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")<caret>
            api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
        }
        """.trimIndent(),
        """
        dependencies {
            api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
        }
        """.trimIndent(),
        "Remove exact duplicates"
      )
    }

    @Test
    fun testRemoveSimpleMultiple() {
      codeInsightFixture.checkGradleScriptIntention(
        """
        dependencies {
            api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")<caret>
            api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
            api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
        }
        """.trimIndent(),
        """
        dependencies {
            api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
        }
        """.trimIndent(),
        "Remove exact duplicates"
      )
    }

    @Test
    fun testRemoveSimpleUnselected() {
      codeInsightFixture.checkGradleScriptIntention(
        """
        dependencies {
            api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
            implementation("org:another:1.0.0")
            api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")<caret>
            implementation("org:yet-another:1.0.0")
            api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
        }
        """.trimIndent(),
        """
        dependencies {
            implementation("org:another:1.0.0")
            api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
            implementation("org:yet-another:1.0.0")
        }
        """.trimIndent(),
        "Remove exact duplicates"
      )
    }

    @Test
    fun testRemoveNamedArguments() {
      codeInsightFixture.checkGradleScriptIntention(
        """
        dependencies {
            api(group = "org.jetbrains.kotlin", name = "kotlin-stdlib", version = "2.2.0")<caret>
            api(group = "org.jetbrains.kotlin", name = "kotlin-stdlib", version = "2.2.0")
        }
        """.trimIndent(),
        """
        dependencies {
            api(group = "org.jetbrains.kotlin", name = "kotlin-stdlib", version = "2.2.0")
        }
        """.trimIndent(),
        "Remove exact duplicates"
      )
    }

    @Test
    fun testNoRemoveNamedArgumentsAndSimpleMix() {
      codeInsightFixture.checkNoGradleScriptIntentions(
        """
        dependencies {
            api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")<caret>
            api(group = "org.jetbrains.kotlin", name = "kotlin-stdlib", version = "2.2.0")
        }
        """.trimIndent(),
        "Remove exact duplicates"
      )
      codeInsightFixture.checkNoGradleScriptIntentions(
        """
        dependencies {
            api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
            api(group = "org.jetbrains.kotlin", name = "kotlin-stdlib", version = "2.2.0")<caret>
        }
        """.trimIndent(),
        "Remove exact duplicates"
      )
    }

    @Test
    fun testRemoveVals() {
      codeInsightFixture.checkGradleScriptIntention(
        """
        dependencies {
            val versionRef = "2.2.0"
            api("org.jetbrains.kotlin:kotlin-stdlib:versionRef")<caret>
            api("org.jetbrains.kotlin:kotlin-stdlib:versionRef")
            api("org.jetbrains.kotlin:kotlin-stdlib:versionRef")
        }
        """.trimIndent(),
        """
        dependencies {
            val versionRef = "2.2.0"
            api("org.jetbrains.kotlin:kotlin-stdlib:versionRef")
        }
        """.trimIndent(),
        "Remove exact duplicates"
      )
    }

    @Test
    fun testNoRemoveDifferentConfiguration() {
      codeInsightFixture.checkNoGradleScriptIntentions(
        """
        dependencies {
            api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")<caret>
            implementation("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
        }
        """.trimIndent(),
        "Remove exact duplicates"
      )
      codeInsightFixture.checkNoGradleScriptIntentions(
        """
        dependencies {
            api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
            implementation("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")<caret>
        }
        """.trimIndent(),
        "Remove exact duplicates"
      )
    }

    @Test
    fun testNoRemoveWithConfigurationBlock() {
      codeInsightFixture.checkNoGradleScriptIntentions(
        """
        dependencies {
            api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")<caret>
            api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0") { exclude("something") }
        }
        """.trimIndent(),
        "Remove exact duplicates"
      )
      codeInsightFixture.checkNoGradleScriptIntentions(
        """
        dependencies {
            api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
            api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")<caret> { exclude("something") }
        }
        """.trimIndent(),
        "Remove exact duplicates"
      )
    }

    @Test
    fun testNoRemoveDifferentExtraArguments() {
      codeInsightFixture.checkNoGradleScriptIntentions(
        """
        dependencies {
            api(group = "org.jetbrains.kotlin", name = "kotlin-stdlib", version = "2.2.0")<caret>
            api(group = "org.jetbrains.kotlin", name = "kotlin-stdlib", version = "2.2.0", ext = "something")
        }
        """.trimIndent(),
        "Remove exact duplicates"
      )
      codeInsightFixture.checkNoGradleScriptIntentions(
        """
        dependencies {
            api(group = "org.jetbrains.kotlin", name = "kotlin-stdlib", version = "2.2.0")
            api(group = "org.jetbrains.kotlin", name = "kotlin-stdlib", version = "2.2.0", ext = "something")<caret>
        }
        """.trimIndent(),
        "Remove exact duplicates"
      )
    }

    @Test
    fun testAnnotationProcessorSeparation() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        dependencies {
            implementation("org.projectlombok:lombok:1.18.20")
            annotationProcessor("org.projectlombok:lombok:1.18.20")
        }
        """.trimIndent()
      )
    }

    @Test
    fun testAnnotationProcessorGrouping() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        dependencies {
            implementation("org.projectlombok:lombok:1.18.20")
            <weak_warning>annotationProcessor("org.projectlombok:lombok:1.18.20")</weak_warning>
            <weak_warning>annotationProcessor("org.projectlombok:lombok:1.18.20")</weak_warning>
        }
        """.trimIndent()
      )
    }

    @Test
    fun testAnnotationProcessorDifferentGrouping() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        dependencies {
            implementation("org.projectlombok:lombok:1.18.20")
            annotationProcessor("org.projectlombok:lombok:1.18.20")
            testAnnotationProcessor("org.projectlombok:lombok:1.18.20")
        }
        """.trimIndent()
      )
    }

    @Test
    fun testExtendingConfigurationsHierarchy() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        dependencies {
            api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
            <weak_warning>implementation("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</weak_warning>
            <weak_warning>testImplementation("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</weak_warning>
        }
        """.trimIndent()
      )
    }

    @Test
    fun testNotExtendingConfigurations() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        dependencies {
            implementation("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
            compileOnly("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
            runtimeOnly("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
            testCompileOnly("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
        }
        """.trimIndent()
      )
    }

    @Test
    fun `test custom configuration directly extending implementation`() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        val customExtendingImplementation = configurations.named("customExtendingImplementation").get()
        dependencies {
            implementation("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
            <weak_warning>customExtendingImplementation("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</weak_warning>
        }
        """.trimIndent()
      )
    }

    @Test
    fun `test custom configuration indirectly extending api`() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        val customExtendingImplementation = configurations.named("customExtendingImplementation").get()
        dependencies {
            api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
            <weak_warning>customExtendingImplementation("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</weak_warning>
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
      codeInsightFixture.enableInspections(GradleAvoidDuplicateDependenciesInspection::class.java)
      if (isMissingDeprecationAnnotationAddedToKotlinDsl(gradleVersion)) {
        codeInsightFixture.disableBuiltInKotlinHighlighters()
      }
    }

    @Test
    fun testSimpleVersionCatalogsResolve() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        dependencies {
            <weak_warning>api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</weak_warning>
            <weak_warning>api(libs.kotlin.std.lib.simple)</weak_warning>
        }
        """.trimIndent()
      )
    }

    @Test
    fun testSimpleVersionCatalogsResolveNoVersion() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        dependencies {
            <weak_warning>api("org.jetbrains.kotlin:kotlin-stdlib")</weak_warning>
            <weak_warning>api(libs.kotlin.std.lib.noVersion)</weak_warning>
        }
        """.trimIndent()
      )
    }

    @Test
    fun testVersionCatalogsResolve() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        dependencies {
            <weak_warning>api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</weak_warning>
            <weak_warning>api(libs.kotlin.std.lib.moduleVersion)</weak_warning>
        }
        """.trimIndent()
      )
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        dependencies {
            <weak_warning>api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</weak_warning>
            <weak_warning>api(libs.kotlin.std.lib.groupNameVersion)</weak_warning>
        }
        """.trimIndent()
      )
    }

    @Test
    fun testVersionCatalogsResolveVersionRef() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        dependencies {
            <weak_warning>api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</weak_warning>
            <weak_warning>api(libs.kotlin.std.lib.moduleVersionRef)</weak_warning>
        }
        """.trimIndent()
      )
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        dependencies {
            <weak_warning>api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</weak_warning>
            <weak_warning>api(libs.kotlin.std.lib.groupNameVersionRef)</weak_warning>
        }
        """.trimIndent()
      )
    }

    @Test
    fun testVersionCatalogsResolveMultiline() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        dependencies {
            <weak_warning>api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</weak_warning>
            <weak_warning>api(libs.kotlin.std.lib.multiline)</weak_warning>
        }
        """.trimIndent()
      )
    }

    @Test
    fun testCustomConfigurationVersionCatalog() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        val customConf = configurations.named("customConf").get()
        dependencies {
            <weak_warning>customConf("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</weak_warning>
            <weak_warning>customConf(libs.kotlin.std.lib.simple)</weak_warning>
        }
        """.trimIndent()
      )
    }
  }

  @Nested
  @ParameterizedClass
  @AllGradleVersionsSource
  @TargetVersions("7.0+", reason = "Gradle 7.0+ supports type-safe project dependencies")
  inner class TypeSafeProjectDependencies(private val gradleVersion: GradleVersion) {

    private val codeInsightFixture by typeSafeProjectDependenciesCodeInsightFixture(gradleVersion)

    @BeforeEach
    fun setUp() {
      codeInsightFixture.enableInspections(GradleAvoidDuplicateDependenciesInspection::class.java)
      if (isMissingDeprecationAnnotationAddedToKotlinDsl(gradleVersion)) {
        codeInsightFixture.disableBuiltInKotlinHighlighters()
      }
    }

    @Test
    fun `test different unresolvable accessors are not duplicates`() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        dependencies {
            api(projects.subproject1)
            api(projects.subproject2)
        }
        """.trimIndent()
      )
    }

    @Test
    fun `test identical unresolvable accessors are duplicates`() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        dependencies {
            api(projects.subproject1)
            <weak_warning>api(projects.subproject1.sub)</weak_warning>
            <weak_warning>api(projects.subproject1.sub)</weak_warning>
        }
        """.trimIndent()
      )
    }
  }

  companion object {
    private fun typeSafeProjectDependenciesCodeInsightFixture(gradleVersion: GradleVersion) =
      gradleCodeInsightFixture(gradleVersion, "with-type-safe-project-dependencies") {
        simpleSettingsFile {
          addCode("enableFeaturePreview(\"TYPESAFE_PROJECT_ACCESSORS\")")
          include("subproject1", "subproject1:sub", "subproject2")
        }
        buildFile {
          withKotlinJvmPlugin()
        }
        moduleInfo("$projectName.subproject1", "subproject1") { buildFile { } }
        moduleInfo("$projectName.subproject1.sub", "subproject1/sub") { buildFile { } }
        moduleInfo("$projectName.subproject2", "subproject2") { buildFile { } }
      }
  }
}
