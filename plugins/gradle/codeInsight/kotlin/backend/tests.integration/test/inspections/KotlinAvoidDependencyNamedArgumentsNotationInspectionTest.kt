// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.codeInsight.kotlin.backend.tests.integration.inspections

import com.intellij.gradle.codeInsight.backend.inspections.declarations.GradleAvoidDependencyNamedArgumentsNotationInspection
import com.intellij.gradle.codeInsight.kotlin.testFramework.checkGradleScriptHighlighting
import com.intellij.gradle.codeInsight.kotlin.testFramework.checkGradleScriptIntention
import com.intellij.gradle.codeInsight.kotlin.testFramework.checkNoGradleScriptIntentions
import com.intellij.gradle.codeInsight.kotlin.testFramework.disableBuiltInKotlinHighlighters
import com.intellij.gradle.codeInsight.kotlin.testFramework.fixtures.customConfigurationsGradleCodeInsightFixture
import com.intellij.gradle.codeInsight.kotlin.testFramework.isMissingDeprecationAnnotationAddedToKotlinDsl
import com.intellij.gradle.testFramework.annotations.AllGradleVersionsSource
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

@GradleImmutableProjectTestApplication
class KotlinAvoidDependencyNamedArgumentsNotationInspectionTest {

  @AfterEach
  fun tearDown() {
    KotlinSdkType.removeKotlinSdkInTests()
  }

