// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.producer

import com.intellij.execution.Location
import com.intellij.mock.MockVirtualFile
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.gradle.action.createRerunTestFilter
import org.jetbrains.plugins.gradle.action.getRerunTestLocationInfo
import org.jetbrains.plugins.gradle.action.removeTestFilters
import org.jetbrains.plugins.gradle.execution.GradleRunnerUtil
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestLocationInfo
import org.jetbrains.plugins.gradle.execution.test.runner.applyTestConfiguration
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertIterableEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.mockito.Mockito.mock
import java.util.Collections.emptyIterator

class TestGradleConfigurationProducerUtilTest {

  @Test
  fun testThatSettingsGetProperTaskNames() {
    val settingsUnderTest = ExternalSystemTaskExecutionSettings()

    val fileWithTestsA = MockVirtualFile("FileNameA.kt")
    val fileWithTestsB = MockVirtualFile("FileNameB.kt")

    val applied = settingsUnderTest.applyTestConfiguration(
      "whatever-project-path",
      listOf("TestA", "TestB"),
      {
        when(it) {
          "TestA" -> fileWithTestsA
          "TestB" -> fileWithTestsB
          else -> null
        }
      },
      { "FILTER" },
      {
        when(it) {
          fileWithTestsA -> listOf(listOf("task A", "task B"))
          fileWithTestsB -> listOf(listOf(":module1:module2:taskC", "taskD"))
          else -> emptyList()
        }

      }
    )

    assertTrue(applied) { "Test configuration was not applied" }
    assertIterableEquals(listOf("'task A'", "'task B'", "FILTER", ":module1:module2:taskC", "taskD", "FILTER"), settingsUnderTest.taskNames)
  }

  @Test
  fun testThatEmptyConfigurationDoesNotResetExistingSettings() {
    val settingsUnderTest = ExternalSystemTaskExecutionSettings().apply {
      externalProjectPath = "original-project-path"
      taskNames = listOf(":test", "--tests", "org.example.TestCase")
      scriptParameters = "--info"
    }

    val applied = settingsUnderTest.applyTestConfiguration(
      "new-project-path",
      emptyList<String>(),
      { MockVirtualFile("FileName.kt") },
      { "FILTER" },
      { listOf(listOf(":test")) }
    )

    assertFalse(applied) { "Empty test configuration should not be applied" }
    assertEquals("original-project-path", settingsUnderTest.externalProjectPath)
    assertIterableEquals(listOf(":test", "--tests", "org.example.TestCase"), settingsUnderTest.taskNames)
    assertEquals("--info", settingsUnderTest.scriptParameters)
  }

  @Test
  fun testThatRerunUsesExplicitGradleTestLocationInfo() {
    val sourceElement = mock(PsiElement::class.java)
    val psiClass = mock(PsiClass::class.java)
    val psiMethod = mock(PsiMethod::class.java)
    val project = mock(Project::class.java)
    val location = object : Location<PsiElement>(), GradleTestLocationInfo {
      override val sourceElement: PsiElement = sourceElement
      override val testClass: PsiClass = psiClass
      override val testMethod: PsiMethod = psiMethod
      override val testFilter: String = "--tests \"org.example.TestCase.test\""

      override fun getPsiElement(): PsiElement = sourceElement
      override fun getProject(): Project = project
      override fun getModule(): Module? = null

      override fun <T : PsiElement> getAncestors(ancestorClass: Class<T>, strict: Boolean): Iterator<Location<T>> {
        return emptyIterator()
      }
    }

    assertNull(GradleRunnerUtil.getMethodLocation(location))
    val locationInfo = getRerunTestLocationInfo(location)!!
    assertSame(sourceElement, locationInfo.element)
    assertSame(psiClass, locationInfo.psiClass)
    assertSame(psiMethod, locationInfo.psiMethod)
    assertEquals("--tests \"org.example.TestCase.test\"", createRerunTestFilter(locationInfo))
  }

  @Test
  fun testThatRerunTasksDoNotKeepPreviousTestFilters() {
    assertIterableEquals(
      listOf(":shared:cleanIosSimulatorArm64Test", ":shared:iosSimulatorArm64Test"),
      removeTestFilters(
        listOf(
          ":shared:cleanIosSimulatorArm64Test",
          ":shared:iosSimulatorArm64Test",
          "--tests \"com.jetbrains.kmt_1087_test_project.IosGreetingTest\""
        )
      )
    )
    assertIterableEquals(
      listOf(":cleanTest", ":test"),
      removeTestFilters(listOf(":cleanTest", ":test", "--tests", "\"org.example.TestCase\""))
    )
    assertIterableEquals(
      listOf(":cleanTest", ":test", "--rerun"),
      removeTestFilters(listOf(":cleanTest", ":test", "--tests=org.example.TestCase", "--rerun"))
    )
    assertIterableEquals(
      listOf(":cleanTest", ":test", "--rerun"),
      removeTestFilters(listOf(":cleanTest", ":test", "--tests \"org.example.TestCase\"", "--rerun"))
    )
    assertIterableEquals(
      listOf(":cleanIosTest", ":iosTest", ":cleanJsTest", ":jsTest"),
      removeTestFilters(
        listOf(
          ":cleanIosTest", ":iosTest", "--tests", "\"org.example.IosTest\"",
          ":cleanJsTest", ":jsTest", "--tests", "\"org.example.JsTest\""
        )
      )
    )
  }
}
