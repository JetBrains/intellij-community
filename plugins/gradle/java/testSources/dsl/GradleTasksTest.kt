// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.dsl

import com.intellij.psi.PsiMethod
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.service.resolve.GradleTaskProperty
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.junit.jupiter.params.ParameterizedTest
import com.intellij.psi.CommonClassNames.JAVA_LANG_STRING
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.*
import org.junit.jupiter.api.Assertions.assertEquals

class GradleTasksTest : GradleCodeInsightTestCase() {

  override fun createGradleTestFixture(gradleVersion: GradleVersion) =
    createGradleCodeInsightTestFixture(gradleVersion, "java")

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test task ref`(gradleVersion: GradleVersion) {
    testBuildscript(gradleVersion, "<caret>javadoc") {
      testTask("javadoc", GRADLE_API_TASKS_JAVADOC_JAVADOC)
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test task call`(gradleVersion: GradleVersion) {
    testBuildscript(gradleVersion, "<caret>javadoc {}") {
      methodCallTest(PsiMethod::class.java, GRADLE_API_TASKS_JAVADOC_JAVADOC)
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test task call delegate`(gradleVersion: GradleVersion) {
    testBuildscript(gradleVersion, "javadoc { <caret> }") {
      closureDelegateTest(GRADLE_API_TASKS_JAVADOC_JAVADOC, 1)
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test task container vs task ref`(gradleVersion: GradleVersion) {
    testBuildscript(gradleVersion, "<caret>tasks") {
      referenceExpressionTest(PsiMethod::class.java, GRADLE_API_TASK_CONTAINER)
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test task container vs task call`(gradleVersion: GradleVersion) {
    testBuildscript(gradleVersion, "<caret>tasks {}") {
      methodCallTest(PsiMethod::class.java, GRADLE_API_TASKS_DIAGNOSTICS_TASK_REPORT_TASK)
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test task via TaskContainer`(gradleVersion: GradleVersion) {
    testBuildscript(gradleVersion, "tasks.<caret>tasks") {
      testTask("tasks", GRADLE_API_TASKS_DIAGNOSTICS_TASK_REPORT_TASK)
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test task via Project`(gradleVersion: GradleVersion) {
    testBuildscript(gradleVersion, "project.<caret>clean") {
      testTask("clean", GRADLE_API_TASKS_DELETE)
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test task in allProjects`(gradleVersion: GradleVersion) {
    testBuildscript(gradleVersion, "allProjects { <caret>clean }") {
      testTask("clean", GRADLE_API_TASKS_DELETE)
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test task in allProjects via explicit delegate`(gradleVersion: GradleVersion) {
    testBuildscript(gradleVersion, "allProjects { delegate.<caret>clean }") {
      resolveTest<Nothing>(null)
    }
  }

  private fun testTask(name: String, type: String) {
    val property = referenceExpressionTest(GradleTaskProperty::class.java, type)
    assertEquals(name, property.name)
  }

  @ParameterizedTest
  @AllGradleVersionsSource("""
    "task('s') { <caret> }",
    "task(id2) { <caret> }",
    "task(id3, { <caret> })",
    "task(id5, description: 'oh') { <caret> }",
    "task(id6, description: 'oh', { <caret> })",
    "task id9() { <caret>}",
    "task id8 { <caret> }",
    "task id10({ <caret> })",
    "task id12(description: 'hi') { <caret> }",
    "task id13(description: 'hi', { <caret> })",
    "task mid12([description: 'hi']) { <caret> }",
    "task mid13([description: 'hi'], { <caret> })",
    "task emid12([:]) { <caret> }",
    "task emid13([:], { <caret> })",
    "tasks.create(name: 'cid1') { <caret> }",
    "tasks.create([name: 'mcid1']) { <caret> }",
    "tasks.create('eid1') { <caret> }"
  """)
  fun `test task declaration configuration delegate`(gradleVersion: GradleVersion, expression: String) {
    testBuildscript(gradleVersion, expression) {
      closureDelegateTest(GRADLE_API_TASK, 1)
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource("""
    "task('s', type: String) { <caret> }",
    "task(id5, type: String) { <caret> }",
    "task(id6, type: String, { <caret> })",
    "task id12(type: String) { <caret> }",
    "task id13(type: String, { <caret> })",
    "task mid12([type: String]) { <caret> }",
    "task mid13([type: String], { <caret> })",
    "tasks.create(name: 'cid1', type: String) { <caret> }",
    "tasks.create([name: 'mcid1', type: String]) { <caret> }",
    "tasks.create('eid1', String) { <caret> }"
  """)
  fun `test task declaration configuration delegate with explicit type`(gradleVersion: GradleVersion, expression: String) {
    testBuildscript(gradleVersion, expression) {
      closureDelegateTest(JAVA_LANG_STRING, 1)
    }
  }
}