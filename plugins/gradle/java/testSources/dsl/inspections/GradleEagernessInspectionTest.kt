// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.dsl.inspections

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.codeInspection.GradleEagernessInspection
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.jetbrains.plugins.gradle.testFramework.annotations.GradleTestSource
import org.junit.jupiter.params.ParameterizedTest

class GradleEagernessInspectionTest : GradleCodeInsightTestCase() {

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testCreateToRegister(gradleVersion: GradleVersion) {
    testEmptyProject(gradleVersion) {
      codeInsightFixture.enableInspections(GradleEagernessInspection::class.java)
      testHighlighting("tasks.<warning>create</warning>('abc')")
    }
  }

  @ParameterizedTest
  @GradleTestSource("4.8")
  fun testNoWarningWithOldGradle(gradleVersion: GradleVersion) {
    testEmptyProject(gradleVersion) {
      codeInsightFixture.enableInspections(GradleEagernessInspection::class.java)
      testHighlighting("tasks.create('abc')")
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource("getByPath,findByPath")
  fun testGetByPath(gradleVersion: GradleVersion, methodName : String) {
    testEmptyProject(gradleVersion) {
      codeInsightFixture.enableInspections(GradleEagernessInspection::class.java)
      testHighlighting("tasks.<warning>$methodName</warning>('abc')")
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testAll(gradleVersion: GradleVersion) {
    testEmptyProject(gradleVersion) {
      codeInsightFixture.enableInspections(GradleEagernessInspection::class.java)
      testHighlighting("tasks.<warning>all</warning> {}")
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testWhenObjectAdded(gradleVersion: GradleVersion) {
    testEmptyProject(gradleVersion) {
      codeInsightFixture.enableInspections(GradleEagernessInspection::class.java)
      testHighlighting("tasks.<warning>whenObjectAdded</warning> {}")
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testPlainWithType(gradleVersion: GradleVersion) {
    testEmptyProject(gradleVersion) {
      codeInsightFixture.enableInspections(GradleEagernessInspection::class.java)
      testHighlighting("tasks.withType(Jar)")
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testWithType(gradleVersion: GradleVersion) {
    testEmptyProject(gradleVersion) {
      codeInsightFixture.enableInspections(GradleEagernessInspection::class.java)
      testHighlighting("tasks.<warning>withType</warning>(Jar) {}")
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testEagerTask(gradleVersion: GradleVersion) {
    testEmptyProject(gradleVersion) {
      codeInsightFixture.enableInspections(GradleEagernessInspection::class.java)
      testHighlighting("<warning>task</warning> myTask() {}")
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testFindByName(gradleVersion: GradleVersion) {
    testEmptyProject(gradleVersion) {
      codeInsightFixture.enableInspections(GradleEagernessInspection::class.java)
      testHighlighting("tasks.<warning>findByName</warning>('abc')")
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testGetByName(gradleVersion: GradleVersion) {
    testEmptyProject(gradleVersion) {
      codeInsightFixture.enableInspections(GradleEagernessInspection::class.java)
      testHighlighting("tasks.<warning>getByName</warning>('abc')")
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testGetAt(gradleVersion: GradleVersion) {
    testEmptyProject(gradleVersion) {
      codeInsightFixture.enableInspections(GradleEagernessInspection::class.java)
      testHighlighting("tasks<warning>['abc']</warning>")
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testFixWithType(gradleVersion: GradleVersion) {
    testEmptyProject(gradleVersion) {
      codeInsightFixture.enableInspections(GradleEagernessInspection::class.java)
      testIntention("tasks.withTy<caret>pe(Jar) { 1 + 1 }",
                    "tasks.withType(Jar).configureEach { 1 + 1 }", "Add 'configureEach'")
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testFixWithTypeComplex(gradleVersion: GradleVersion) {
    testEmptyProject(gradleVersion) {
      codeInsightFixture.enableInspections(GradleEagernessInspection::class.java)
      testIntention("def a = tasks.withTy<caret>pe(Jar) { 1 + 1 }",
                    "def a = tasks.withType(Jar).tap { configureEach { 1 + 1 } }", "Add 'configureEach'")
    }
  }
}