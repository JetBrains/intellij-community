// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.dsl.inspections

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.codeInspection.GradleAvoidDependencyNamedArgumentsNotationInspection
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.jetbrains.plugins.gradle.testFramework.util.withBuildFile
import org.junit.jupiter.params.ParameterizedTest

class GradleAvoidDependencyNamedArgumentsNotationInspectionTest : GradleCodeInsightTestCase() {

  private fun runTest(
    gradleVersion: GradleVersion,
    test: () -> Unit,
  ) {
    test(gradleVersion, CUSTOM_PROJECT) {
      codeInsightFixture.enableInspections(GradleAvoidDependencyNamedArgumentsNotationInspection::class.java)
      test()
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testSingleString(gradleVersion: GradleVersion) {
    runTest(gradleVersion) {
      testHighlighting(
        """
        dependencies {
          implementation "org.gradle:gradle-core:1.0"
        }
        """.trimIndent()
      )
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testArgumentListWithSpaces(gradleVersion: GradleVersion) {
    runTest(gradleVersion) {
      testHighlighting(
        """
        dependencies {
          implementation ${WARNING_START}group: 'org.gradle', name: 'gradle-core', version: '1.0'$WARNING_END
        }
        """.trimIndent()
      )
      testIntention(
        """
        dependencies {
          implementation group: 'org.gradle',<caret> name: 'gradle-core', version: '1.0'
        }
        """.trimIndent(),
        """
        dependencies {
          implementation "org.gradle:gradle-core:1.0"
        }
        """.trimIndent(),
        "Simplify"
      )
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testArgumentListWithParentheses(gradleVersion: GradleVersion) {
    runTest(gradleVersion) {
      testHighlighting(
        """
        dependencies {
          implementation$WARNING_START(group: 'org.gradle', name: 'gradle-core', version: '1.0')$WARNING_END
        }
        """.trimIndent()
      )
      testIntention(
        """
        dependencies {
          implementation(group: 'org.gradle',<caret> name: 'gradle-core', version: '1.0')
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
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testArgumentsWithDoubleQuotes(gradleVersion: GradleVersion) {
    runTest(gradleVersion) {
      testHighlighting(
        """
        dependencies {
          implementation ${WARNING_START}group: "org.gradle", name: "gradle-core", version: "1.0"$WARNING_END
        }
        """.trimIndent()
      )
      testIntention(
        """
        dependencies {
          implementation group: "org.gradle",<caret> name: "gradle-core", version: "1.0"
        }
        """.trimIndent(),
        """
        dependencies {
          implementation "org.gradle:gradle-core:1.0"
        }
        """.trimIndent(),
        "Simplify"
      )
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testNonLiteralArgument(gradleVersion: GradleVersion) {
    runTest(gradleVersion) {
      testHighlighting(
        """
        var verRef = '1.0'
        dependencies {
          implementation group: 'org.gradle', name: 'gradle-core', version: verRef
        }
        """.trimIndent()
      )
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testCustomConfiguration(gradleVersion: GradleVersion) {
    runTest(gradleVersion) {
      testHighlighting(
        """
        dependencies { 
          customConf ${WARNING_START}group: 'org.gradle', name: 'gradle-core', version: '1.0'$WARNING_END
        }
        """.trimIndent()
      )
      testIntention(
        """
        dependencies { 
          customConf group: 'org.gradle',<caret> name: 'gradle-core', version: '1.0'
        }
        """.trimIndent(),
        """
        dependencies { 
          customConf "org.gradle:gradle-core:1.0"
        }
        """.trimIndent(),
        "Simplify"
      )
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testArgumentWithDollarInterpolation(gradleVersion: GradleVersion) {
    runTest(gradleVersion) {
      testHighlighting(
        $$"""
        var verRef = '1.0'
        dependencies { 
          implementation $${WARNING_START}group: 'org.gradle', name: 'gradle-core', version: "$verRef"$$WARNING_END
        }
        """.trimIndent()
      )
      testIntention(
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
        """.trimIndent(),
        "Simplify"
      )
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testTripleQuoteArguments(gradleVersion: GradleVersion) {
    runTest(gradleVersion) {
      testHighlighting("""
        dependencies {
          implementation ${WARNING_START}group: ${'"'}""org.gradle""${'"'}, name: '''gradle-core''', version: '1.0'$WARNING_END
        }
        """.trimIndent()
      )
      testIntention("""
        dependencies {
          implementation group: ${'"'}""org.gradle""${'"'},<caret> name: '''gradle-core''', version: '1.0'
        }
        """.trimIndent(),
                    """
        dependencies {
          implementation "org.gradle:gradle-core:1.0"
        }
        """.trimIndent(),
                    "Simplify"
      )
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testTripleQuoteArgumentsWithInterpolation(gradleVersion: GradleVersion) {
    runTest(gradleVersion) {
      testHighlighting(
        $$"""
        def gradle = "org.gradle"
        dependencies {
          implementation $${WARNING_START}group: $${'"'}""$gradle""$${'"'}, name: '''gr\$dle-core''', version: "1.0"$$WARNING_END
        }
        """.trimIndent()
      )
      testIntention(
        $$"""
        def gradle = "org.gradle"
        dependencies {
          implementation group: $${'"'}""$gradle""$${'"'},<caret> name: '''gr\$dle-core''', version: "1.0"
        }
        """.trimIndent(),
        $$"""
        def gradle = "org.gradle"
        dependencies {
          implementation "$gradle:gr\$dle-core:1.0"
        }
        """.trimIndent(),
        "Simplify"
      )
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testExtraArgument(gradleVersion: GradleVersion) {
    runTest(gradleVersion) {
      testHighlighting(
        """
        dependencies { 
          implementation group: 'org.gradle', name: 'gradle-core', version: '1.0', configuration: 'someConf'
        }
        """.trimIndent()
      )
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testNoVersionArgument(gradleVersion: GradleVersion) {
    runTest(gradleVersion) {
      testHighlighting(
        """
        dependencies { 
          implementation ${WARNING_START}group: 'org.gradle', name: 'gradle-core'$WARNING_END
        }
        """.trimIndent()
      )
      testIntention(
        """
        dependencies { 
          implementation group: 'org.gradle',<caret> name: 'gradle-core'
        }
        """.trimIndent(),
        """
        dependencies { 
          implementation "org.gradle:gradle-core"
        }
        """.trimIndent(),
        "Simplify"
      )
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testNoVersionButAnotherArgument(gradleVersion: GradleVersion) {
    runTest(gradleVersion) {
      testHighlighting(
        """
        dependencies { 
          implementation group: 'org.gradle', name: 'gradle-core', configuration: 'someConf'
        }
        """.trimIndent()
      )
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testWithBlock(gradleVersion: GradleVersion) {
    runTest(gradleVersion) {
      testHighlighting(
        """
        dependencies { 
          implementation$WARNING_START(group: 'org.gradle', name: 'gradle-core', version: '1.0')$WARNING_END {
            exclude(group: 'com.google.guava', module: 'guava')
          }
        }
        """.trimIndent()
      )
      testIntention(
        """
        dependencies { 
          implementation(group: 'org.gradle',<caret> name: 'gradle-core', version: '1.0') {
            exclude(group: 'com.google.guava', module: 'guava')
          }
        }
        """.trimIndent(),
        """
        dependencies { 
          implementation("org.gradle:gradle-core:1.0") {
            exclude(group: 'com.google.guava', module: 'guava')
          }
        }
        """.trimIndent(),
        "Simplify"
      )
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testUnusualArgumentOrder(gradleVersion: GradleVersion) {
    runTest(gradleVersion) {
      testHighlighting(
        """
        dependencies { 
          implementation ${WARNING_START}version: '1.0', group: 'org.gradle', name: 'gradle-core'$WARNING_END
        }
        """.trimIndent()
      )
      testIntention(
        """
        dependencies { 
          implementation version: '1.0',<caret> group: 'org.gradle', name: 'gradle-core'
        }
        """.trimIndent(),
        """
        dependencies { 
          implementation "org.gradle:gradle-core:1.0"
        }
        """.trimIndent(),
        "Simplify"
      )
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testListOfMaps(gradleVersion: GradleVersion) {
    runTest(gradleVersion) {
      testHighlighting(
        """
        dependencies { 
          implementation(
                  $WARNING_START[group: 'org.gradle', name: 'gradle-core', version: '1.0']$WARNING_END,
                  'com.fasterxml.jackson.core:jackson-databind:2.17.0',
                  $WARNING_START[group: 'com.google.guava', name: 'guava', version: '32.1.3-jre']$WARNING_END
          )
        }
        """.trimIndent()
      )
      testIntention(
        """
        dependencies { 
          implementation(
                  <caret>[group: 'org.gradle', name: 'gradle-core', version: '1.0'],
                  'com.fasterxml.jackson.core:jackson-databind:2.17.0',
                  [group: 'com.google.guava', name: 'guava', version: '32.1.3-jre']
          )
        }
        """.trimIndent(),
        """
        dependencies { 
          implementation(
                  "org.gradle:gradle-core:1.0",
                  'com.fasterxml.jackson.core:jackson-databind:2.17.0',
                  [group: 'com.google.guava', name: 'guava', version: '32.1.3-jre']
          )
        }
        """.trimIndent(),
        "Simplify"
      )
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testListOfMapsUnusualOrder(gradleVersion: GradleVersion) {
    runTest(gradleVersion) {
      testHighlighting(
        """
        dependencies { 
          implementation(
                  $WARNING_START[name: 'gradle-core', group: 'org.gradle', version: '1.0']$WARNING_END,
                  $WARNING_START[version: '32.1.3-jre', group: 'com.google.guava', name: 'guava']$WARNING_END
          )
        }
        """.trimIndent()
      )
      testIntention(
        """
        dependencies { 
          implementation(
                  [name: 'gradle-core', group: 'org.gradle', version: '1.0'],
                  <caret>[version: '32.1.3-jre', group: 'com.google.guava', name: 'guava']
          )
        }
        """.trimIndent(),
        """
        dependencies { 
          implementation(
                  [name: 'gradle-core', group: 'org.gradle', version: '1.0'],
                  "com.google.guava:guava:32.1.3-jre"
          )
        }
        """.trimIndent(),
        "Simplify"
      )
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testListOfMapsMissingVersion(gradleVersion: GradleVersion) {
    runTest(gradleVersion) {
      testHighlighting(
        """
        dependencies { 
          implementation(
                  $WARNING_START[name: 'gradle-core', group: 'org.gradle']$WARNING_END,
                  $WARNING_START[group: 'com.google.guava', name: 'guava']$WARNING_END
          )
        }
        """.trimIndent()
      )
      testIntention(
        """
        dependencies { 
          implementation(
                  [name: 'gradle-core', group: 'org.gradle'],
                  <caret>[group: 'com.google.guava', name: 'guava']
          )
        }
        """.trimIndent(),
        """
        dependencies { 
          implementation(
                  [name: 'gradle-core', group: 'org.gradle'],
                  "com.google.guava:guava"
          )
        }
        """.trimIndent(),
        "Simplify"
      )
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testListOfMapsExtraArgument(gradleVersion: GradleVersion) {
    runTest(gradleVersion) {
      testHighlighting(
        """
        dependencies { 
          implementation(
                  [group: 'org.gradle', name: 'gradle-core', version: '1.0', configuration: 'someConf'],
                  [group: 'com.google.guava', name: 'guava', configuration: 'someConf']
          )
        }
        """.trimIndent()
      )
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testListOfMapsWithVariables(gradleVersion: GradleVersion) {
    runTest(gradleVersion) {
      testHighlighting(
        $"""
        def ver = '1.0'
        def name = 'guava'
        dependencies {
          implementation(
                  [group: 'org.gradle', name: 'gradle-core', version: ver],
                  [group: 'com.google.guava', name: name, version: '32.1.3-jre']
          )
        }
        """.trimIndent()
      )
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testListOfMapsWithDollarInterpolation(gradleVersion: GradleVersion) {
    runTest(gradleVersion) {
      testHighlighting(
        $$"""
        def ver = '1.0'
        def name = 'guava'
        dependencies {
          implementation(
                  $$WARNING_START[group: 'org.gradle', name: 'gradle-core', version: "$ver"]$$WARNING_END,
                  $$WARNING_START[group: 'com.google.guava', name: "$name", version: '32.1.3-jre']$$WARNING_END
          )
        }
        """.trimIndent()
      )
      testIntention(
        $$"""
        def ver = '1.0'
        def name = 'guava'
        dependencies {
          implementation(
                  <caret>[group: 'org.gradle', name: 'gradle-core', version: "$ver"],
                  [group: 'com.google.guava', name: "$name", version: '32.1.3-jre']
          )
        }
        """.trimIndent(),
        $$"""
        def ver = '1.0'
        def name = 'guava'
        dependencies {
          implementation(
                  "org.gradle:gradle-core:$ver",
                  [group: 'com.google.guava', name: "$name", version: '32.1.3-jre']
          )
        }
        """.trimIndent(),
        "Simplify"
      )
    }
  }

  companion object {
    private const val WARNING_START = "<weak_warning>"
    private const val WARNING_END = "</weak_warning>"
    private val CUSTOM_PROJECT = GradleTestFixtureBuilder.create("avoid_named_arguments") { gradleVersion ->
      withBuildFile(gradleVersion) {
        withJavaPlugin()
        withPrefix {
          call("configurations") {
            code("customConf")
          }
        }
      }
    }
  }
}