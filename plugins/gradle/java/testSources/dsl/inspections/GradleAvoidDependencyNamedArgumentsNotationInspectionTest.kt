// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.dsl.inspections

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.codeInspection.GradleAvoidDependencyNamedArgumentsNotationInspection
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.jetbrains.plugins.gradle.testFramework.util.withBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.withSettingsFile
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.params.ParameterizedTest

class GradleAvoidDependencyNamedArgumentsNotationInspectionTest : GradleCodeInsightTestCase() {

  private val CUSTOM_PROJECT = GradleTestFixtureBuilder.create("avoid_named_arguments") {
    withSettingsFile(gradleVersion) {
      setProjectName("java-plugin-project")
    }
    withBuildFile(gradleVersion) {
      withJavaPlugin()
      withPrefix {
        call("configurations") {
          code("customConf")
        }
      }
    }
  }

  private fun runTest(
    gradleVersion: GradleVersion,
    textForHighlighting: String,
    textBeforeIntention: String? = null,
    textAfterIntention: String? = null,
  ) {
    test(gradleVersion, CUSTOM_PROJECT) {
      codeInsightFixture.enableInspections(GradleAvoidDependencyNamedArgumentsNotationInspection::class.java)
      testHighlighting(textForHighlighting)

      // if there is a warning, check that an intention exists and can be applied
      if (textForHighlighting.contains("<weak_warning>")) {
        assertNotNull(textBeforeIntention) { "Provide 'textBeforeIntention' text, text with a warning should have a quick fix available" }
        assertNotNull(textAfterIntention) { "Provide 'textAfterIntention' text, text with a warning should have a quick fix available" }

        val textForHighlightingStripped = textForHighlighting.replace("<weak_warning>", "").replace("</weak_warning>", "")
        val textBeforeIntentionStripped = textBeforeIntention.replace("<caret>", "")
        // assert that the text used to check highlighting and the text used to apply the intention on are equal just in case
        Assertions.assertEquals(textForHighlightingStripped, textBeforeIntentionStripped) {
          "textForHighlighting and textBeforeIntention should be similar"
        }

        testIntention(textBeforeIntention, textAfterIntention, "Simplify")
      }
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testSingleString(gradleVersion: GradleVersion) {
    runTest(
      gradleVersion,
      """
      dependencies { 
        implementation 'org.gradle:gradle-core:1.0'
      }
      """.trimIndent()
    )
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testArgumentListWithSpaces(gradleVersion: GradleVersion) {
    runTest(
      gradleVersion,
      """
      dependencies { 
        implementation <weak_warning>group: 'org.gradle', name: 'gradle-core', version: '1.0'</weak_warning>
      }
      """.trimIndent(),
      """
      dependencies { 
        implementation group: 'org.gradle',<caret> name: 'gradle-core', version: '1.0'
      }
      """.trimIndent(),
      """
      dependencies { 
        implementation 'org.gradle:gradle-core:1.0'
      }
      """.trimIndent()
    )
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testArgumentListWithParentheses(gradleVersion: GradleVersion) {
    runTest(
      gradleVersion,
      """
      dependencies { 
        implementation<weak_warning>(group: 'org.gradle', name: 'gradle-core', version: '1.0')</weak_warning>
      }
      """.trimIndent(),
      """
      dependencies { 
        implementation(group: 'org.gradle',<caret> name: 'gradle-core', version: '1.0')
      }
      """.trimIndent(),
      """
      dependencies { 
        implementation('org.gradle:gradle-core:1.0')
      }
      """.trimIndent()
    )
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testArgumentsWithDoubleQuotes(gradleVersion: GradleVersion) {
    runTest(
      gradleVersion,
      """
      dependencies { 
        implementation <weak_warning>group: "org.gradle", name: "gradle-core", version: "1.0"</weak_warning>
      }
      """.trimIndent(),
      """
      dependencies { 
        implementation group: "org.gradle",<caret> name: "gradle-core", version: "1.0"
      }
      """.trimIndent(),
      """
      dependencies { 
        implementation 'org.gradle:gradle-core:1.0'
      }
      """.trimIndent()
    )
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testNonLiteralArgument(gradleVersion: GradleVersion) {
    runTest(
      gradleVersion,
      """
      var verRef = '1.0'
      dependencies { 
        implementation group: 'org.gradle', name: 'gradle-core', version: verRef
      }
      """.trimIndent()
    )
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testCustomConfiguration(gradleVersion: GradleVersion) {
    runTest(
      gradleVersion,
      """
      dependencies { 
        customConf <weak_warning>group: 'org.gradle', name: 'gradle-core', version: '1.0'</weak_warning>
      }
      """.trimIndent(),
      """
      dependencies { 
        customConf group: 'org.gradle',<caret> name: 'gradle-core', version: '1.0'
      }
      """.trimIndent(),
      """
      dependencies { 
        customConf 'org.gradle:gradle-core:1.0'
      }
      """.trimIndent()
    )
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testArgumentWithDollarInterpolation(gradleVersion: GradleVersion) {
    runTest(
      gradleVersion,
      $$"""
      var verRef = '1.0'
      dependencies { 
        implementation <weak_warning>group: 'org.gradle', name: 'gradle-core', version: "$verRef"</weak_warning>
      }
      """.trimIndent(),
      $$"""
      var verRef = '1.0'
      dependencies { 
        implementation group: 'org.gradle',<caret> name: 'gradle-core', version: "$verRef"
      }
      """.trimIndent(),
      $$"""
      var verRef = '1.0'
      dependencies { 
        implementation "org.gradle:gradle-core:$verRef"
      }
      """.trimIndent()
    )
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testExtraArgument(gradleVersion: GradleVersion) {
    runTest(
      gradleVersion,
      """
      dependencies { 
        implementation group: 'org.gradle', name: 'gradle-core', version: '1.0', configuration: 'someConf'
      }
      """.trimIndent()
    )
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testNoVersionArgument(gradleVersion: GradleVersion) {
    runTest(
      gradleVersion,
      """
      dependencies { 
        implementation <weak_warning>group: 'org.gradle', name: 'gradle-core'</weak_warning>
      }
      """.trimIndent(),
      """
      dependencies { 
        implementation group: 'org.gradle',<caret> name: 'gradle-core'
      }
      """.trimIndent(),
      """
      dependencies { 
        implementation 'org.gradle:gradle-core'
      }
      """.trimIndent()
    )
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testNoVersionButAnotherArgument(gradleVersion: GradleVersion) {
    runTest(
      gradleVersion,
      """
      dependencies { 
        implementation group: 'org.gradle', name: 'gradle-core', configuration: 'someConf'
      }
      """.trimIndent()
    )
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testWithBlock(gradleVersion: GradleVersion) {
    runTest(
      gradleVersion,
      """
      dependencies { 
        implementation<weak_warning>(group: 'org.gradle', name: 'gradle-core', version: '1.0')</weak_warning> {
          exclude(group: 'com.google.guava', module: 'guava')
        }
      }
      """.trimIndent(),
      """
      dependencies { 
        implementation(group: 'org.gradle',<caret> name: 'gradle-core', version: '1.0') {
          exclude(group: 'com.google.guava', module: 'guava')
        }
      }
      """.trimIndent(),
      """
      dependencies { 
        implementation('org.gradle:gradle-core:1.0') {
          exclude(group: 'com.google.guava', module: 'guava')
        }
      }
      """.trimIndent()
    )
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testUnusualArgumentOrder(gradleVersion: GradleVersion) {
    runTest(
      gradleVersion,
      """
      dependencies { 
        implementation <weak_warning>version: '1.0', group: 'org.gradle', name: 'gradle-core'</weak_warning>
      }
      """.trimIndent(),
      """
      dependencies { 
        implementation version: '1.0',<caret> group: 'org.gradle', name: 'gradle-core'
      }
      """.trimIndent(),
      """
      dependencies { 
        implementation 'org.gradle:gradle-core:1.0'
      }
      """.trimIndent()
    )
  }
}