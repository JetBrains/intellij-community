// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.dsl.notations

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.codeInspection.GradleIncorrectDependencyNotationArgumentInspection
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.jetbrains.plugins.gradle.testFramework.util.assumeThatGradleIsOlderThan
import org.junit.jupiter.params.ParameterizedTest

class GradleDependencyNotationTest : GradleCodeInsightTestCase() {

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testEmptyString(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      codeInsightFixture.enableInspections(GradleIncorrectDependencyNotationArgumentInspection::class.java)
      testHighlighting("dependencies { implementation(<warning>''</warning>) }")
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testIncorrectString(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      codeInsightFixture.enableInspections(GradleIncorrectDependencyNotationArgumentInspection::class.java)
      testHighlighting("dependencies { implementation(<warning>'ab'</warning>) }")
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testIncorrectArgument(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      codeInsightFixture.enableInspections(GradleIncorrectDependencyNotationArgumentInspection::class.java)
      testHighlighting("dependencies { implementation(<warning>1</warning>) }")
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testRecognizeFiles(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      codeInsightFixture.enableInspections(GradleIncorrectDependencyNotationArgumentInspection::class.java)
      testHighlighting("dependencies { implementation(files('.')) }")
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testRecognizeProjects(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      codeInsightFixture.enableInspections(GradleIncorrectDependencyNotationArgumentInspection::class.java)
      testHighlighting("dependencies { implementation(project(':')) }")
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun testRecognizeDependency(gradleVersion: GradleVersion) {
    assumeThatGradleIsOlderThan(gradleVersion, "9.0") {
      """
      ClientModule dependencies were a legacy precursor to ComponentMetadataRules, and have since been replaced and removed in Gradle 9.0.
      See gradle/pull/32743 for more information. 
      """.trimIndent()
    }
    testJavaProject(gradleVersion) {
      codeInsightFixture.enableInspections(GradleIncorrectDependencyNotationArgumentInspection::class.java)
      testHighlighting("dependencies { implementation(module('org.apache.groovy:groovy:4.0.0')) }")
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testRecognizeProvider(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      codeInsightFixture.enableInspections(GradleIncorrectDependencyNotationArgumentInspection::class.java)
      testHighlighting("""
        def x = provider({ "org.apache.groovy:groovy:4.0.0"})
        dependencies { implementation(x) }
        """.trimIndent())
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testRecognizeMap(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      codeInsightFixture.enableInspections(GradleIncorrectDependencyNotationArgumentInspection::class.java)
      testHighlighting("dependencies { implementation name: '', group: '', version: '' }")
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testAbsentName(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      codeInsightFixture.enableInspections(GradleIncorrectDependencyNotationArgumentInspection::class.java)
      testHighlighting("dependencies { implementation <warning>group: '', version: ''</warning> }")
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testAbsentGroup(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      codeInsightFixture.enableInspections(GradleIncorrectDependencyNotationArgumentInspection::class.java)
      testHighlighting("dependencies { implementation <warning>name: '', version: ''</warning> }")
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testAbsentVersion(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      codeInsightFixture.enableInspections(GradleIncorrectDependencyNotationArgumentInspection::class.java)
      testHighlighting("dependencies { implementation <warning>name: '', group: ''</warning> }")
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testList(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      codeInsightFixture.enableInspections(GradleIncorrectDependencyNotationArgumentInspection::class.java)
      testHighlighting("dependencies { implementation(['org.apache.groovy:groovy:4.0.0']) }")
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testProviderConvertible(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      codeInsightFixture.enableInspections(GradleIncorrectDependencyNotationArgumentInspection::class.java)
      testHighlighting("""
        |class A implements ProviderConvertible<ExternalModuleDependency> {
        |   Provider<ExternalModuleDependency> asProvider() { null }
        | }
        |dependencies { implementation(new A()) }""".trimMargin())
    }
  }

}