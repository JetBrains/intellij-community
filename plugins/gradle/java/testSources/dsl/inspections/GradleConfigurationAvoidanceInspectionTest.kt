// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.dsl.inspections

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.codeInspection.GradleConfigurationAvoidanceInspection
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.jetbrains.plugins.gradle.testFramework.annotations.GradleTestSource
import org.junit.jupiter.params.ParameterizedTest

class GradleConfigurationAvoidanceInspectionTest : GradleCodeInsightTestCase() {

  private fun runTest(gradleVersion: GradleVersion, test: () -> Unit) {
    testEmptyProject(gradleVersion) {
      codeInsightFixture.enableInspections(GradleConfigurationAvoidanceInspection::class.java)
      test()
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testCreateToRegister(gradleVersion: GradleVersion) {
    runTest(gradleVersion) {
      testHighlighting("tasks.<warning>create</warning>('abc')")
    }
  }

  @ParameterizedTest
  @GradleTestSource("4.8")
  fun testNoWarningWithOldGradle(gradleVersion: GradleVersion) {
    runTest(gradleVersion) {
      testHighlighting("tasks.create('abc')")
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource("getByPath,findByPath")
  fun testGetByPath(gradleVersion: GradleVersion, methodName : String) {
    runTest(gradleVersion) {
      testHighlighting("tasks.<warning>$methodName</warning>('abc')")
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testAll(gradleVersion: GradleVersion) {
    runTest(gradleVersion) {
      testHighlighting("tasks.<warning>all</warning> {}")
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testWhenObjectAdded(gradleVersion: GradleVersion) {
    runTest(gradleVersion) {
      testHighlighting("tasks.<warning>whenObjectAdded</warning> {}")
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testPlainWithType(gradleVersion: GradleVersion) {
    runTest(gradleVersion) {
      testHighlighting("tasks.withType(Jar)")
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testWithType(gradleVersion: GradleVersion) {
    runTest(gradleVersion) {
      testHighlighting("tasks.<warning>withType</warning>(Jar) {}")
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testEagerTask(gradleVersion: GradleVersion) {
    runTest(gradleVersion) {
      testHighlighting("<warning>task</warning> myTask() {}")
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testFindByName(gradleVersion: GradleVersion) {
    runTest(gradleVersion) {
      testHighlighting("tasks.<warning>findByName</warning>('abc')")
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testGetByName(gradleVersion: GradleVersion) {
    runTest(gradleVersion) {
      testHighlighting("tasks.<warning>getByName</warning>('abc')")
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testGetAt(gradleVersion: GradleVersion) {
    runTest(gradleVersion) {
      testHighlighting("tasks<warning>['abc']</warning>")
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testFixWithType(gradleVersion: GradleVersion) {
    runTest(gradleVersion) {
      testIntention("tasks.withTy<caret>pe(Jar) { 1 + 1 }",
                    "tasks.withType(Jar).configureEach { 1 + 1 }", "Add 'configureEach'")
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testFixWithTypeComplex(gradleVersion: GradleVersion) {
    runTest(gradleVersion) {
      testIntention("def a = tasks.withTy<caret>pe(Jar) { 1 + 1 }",
                    "def a = tasks.withType(Jar).tap { configureEach { 1 + 1 } }", "Add 'configureEach'")
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testCorrectTaskDeclaration1(gradleVersion: GradleVersion) {
    runTest(gradleVersion) {
      testIntention("t<caret>ask tt {}",
                    "tasks.register('tt') {}", "Use")
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testCorrectTaskDeclaration2(gradleVersion: GradleVersion) {
    runTest(gradleVersion) {
      testIntention("t<caret>ask tt() {}",
                    "tasks.register('tt') {}", "Use")
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testCorrectTaskDeclaration3(gradleVersion: GradleVersion) {
    runTest(gradleVersion) {
      testIntention("t<caret>ask tt",
                    "tasks.register('tt')", "Use")
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testCreateToRegisterIntention(gradleVersion: GradleVersion) {
    runTest(gradleVersion) {
      testIntention("tasks.cr<caret>eate('abc')",
                    "tasks.register('abc')", "Replace")
    }
  }
}