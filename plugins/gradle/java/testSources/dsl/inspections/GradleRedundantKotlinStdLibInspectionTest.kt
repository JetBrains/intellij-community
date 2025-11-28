// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.dsl.inspections

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.codeInspection.GradleRedundantKotlinStdLibInspection
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.testFramework.util.assumeThatGradleIsAtLeast
import org.jetbrains.plugins.gradle.testFramework.util.withBuildFile
import org.junit.jupiter.params.ParameterizedTest

class GradleRedundantKotlinStdLibInspectionTest : GradleCodeInsightTestCase() {

  private fun runTest(
    gradleVersion: GradleVersion,
    projectFixture: GradleTestFixtureBuilder,
    test: () -> Unit,
  ) {
    assumeThatGradleIsAtLeast(gradleVersion, "9.0.0") { "Best practice added in Gradle 9.0.0" }
    test(gradleVersion, projectFixture) {
      codeInsightFixture.enableInspections(GradleRedundantKotlinStdLibInspection::class.java)
      test()
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun testSameVersion(gradleVersion: GradleVersion) {
    runTest(gradleVersion, DEFAULT_FIXTURE) {
      testHighlighting(
        """
        plugins { id 'org.jetbrains.kotlin.jvm' version '2.2.0' }
        dependencies { 
            <warning>api 'org.jetbrains.kotlin:kotlin-stdlib:2.2.0'</warning>
        }
        """.trimIndent()
      )
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun testDifferentVersion(gradleVersion: GradleVersion) {
    runTest(gradleVersion, DEFAULT_FIXTURE) {
      testHighlighting(
        """
        plugins { id 'org.jetbrains.kotlin.jvm' version '2.2.0' }
        dependencies { 
            api 'org.jetbrains.kotlin:kotlin-stdlib:2.1.0'
        }
        """.trimIndent()
      )
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun testDependencyNoVersion(gradleVersion: GradleVersion) {
    runTest(gradleVersion, DEFAULT_FIXTURE) {
      testHighlighting(
        """
        plugins { id 'org.jetbrains.kotlin.jvm' version '2.2.0' }
        dependencies { 
            api 'org.jetbrains.kotlin:kotlin-stdlib'
        }
        """.trimIndent()
      )
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun testDependencyNamedArguments(gradleVersion: GradleVersion) {
    runTest(gradleVersion, DEFAULT_FIXTURE) {
      testHighlighting(
        """
        plugins { id 'org.jetbrains.kotlin.jvm' version '2.2.0' }
        dependencies { 
            <warning>api group: 'org.jetbrains.kotlin', name: 'kotlin-stdlib', version: '2.2.0'</warning>
        }
        """.trimIndent()
      )
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun testDependencyNamedArgumentsNoVersion(gradleVersion: GradleVersion) {
    runTest(gradleVersion, DEFAULT_FIXTURE) {
      testHighlighting(
        """
        plugins { id 'org.jetbrains.kotlin.jvm' version '2.2.0' }
        dependencies { 
            api group: 'org.jetbrains.kotlin', name: 'kotlin-stdlib'
        }
        """.trimIndent()
      )
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun testDifferentConfiguration(gradleVersion: GradleVersion) {
    runTest(gradleVersion, DEFAULT_FIXTURE) {
      testHighlighting(
        """
        plugins { id 'org.jetbrains.kotlin.jvm' version '2.2.0' }
        dependencies { 
            <warning>implementation 'org.jetbrains.kotlin:kotlin-stdlib:2.2.0'</warning>
        }
        """.trimIndent()
      )
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun testCustomConfiguration(gradleVersion: GradleVersion) {
    runTest(gradleVersion, DEFAULT_FIXTURE) {
      testHighlighting(
        """
        plugins { id 'org.jetbrains.kotlin.jvm' version '2.2.0' }
        configurations { customConf }
        dependencies { 
            <warning>customConf 'org.jetbrains.kotlin:kotlin-stdlib:2.2.0'</warning>
        }
        """.trimIndent()
      )
    }
  }

  // should not warn as the overriding kotlin-stdlib dependency is a bit different
  @ParameterizedTest
  @AllGradleVersionsSource
  fun testDependencyWithExtraArgumentsInClosure(gradleVersion: GradleVersion) {
    runTest(gradleVersion, DEFAULT_FIXTURE) {
      testHighlighting(
        """
        plugins { id 'org.jetbrains.kotlin.jvm' version '2.2.0' }
        dependencies { 
            api('org.jetbrains.kotlin:kotlin-stdlib:2.2.0') {
                exclude(group: 'org.jetbrains', module: 'annotations')
            }
        }
        """.trimIndent()
      )
    }
  }

  // should not warn as the overriding kotlin-stdlib dependency is a bit different
  @ParameterizedTest
  @AllGradleVersionsSource
  fun testDependencyWithExtraArgumentsInMap(gradleVersion: GradleVersion) {
    runTest(gradleVersion, DEFAULT_FIXTURE) {
      testHighlighting(
        """
        plugins { id 'org.jetbrains.kotlin.jvm' version '2.2.0' }
        dependencies { 
            api group: 'org.jetbrains.kotlin', name: 'kotlin-stdlib', version: '2.2.0', because: 'why not'
        }
        """.trimIndent()
      )
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun testDependencyListSingleStrings(gradleVersion: GradleVersion) {
    runTest(gradleVersion, DEFAULT_FIXTURE) {
      testHighlighting(
        """
        plugins { id 'org.jetbrains.kotlin.jvm' version '2.2.0' }
        dependencies { 
            api <warning>'org.jetbrains.kotlin:kotlin-stdlib:2.2.0'</warning>, 'com.fasterxml.jackson.core:jackson-databind:2.17.0'
        }
        """.trimIndent()
      )
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun testDependencyListSingleStringsInBrackets(gradleVersion: GradleVersion) {
    runTest(gradleVersion, DEFAULT_FIXTURE) {
      testHighlighting(
        """
        plugins { id 'org.jetbrains.kotlin.jvm' version '2.2.0' }
        dependencies { 
            api(<warning>['org.jetbrains.kotlin:kotlin-stdlib:2.2.0']</warning>, ['com.fasterxml.jackson.core:jackson-databind:2.17.0'])
        }
        """.trimIndent()
      )
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun testDependencyListMaps(gradleVersion: GradleVersion) {
    runTest(gradleVersion, DEFAULT_FIXTURE) {
      testHighlighting(
        """
        plugins { id 'org.jetbrains.kotlin.jvm' version '2.2.0' }
        dependencies { 
            api(
                <warning>[group: 'org.jetbrains.kotlin', name: 'kotlin-stdlib', version: '2.2.0']</warning>,
                [group: 'com.fasterxml.jackson.core', name: 'jackson-databind', version: '2.17.0'],
                <warning>'org.jetbrains.kotlin:kotlin-stdlib:2.2.0'</warning>,
                'com.fasterxml.jackson.core:jackson-databind:2.17.0'
            )
        }
        """.trimIndent()
      )
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun testDependencyListMapsExtraArguments(gradleVersion: GradleVersion) {
    runTest(gradleVersion, DEFAULT_FIXTURE) {
      testHighlighting(
        """
        plugins { id 'org.jetbrains.kotlin.jvm' version '2.2.0' }
        dependencies { 
            api(
                [group: 'org.jetbrains.kotlin', name: 'kotlin-stdlib', version: '2.2.0', because: 'why not'],
                [group: 'com.fasterxml.jackson.core', name: 'jackson-databind', version: '2.17.0']
            )
        }
        """.trimIndent()
      )
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun testCompileOnly(gradleVersion: GradleVersion) {
    runTest(gradleVersion, DEFAULT_FIXTURE) {
      testHighlighting(
        """
        plugins { id 'org.jetbrains.kotlin.jvm' version '2.2.0' }
        dependencies { 
            compileOnly 'org.jetbrains.kotlin:kotlin-stdlib:2.2.0'
        }
        """.trimIndent()
      )
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun testCompileOnlyCustomSourceSet(gradleVersion: GradleVersion) {
    runTest(gradleVersion, DEFAULT_FIXTURE) {
      testHighlighting(
        """
        plugins { id 'org.jetbrains.kotlin.jvm' version '2.2.0' }
        sourceSets { customSourceSet }
        dependencies { 
            customSourceSetCompileOnly 'org.jetbrains.kotlin:kotlin-stdlib:2.2.0'
        }
        """.trimIndent()
      )
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun testDisabledDefaultStdLib(gradleVersion: GradleVersion) {
    runTest(gradleVersion, DISABLED_DEFAULT_STDLIB_FIXTURE) {
      testHighlighting(
        """
        plugins { id 'org.jetbrains.kotlin.jvm' version '2.2.0' }
        dependencies { 
            api 'org.jetbrains.kotlin:kotlin-stdlib:2.2.0'
        }
        """.trimIndent()
      )
    }
  }

  // VERSION CATALOG RESOLVING TESTS

  @ParameterizedTest
  @AllGradleVersionsSource
  fun testDependencyFromVersionCatalog(gradleVersion: GradleVersion) {
    runTest(gradleVersion, DEFAULT_FIXTURE) {
      testHighlighting(
        """
        plugins { id 'org.jetbrains.kotlin.jvm' version '2.2.0' }
        dependencies { 
            <warning>api libs.kotlin.std.lib1</warning>
        }
        """.trimIndent()
      )
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun testDependencyFromVersionCatalogNoVersion(gradleVersion: GradleVersion) {
    runTest(gradleVersion, DEFAULT_FIXTURE) {
      testHighlighting(
        """
        plugins { id 'org.jetbrains.kotlin.jvm' version '2.2.0' }
        dependencies { 
            api libs.kotlin.std.lib2
        }
        """.trimIndent()
      )
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun testDependencyFromVersionCatalogModuleAndVersion(gradleVersion: GradleVersion) {
    runTest(gradleVersion, DEFAULT_FIXTURE) {
      testHighlighting(
        """
        plugins { id 'org.jetbrains.kotlin.jvm' version '2.2.0' }
        dependencies { 
            <warning>api libs.kotlin.std.lib3</warning>
        }
        """.trimIndent()
      )
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun testDependencyFromVersionCatalogFull(gradleVersion: GradleVersion) {
    runTest(gradleVersion, DEFAULT_FIXTURE) {
      testHighlighting(
        """
        plugins { id 'org.jetbrains.kotlin.jvm' version '2.2.0' }
        dependencies { 
            <warning>api libs.kotlin.std.lib4</warning>
        }
        """.trimIndent()
      )
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun testDependencyFromVersionCatalogFullVersionReference(gradleVersion: GradleVersion) {
    runTest(gradleVersion, DEFAULT_FIXTURE) {
      testHighlighting(
        """
        plugins { id 'org.jetbrains.kotlin.jvm' version '2.2.0' }
        dependencies { 
            <warning>api libs.kotlin.std.lib5</warning>
        }
        """.trimIndent()
      )
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun testDependencyFromVersionCatalogMultilineString(gradleVersion: GradleVersion) {
    runTest(gradleVersion, DEFAULT_FIXTURE) {
      testHighlighting(
        """
        plugins { id 'org.jetbrains.kotlin.jvm' version '2.2.0' }
        dependencies { 
            <warning>api libs.kotlin.std.multiline</warning>
        }
        """.trimIndent()
      )
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun testDependencyFromVersionCatalogCustomConfiguration(gradleVersion: GradleVersion) {
    runTest(gradleVersion, DEFAULT_FIXTURE) {
      testHighlighting(
        """
        plugins { id 'org.jetbrains.kotlin.jvm' version '2.2.0' }
        configurations { customConf }
        dependencies { 
            <warning>customConf libs.kotlin.std.lib1</warning>
        }
        """.trimIndent()
      )
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun testDependencyListVersionCatalog(gradleVersion: GradleVersion) {
    runTest(gradleVersion, DEFAULT_FIXTURE) {
      testHighlighting(
        """
        plugins { id 'org.jetbrains.kotlin.jvm' version '2.2.0' }
        dependencies { 
            api <warning>libs.kotlin.std.lib1</warning>, 'com.fasterxml.jackson.core:jackson-databind:2.17.0'
        }
        """.trimIndent()
      )
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun testDependencyListVersionCatalogInBrackets(gradleVersion: GradleVersion) {
    runTest(gradleVersion, DEFAULT_FIXTURE) {
      testHighlighting(
        """
        plugins { id 'org.jetbrains.kotlin.jvm' version '2.2.0' }
        dependencies { 
            api(<warning>[libs.kotlin.std.lib1]</warning>, ['com.fasterxml.jackson.core:jackson-databind:2.17.0'])
        }
        """.trimIndent()
      )
    }
  }

  // PLUGIN DETECTION

  @ParameterizedTest
  @AllGradleVersionsSource
  fun testNoPlugin(gradleVersion: GradleVersion) {
    runTest(gradleVersion, DEFAULT_FIXTURE) {
      testHighlighting(
        """
        dependencies { 
            api 'org.jetbrains.kotlin:kotlin-stdlib:2.2.0'
        }
        """.trimIndent()
      )
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun testDifferentPlugin(gradleVersion: GradleVersion) {
    runTest(gradleVersion, DEFAULT_FIXTURE) {
      testHighlighting(
        """
        plugins { id 'java' }
        dependencies { 
            api 'org.jetbrains.kotlin:kotlin-stdlib:2.2.0'
        }
        """.trimIndent()
      )
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun testPluginIdNoVersion(gradleVersion: GradleVersion) {
    runTest(gradleVersion, DEFAULT_FIXTURE) {
      testHighlighting(
        """
        plugins { id 'org.jetbrains.kotlin.jvm' }
        dependencies { 
            api 'org.jetbrains.kotlin:kotlin-stdlib:2.2.0'
        }
        """.trimIndent()
      )
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun testPluginFromVersionCatalog(gradleVersion: GradleVersion) {
    runTest(gradleVersion, DEFAULT_FIXTURE) {
      testHighlighting(
        """
        plugins { alias(libs.plugins.kotlinJvm) }
        dependencies { 
            <warning>api 'org.jetbrains.kotlin:kotlin-stdlib:2.2.0'</warning>
        }
        """.trimIndent()
      )
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun testPluginFromVersionCatalogFull(gradleVersion: GradleVersion) {
    runTest(gradleVersion, DEFAULT_FIXTURE) {
      testHighlighting(
        """
        plugins { alias(libs.plugins.kotlinJvmFull) }
        dependencies { 
            <warning>api 'org.jetbrains.kotlin:kotlin-stdlib:2.2.0'</warning>
        }
        """.trimIndent()
      )
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun testPluginFromVersionCatalogFullWithVersionReference(gradleVersion: GradleVersion) {
    runTest(gradleVersion, DEFAULT_FIXTURE) {
      testHighlighting(
        """
        plugins { alias(libs.plugins.kotlinJvmFullRef) }
        dependencies { 
            <warning>api 'org.jetbrains.kotlin:kotlin-stdlib:2.2.0'</warning>
        }
        """.trimIndent()
      )
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun testPluginIdNotApplied(gradleVersion: GradleVersion) {
    runTest(gradleVersion, DEFAULT_FIXTURE) {
      testHighlighting(
        """
        plugins { 
            id 'org.jetbrains.kotlin.jvm' version '2.2.0' apply false 
        }
        dependencies { 
            api 'org.jetbrains.kotlin:kotlin-stdlib:2.2.0'
        }
        """.trimIndent()
      )
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun testPluginFromVersionCatalogNotApplied(gradleVersion: GradleVersion) {
    runTest(gradleVersion, DEFAULT_FIXTURE) {
      testHighlighting(
        """
        plugins { 
            alias(libs.plugins.kotlinJvm) apply false
        }
        dependencies { 
            api 'org.jetbrains.kotlin:kotlin-stdlib:2.2.0'
        }
        """.trimIndent()
      )
    }
  }

  // QUICK FIX TESTS

  @ParameterizedTest
  @AllGradleVersionsSource
  fun testQuickFixRemove(gradleVersion: GradleVersion) {
    runTest(gradleVersion, DEFAULT_FIXTURE) {
      testIntention(
        """
        plugins { id 'org.jetbrains.kotlin.jvm' version '2.2.0' }
        dependencies {
            <caret>api 'org.jetbrains.kotlin:kotlin-stdlib:2.2.0'
        }
        """.trimIndent(),
        """
        plugins { id 'org.jetbrains.kotlin.jvm' version '2.2.0' }
        dependencies {
        }
        """.trimIndent(),
        "Remove dependency"
      )
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun testQuickFixRemoveSingleStringFromList(gradleVersion: GradleVersion) {
    runTest(gradleVersion, DEFAULT_FIXTURE) {
      testIntention(
        """
        plugins { id 'org.jetbrains.kotlin.jvm' version '2.2.0' }
        dependencies {
            api <caret>'org.jetbrains.kotlin:kotlin-stdlib:2.2.0', 'com.fasterxml.jackson.core:jackson-databind:2.17.0'
        }
        """.trimIndent(),
        """
        plugins { id 'org.jetbrains.kotlin.jvm' version '2.2.0' }
        dependencies {
            api 'com.fasterxml.jackson.core:jackson-databind:2.17.0'
        }
        """.trimIndent(),
        "Remove dependency"
      )
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun testQuickFixRemoveMapFromList(gradleVersion: GradleVersion) {
    runTest(gradleVersion, DEFAULT_FIXTURE) {
      testIntention(
        """
        plugins { id 'org.jetbrains.kotlin.jvm' version '2.2.0' }
        dependencies {
            api(
                    <caret>[group: 'org.jetbrains.kotlin', name: 'kotlin-stdlib', version: '2.2.0'],
                    [group: 'com.fasterxml.jackson.core', name: 'jackson-databind', version: '2.17.0'],
                    'com.fasterxml.jackson.core:jackson-databind:2.17.0'
            )
        }
        """.trimIndent(),
        """
        plugins { id 'org.jetbrains.kotlin.jvm' version '2.2.0' }
        dependencies {
            api(
                    [group: 'com.fasterxml.jackson.core', name: 'jackson-databind', version: '2.17.0'],
                    'com.fasterxml.jackson.core:jackson-databind:2.17.0'
            )
        }
        """.trimIndent(),
        "Remove dependency"
      )
    }
  }

  companion object {
    private val DEFAULT_FIXTURE = GradleTestFixtureBuilder.create("redundant_kotlin_stdlib") { gradleVersion ->
      withFile(
        "gradle/libs.versions.toml", /* language=TOML */ """
          [versions]
          kotlin = "2.2.0"
          [plugins]
          kotlinJvm = "org.jetbrains.kotlin.jvm:2.2.0"
          kotlinJvmFull = { id = "org.jetbrains.kotlin.jvm", version = "2.2.0" }
          kotlinJvmFullRef = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
          [libraries]
          kotlin-std-lib1 = "org.jetbrains.kotlin:kotlin-stdlib:2.2.0"
          kotlin-std-lib2.module = "org.jetbrains.kotlin:kotlin-stdlib"
          kotlin-std-lib3 = { module = "org.jetbrains.kotlin:kotlin-stdlib", version = "2.2.0" }
          kotlin-std-lib4 = { group = "org.jetbrains.kotlin", name = "kotlin-stdlib", version = "2.2.0" }
          kotlin-std-lib5 = { group = "org.jetbrains.kotlin", name = "kotlin-stdlib", version.ref = "kotlin" }
          kotlin-std-multiline = ""${'"'}org.jetbrains.kotlin
          :kotlin-stdlib
          :2.2.0
          ""${'"'}
          """.trimIndent()
      )
      withBuildFile(gradleVersion) {
        withKotlinJvmPlugin("2.2.0")
        withPrefix {
          call("configurations") {
            code("customConf")
          }
          call("sourceSets") {
            code("customSourceSet")
          }
        }
      }
    }
    private val DISABLED_DEFAULT_STDLIB_FIXTURE = GradleTestFixtureBuilder.create("disabled_default_stdlib") { gradleVersion ->
      withBuildFile(gradleVersion) {
        withKotlinJvmPlugin("2.2.0")
      }
      withFile("gradle.properties", "kotlin.stdlib.default.dependency=false")
    }
  }
}