  @Nested
  @ParameterizedClass
  @AllGradleVersionsSource
  @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
  inner class AllGradleVersions(private val gradleVersion: GradleVersion) {

    private val codeInsightFixture by customConfigurationsGradleCodeInsightFixture(gradleVersion)

    @BeforeEach
    fun setUp() {
      codeInsightFixture.enableInspections(GradleAvoidDependencyNamedArgumentsNotationInspection::class.java)
      if (isMissingDeprecationAnnotationAddedToKotlinDsl(gradleVersion)) {
        codeInsightFixture.disableBuiltInKotlinHighlighters()
      }
    }

    @Test
    fun testSingleString() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        dependencies { 
            implementation("org.gradle:gradle-core:1.0")
        }
        """.trimIndent()
      )
    }

    @Test
    fun testRegular() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        dependencies { 
            implementation$WARNING_START(group = "org.gradle", name = "gradle-core", version = "1.0")$WARNING_END
        }
        """.trimIndent()
      )
      codeInsightFixture.checkGradleScriptIntention(
        """
        dependencies { 
            implementation(group = "org.gradle",<caret> name = "gradle-core", version = "1.0")
        }
        """.trimIndent(),
        """
        dependencies { 
            implementation("org.gradle:gradle-core:1.0")
        }
        """.trimIndent(),
        "Simplify"
      )
    }

    @Test
    fun testNonLiteralArgument() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        val verRef = "1.0"
        dependencies { 
            implementation$WARNING_START(group = "org.gradle", name = "gradle-core", version = verRef)$WARNING_END
        }
        """.trimIndent()
      )
      codeInsightFixture.checkGradleScriptIntention(
        """
        val verRef = "1.0"
        dependencies { 
            implementation(group = "org.gradle", name = "gradle-core", version = verRef)<caret>
        }
        """.trimIndent(),
        $$"""
        val verRef = "1.0"
        dependencies { 
            implementation("org.gradle:gradle-core:$verRef")
        }
        """.trimIndent(),
        "Simplify"
      )
    }

    @Test
    fun testNonLiteralArguments() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        val groupRef = "org.gradle"
        val groupRefRef = groupRef
        val nameRef = "gradle-core"
        val verRef = "1.0"
        dependencies { 
            implementation$WARNING_START(group = groupRefRef, name = nameRef, version = verRef)$WARNING_END
        }
        """.trimIndent()
      )
      codeInsightFixture.checkGradleScriptIntention(
        """
        val groupRef = "org.gradle"
        val groupRefRef = groupRef
        val nameRef = "gradle-core"
        val verRef = "1.0"
        dependencies { 
            implementation(group = groupRefRef, name = nameRef, version = verRef)<caret>
        }
        """.trimIndent(),
        $$"""
        val groupRef = "org.gradle"
        val groupRefRef = groupRef
        val nameRef = "gradle-core"
        val verRef = "1.0"
        dependencies { 
            implementation("$groupRefRef:$nameRef:$verRef")
        }
        """.trimIndent(),
        "Simplify"
      )
    }

    @Test
    fun testNonLiteralArgumentsRequiringBraces() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        fun getVer() = "1.0"
        dependencies { 
            val groupObject = object { fun getGroup() = "org.gradle" }
            implementation$WARNING_START(group = groupObject.getGroup(), name = "gradle-core", version = getVer())$WARNING_END
        }
        """.trimIndent()
      )
      codeInsightFixture.checkGradleScriptIntention(
        """
        fun getVer() = "1.0"
        dependencies { 
            val groupObject = object { fun getGroup() = "org.gradle" }
            implementation(group = groupObject.getGroup(), name = "gradle-core", version = getVer())<caret>
        }
        """.trimIndent(),
        $$"""
        fun getVer() = "1.0"
        dependencies { 
            val groupObject = object { fun getGroup() = "org.gradle" }
            implementation("${groupObject.getGroup()}:gradle-core:${getVer()}")
        }
        """.trimIndent(),
        "Simplify"
      )
    }

    @Test
    fun testArgumentWithDollarInterpolation() {
      codeInsightFixture.checkGradleScriptHighlighting(
        $$"""
        val verRef = "1.0"
        dependencies { 
            implementation$$WARNING_START(group = "org.gradle", name = "gradle-core", version = "$verRef")$$WARNING_END
        }
        """.trimIndent()
      )
      codeInsightFixture.checkGradleScriptIntention(
        $$"""
        val verRef = "1.0"
        dependencies { 
            implementation(group = "org.gradle",<caret> name = "gradle-core", version = "$verRef")
        }
        """.trimIndent(),
        $$"""
        val verRef = "1.0"
        dependencies { 
            implementation("org.gradle:gradle-core:$verRef")
        }
        """.trimIndent(),
        "Simplify"
      )
    }

    @Test
    fun testNoVersionArgument() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        dependencies { 
            implementation$WARNING_START(group = "org.gradle", name = "gradle-core")$WARNING_END
        }
        """.trimIndent()
      )
      codeInsightFixture.checkGradleScriptIntention(
        """
        dependencies { 
            implementation(group = "org.gradle",<caret> name = "gradle-core")
        }
        """.trimIndent(),
        """
        dependencies { 
            implementation("org.gradle:gradle-core")
        }
        """.trimIndent(),
        "Simplify"
      )
    }

    @Test
    fun testWithBlock() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        dependencies { 
            implementation$WARNING_START(group = "org.gradle", name = "gradle-core", version = "1.0")$WARNING_END {
                exclude(group = "com.google.guava", module = "guava")
            }
        }
        """.trimIndent()
      )
      codeInsightFixture.checkGradleScriptIntention(
        """
        dependencies { 
            implementation(group = "org.gradle",<caret> name = "gradle-core", version = "1.0") {
                exclude(group = "com.google.guava", module = "guava")
            }
        }
        """.trimIndent(),
        """
        dependencies { 
            implementation("org.gradle:gradle-core:1.0") {
                exclude(group = "com.google.guava", module = "guava")
            }
        }
        """.trimIndent(),
        "Simplify"
      )
    }

    @Test
    fun testUnusualArgumentOrder() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        dependencies { 
            implementation$WARNING_START(version = "1.0", group = "org.gradle", name = "gradle-core")$WARNING_END
        }
        """.trimIndent()
      )
      codeInsightFixture.checkGradleScriptIntention(
        """
        dependencies { 
            implementation(version = "1.0",<caret> group = "org.gradle", name = "gradle-core")
        }
        """.trimIndent(),
        """
        dependencies { 
            implementation("org.gradle:gradle-core:1.0")
        }
        """.trimIndent(),
        "Simplify"
      )
    }

    @Test
    fun testRawStringArguments() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        dependencies { 
            implementation$WARNING_START(group = ${'"'}""org.gradle""${'"'}, name = ${'"'}""gradle-core""${'"'}, version = ${'"'}""1.0""${'"'})$WARNING_END
        }
        """.trimIndent()
      )
      codeInsightFixture.checkGradleScriptIntention(
        """
        dependencies { 
            implementation(group = ${'"'}""org.gradle""${'"'},<caret> name = ${'"'}""gradle-core""${'"'}, version = ${'"'}""1.0""${'"'})
        }
        """.trimIndent(),
        """
        dependencies { 
            implementation("org.gradle:gradle-core:1.0")
        }
        """.trimIndent(),
        "Simplify"
      )
    }

    @Test
    fun testPositionalArguments() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        dependencies { 
            implementation$WARNING_START("org.gradle", "gradle-core", "1.0")$WARNING_END
        }
        """.trimIndent()
      )
      codeInsightFixture.checkGradleScriptIntention(
        """
        dependencies { 
            implementation(<caret>"org.gradle", "gradle-core", "1.0")
        }
        """.trimIndent(),
        """
        dependencies { 
            implementation("org.gradle:gradle-core:1.0")
        }
        """.trimIndent(),
        "Simplify"
      )
    }

    @Test
    fun testMixPositionalAndNamedArguments() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        dependencies { 
            implementation$WARNING_START("org.gradle", "gradle-core", version = "1.0")$WARNING_END
        }
        """.trimIndent()
      )
      codeInsightFixture.checkGradleScriptIntention(
        """
        dependencies { 
            implementation(<caret>"org.gradle", "gradle-core", version = "1.0")
        }
        """.trimIndent(),
        """
        dependencies { 
            implementation("org.gradle:gradle-core:1.0")
        }
        """.trimIndent(),
        "Simplify"
      )
    }

    @Test
    fun testMultipleDependenciesBlocks() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        dependencies { 
            implementation$WARNING_START(group = "org.gradle", name = "gradle-core", version = "1.0")$WARNING_END
        }
        dependencies { 
            implementation$WARNING_START(group = "org.gradle", name = "gradle-core-other", version = "1.0")$WARNING_END
        }
        """.trimIndent()
      )
      codeInsightFixture.checkGradleScriptIntention(
        """
        dependencies { 
            implementation(group = "org.gradle", name = "gradle-core", version = "1.0")<caret>
        }
        dependencies { 
            implementation(group = "org.gradle", name = "gradle-core-other", version = "1.0")
        }
        """.trimIndent(),
        """
        dependencies { 
            implementation("org.gradle:gradle-core:1.0")
        }
        dependencies { 
            implementation(group = "org.gradle", name = "gradle-core-other", version = "1.0")
        }
        """.trimIndent(),
        "Simplify"
      )
    }

    // conversion to a single string does not look nice, so don't offer a QF
    @Test
    fun testMultilineRawStringArguments() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        dependencies { 
            implementation$WARNING_START(group = ${'"'}""
            org.gradle
            ""${'"'}, name = ${'"'}""gradle-core""${'"'}, version = "1.0")$WARNING_END
        }
        """.trimIndent()
      )
      codeInsightFixture.checkNoGradleScriptIntentions(
        """
        dependencies { 
            implementation(group = ${'"'}""
            org.gradle
            ""${'"'}, name = ${'"'}""gradle-core""${'"'}, version = "1.0")<caret>
        }
        """.trimIndent(),
        "Simplify"
      )
    }

    @Test
    fun testWithClassifier() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        dependencies {
            implementation$WARNING_START(group = "org.gradle", name = "gradle-core", version = "1.0", classifier = "resources")$WARNING_END
        }
        """.trimIndent()
      )
      codeInsightFixture.checkGradleScriptIntention(
        """
        dependencies {
            implementation(group = "org.gradle", name = "gradle-core", version = "1.0", classifier = "resources")<caret>
        }
        """.trimIndent(),
        """
        dependencies {
            implementation("org.gradle:gradle-core:1.0:resources")
        }
        """.trimIndent(),
        "Simplify"
      )
    }

    @Test
    fun testWithClassifierNoVersion() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        dependencies {
            implementation$WARNING_START(group = "org.gradle", name = "gradle-core", classifier = "resources")$WARNING_END
        }
        """.trimIndent()
      )
      codeInsightFixture.checkNoGradleScriptIntentions(
        """
        dependencies {
            implementation(group = "org.gradle", name = "gradle-core", classifier = "resources")<caret>
        }
        """.trimIndent(),
        "Simplify"
      )
    }

    @Test
    fun testWithExtension() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        dependencies {
            implementation$WARNING_START(group = "org.gradle", name = "gradle-core", version = "1.0", ext = "zip")$WARNING_END
        }
        """.trimIndent()
      )
      codeInsightFixture.checkGradleScriptIntention(
        """
        dependencies {
            implementation(group = "org.gradle", name = "gradle-core", version = "1.0", ext = "zip")<caret>
        }
        """.trimIndent(),
        """
        dependencies {
            implementation("org.gradle:gradle-core:1.0@zip")
        }
        """.trimIndent(),
        "Simplify"
      )
    }

    @Test
    fun testWithExtensionNoVersion() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        dependencies {
            implementation$WARNING_START(group = "org.gradle", name = "gradle-core", ext = "zip")$WARNING_END
        }
        """.trimIndent()
      )
      codeInsightFixture.checkNoGradleScriptIntentions(
        """
        dependencies {
            implementation(group = "org.gradle", name = "gradle-core", ext = "zip")<caret>
        }
        """.trimIndent(),
        "Simplify"
      )
    }

    @Test
    fun testWithClassifierAndExtension() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        dependencies {
            implementation$WARNING_START(group = "org.gradle", name = "gradle-core", version = "1.0", classifier = "resources", ext = "zip")$WARNING_END
        }
        """.trimIndent()
      )
      codeInsightFixture.checkGradleScriptIntention(
        """
        dependencies {
            implementation(group = "org.gradle", name = "gradle-core", version = "1.0", classifier = "resources", ext = "zip")<caret>
        }
        """.trimIndent(),
        """
        dependencies {
            implementation("org.gradle:gradle-core:1.0:resources@zip")
        }
        """.trimIndent(),
        "Simplify"
      )
    }

    @Test
    fun testWithClassifierAndExtensionNoVersion() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        dependencies {
            implementation$WARNING_START(group = "org.gradle", name = "gradle-core", classifier = "resources", ext = "zip")$WARNING_END
        }
        """.trimIndent()
      )
      codeInsightFixture.checkNoGradleScriptIntentions(
        """
        dependencies {
            implementation(group = "org.gradle", name = "gradle-core", classifier = "resources", ext = "zip")<caret>
        }
        """.trimIndent(),
        "Simplify"
      )
    }

    @Test
    fun testWithTargetConfiguration() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        dependencies {
            implementation$WARNING_START(group = "org.gradle", name = "gradle-core", version = "1.0", configuration = "configTarget")$WARNING_END
        }
        """.trimIndent()
      )
      codeInsightFixture.checkGradleScriptIntention(
        """
        dependencies {
            implementation(group = "org.gradle", name = "gradle-core", version = "1.0", configuration = "configTarget")<caret>
        }
        """.trimIndent(),
        """
        dependencies {
            implementation("org.gradle:gradle-core:1.0") { targetConfiguration = "configTarget" }
        }
        """.trimIndent(),
        "Simplify"
      )
    }

    @Test
    fun testWithTargetConfigurationExistingBlock() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        dependencies {
            implementation$WARNING_START(group = "org.gradle", name = "gradle-core", version = "1.0", configuration = "configTarget")$WARNING_END {
                because("why not")
            }
        }
        """.trimIndent()
      )
      codeInsightFixture.checkGradleScriptIntention(
        """
        dependencies {
            implementation(group = "org.gradle", name = "gradle-core", version = "1.0", configuration = "configTarget")<caret> {
                because("why not")
            }
        }
        """.trimIndent(),
        """
        dependencies {
            implementation("org.gradle:gradle-core:1.0") {
                targetConfiguration = "configTarget"
                because("why not")
            }
        }
        """.trimIndent(),
        "Simplify"
      )
    }

    @Test
    fun testWithTargetConfigurationExistingEmptyBlock() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        dependencies {
            implementation$WARNING_START(group = "org.gradle", name = "gradle-core", version = "1.0", configuration = "configTarget")$WARNING_END {}
        }
        """.trimIndent()
      )
      codeInsightFixture.checkGradleScriptIntention(
        """
        dependencies {
            implementation(group = "org.gradle", name = "gradle-core", version = "1.0", configuration = "configTarget")<caret> {}
        }
        """.trimIndent(),
        """
        dependencies {
            implementation("org.gradle:gradle-core:1.0") {
                targetConfiguration = "configTarget"
            }
        }
        """.trimIndent(),
        "Simplify"
      )
    }

    @Test
    fun testWithAllArguments() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        dependencies {
            implementation$WARNING_START(group = "org.gradle", name = "gradle-core", version = "1.0", configuration = "configTarget", classifier = "resources", ext = "zip")$WARNING_END
        }
        """.trimIndent()
      )
      codeInsightFixture.checkGradleScriptIntention(
        """
        dependencies {
            implementation(group = "org.gradle", name = "gradle-core", version = "1.0", configuration = "configTarget", classifier = "resources", ext = "zip")<caret>
        }
        """.trimIndent(),
        """
        dependencies {
            implementation("org.gradle:gradle-core:1.0:resources@zip") { targetConfiguration = "configTarget" }
        }
        """.trimIndent(),
        "Simplify"
      )
    }

    @Test
    fun testWithTargetConfigurationNoVersion() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        dependencies {
            implementation$WARNING_START(group = "org.gradle", name = "gradle-core", configuration = "configTarget")$WARNING_END
        }
        """.trimIndent()
      )
      codeInsightFixture.checkGradleScriptIntention(
        """
        dependencies {
            implementation(group = "org.gradle", name = "gradle-core", configuration = "configTarget")<caret>
        }
        """.trimIndent(),
        """
        dependencies {
            implementation("org.gradle:gradle-core") { targetConfiguration = "configTarget" }
        }
        """.trimIndent(),
        "Simplify"
      )
    }
  }

  @Nested
  @ParameterizedClass
  @AllGradleVersionsSource
  @TargetVersions(
    KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS, "<9.1.0",
    reason = "IDEA-381769: highlighting fails on deprecations from Gradle"
  )
  inner class BeforeGradle91(private val gradleVersion: GradleVersion) {

    private val codeInsightFixture by customConfigurationsGradleCodeInsightFixture(gradleVersion)

    @BeforeEach
    fun setUp() {
      codeInsightFixture.enableInspections(GradleAvoidDependencyNamedArgumentsNotationInspection::class.java)
      if (isMissingDeprecationAnnotationAddedToKotlinDsl(gradleVersion)) {
        codeInsightFixture.disableBuiltInKotlinHighlighters()
      }
    }

    @Test
    fun testCustomConfiguration() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        val customConf by configurations.creating {}
        dependencies { 
            customConf$WARNING_START(group = "org.gradle", name = "gradle-core", version = "1.0")$WARNING_END
        }
        """.trimIndent()
      )
      codeInsightFixture.checkGradleScriptIntention(
        """
        val customConf by configurations.creating {}
        dependencies { 
            customConf(group = "org.gradle",<caret> name = "gradle-core", version = "1.0")
        }
        """.trimIndent(),
        """
        val customConf by configurations.creating {}
        dependencies { 
            customConf("org.gradle:gradle-core:1.0")
        }
        """.trimIndent(),
        "Simplify"
      )
    }

    @Test
    fun testCustomConfigurationString() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        val customConf by configurations.creating {}
        dependencies { 
            "customConf"$WARNING_START(group = "org.gradle", name = "gradle-core", version = "1.0")$WARNING_END
        }
        """.trimIndent()
      )
      codeInsightFixture.checkGradleScriptIntention(
        """
        val customConf by configurations.creating {}
        dependencies { 
            "customConf"(group = "org.gradle",<caret> name = "gradle-core", version = "1.0")
        }
        """.trimIndent(),
        """
        val customConf by configurations.creating {}
        dependencies { 
            "customConf"("org.gradle:gradle-core:1.0")
        }
        """.trimIndent(),
        "Simplify"
      )
    }

    @Test
    fun testCustomSourceSet() {
      codeInsightFixture.checkGradleScriptHighlighting(
        """
        val customSourceSet by sourceSets.creating {}
        dependencies { 
            "customSourceSetImplementation"$WARNING_START(group = "org.gradle", name = "gradle-core", version = "1.0")$WARNING_END
        }
        """.trimIndent()
      )
      codeInsightFixture.checkGradleScriptIntention(
        """
        val customSourceSet by sourceSets.creating {}
        dependencies { 
            "customSourceSetImplementation"(group = "org.gradle",<caret> name = "gradle-core", version = "1.0")
        }
        """.trimIndent(),
        """
        val customSourceSet by sourceSets.creating {}
        dependencies { 
            "customSourceSetImplementation"("org.gradle:gradle-core:1.0")
        }
        """.trimIndent(),
        "Simplify"
      )
    }
  }

  @Nested
  @ParameterizedClass
  @AllGradleVersionsSource
  @TargetVersions("9.0.0+", reason = "\"multi dollar interpolation\" is only available since language version 2.2")
  inner class MultiDollarInterpolationSupported(private val gradleVersion: GradleVersion) {

    private val codeInsightFixture by customConfigurationsGradleCodeInsightFixture(gradleVersion)

    @BeforeEach
    fun setUp() {
      codeInsightFixture.enableInspections(GradleAvoidDependencyNamedArgumentsNotationInspection::class.java)
      if (isMissingDeprecationAnnotationAddedToKotlinDsl(gradleVersion)) {
        codeInsightFixture.disableBuiltInKotlinHighlighters()
      }
    }

    @Test
    fun testMultiDollarInterpolation() {
      codeInsightFixture.checkGradleScriptHighlighting(
        $$$"""
        val gradle = "org.gradle"
        dependencies { 
            implementation$$$WARNING_START(group = $$${'"'}""$gradle""$$${'"'}, name = $$$$${'"'}""gr$dle-core""$$${'"'}, version = "1.0")$$$WARNING_END
        }
        """.trimIndent()
      )
      codeInsightFixture.checkGradleScriptIntention(
        $$$"""
        val gradle = "org.gradle"
        dependencies { 
            implementation(group = $$${'"'}""$gradle""$$${'"'},<caret> name = $$$$${'"'}""gr$dle-core""$$${'"'}, version = "1.0")
        }
        """.trimIndent(),
        $$$"""
        val gradle = "org.gradle"
        dependencies { 
            implementation("$gradle:gr${'$'}dle-core:1.0")
        }
        """.trimIndent(),
        "Simplify"
      )
    }
  }

  companion object {
    private const val WARNING_START = "<weak_warning>"
    private const val WARNING_END = "</weak_warning>"
  }
}
