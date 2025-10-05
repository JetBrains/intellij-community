// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.dsl

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.codeInspection.GradlePluginDslStructureInspection
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.junit.jupiter.params.ParameterizedTest

class GradlePluginDslStructureTest : GradleCodeInsightTestCase() {

  private fun doTest(gradleVersion: GradleVersion, action: () -> Unit) {
    testEmptyProject(gradleVersion) {
      codeInsightFixture.enableInspections(GradlePluginDslStructureInspection::class.java)
      action()
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test disallowed statement`(gradleVersion: GradleVersion) {
    doTest(gradleVersion) {
      testHighlighting("""
        <error>apply</error> plugin: 'java'
        plugins {}
        """.trimIndent())
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test allowed statement`(gradleVersion: GradleVersion) {
    doTest(gradleVersion) {
      testHighlighting("""
        buildscript {}
        plugins {}
        """.trimIndent())
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test import`(gradleVersion: GradleVersion) {
    doTest(gradleVersion) {
      testHighlighting("""
        <warning>import java.math.BigInteger</warning>
        plugins {}
        """.trimIndent())
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test println`(gradleVersion: GradleVersion) {
    doTest(gradleVersion) {
      testHighlighting("""
        plugins {
          <error>println</error> "20"
        }
        """.trimIndent())
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test bare version`(gradleVersion: GradleVersion) {
    doTest(gradleVersion) {
      testHighlighting("""
        plugins {
          <error>version</error> "20"
        }
        """.trimIndent())
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test bare apply`(gradleVersion: GradleVersion) {
    doTest(gradleVersion) {
      testHighlighting("""
        plugins {
          <error>apply</error> "20"
        }
        """.trimIndent())
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test version and apply`(gradleVersion: GradleVersion) {
    doTest(gradleVersion) {
      testHighlighting("""
        plugins {
          <error>version</error> "20" apply 30
        }
        """.trimIndent())
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test id`(gradleVersion: GradleVersion) {
    doTest(gradleVersion) {
      testHighlighting("""
        plugins {
          id 'java'
        }
        """.trimIndent())
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test id version`(gradleVersion: GradleVersion) {
    doTest(gradleVersion) {
      // todo: this is incorrect code
      testHighlighting("""
        plugins {
          id 'java' version '1'
        }
        """.trimIndent())
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test id version apply`(gradleVersion: GradleVersion) {
    doTest(gradleVersion) {
      testHighlighting("""
        plugins {
          id 'java' version '1' apply true
        }
        """.trimIndent())
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test alias`(gradleVersion: GradleVersion) {
    doTest(gradleVersion) {
      testHighlighting("""
        plugins {
          alias(libs.groovy)
        }
        """.trimIndent())
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test expression lambda`(gradleVersion: GradleVersion) {
    doTest(gradleVersion) {
      testHighlighting("""
        plugins(() -> id 'java')
        """.trimIndent())
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test expression lambda 2`(gradleVersion: GradleVersion) {
    doTest(gradleVersion) {
      testHighlighting("""
        plugins(() -> <error>println</error> 20)
        """.trimIndent())
    }
  }
}