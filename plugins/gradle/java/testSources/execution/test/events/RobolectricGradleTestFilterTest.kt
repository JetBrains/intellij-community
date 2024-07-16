// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.events

import com.intellij.execution.PsiLocation
import com.intellij.psi.*
import com.intellij.testFramework.utils.vfs.getPsiFile
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.GradleExecutionTestCase
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.util.createTestFilterFrom
import org.jetbrains.plugins.gradle.util.findChildByType
import org.jetbrains.plugins.gradle.util.runReadActionAndWait
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.params.ParameterizedTest

class RobolectricGradleTestFilterTest : GradleExecutionTestCase() {

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test test filter generation for the parametrized Robolectric tests`(gradleVersion: GradleVersion) {
    testRobolectricProject(gradleVersion) {
      val virtualFile = writeText("src/test/java/org/example/TestCase.java", """
        |package org.example;
        |
        |import org.junit.Test;
        |import org.junit.runner.RunWith;
        |import org.robolectric.ParameterizedRobolectricTestRunner;
        |
        |@RunWith(ParameterizedRobolectricTestRunner.class)
        |public class TestCase {
        |  @Test
        |  public void test() {}
        |}
      """.trimMargin())
      runReadActionAndWait {
        val psiFile = virtualFile.getPsiFile(project)
        val psiClass = psiFile.findChildByType<PsiClass>()
        val psiMethod = psiClass.findChildByType<PsiMethod>()
        val psiClassLocation = PsiLocation.fromPsiElement(project, psiClass)
        val psiMethodLocation = PsiLocation.fromPsiElement(project, psiMethod)

        Assertions.assertEquals("--tests \"org.example.TestCase\"", createTestFilterFrom(psiClassLocation, psiClass, null))
        Assertions.assertEquals("--tests \"org.example.TestCase.test[*]\"", createTestFilterFrom(psiMethodLocation, psiClass, psiMethod))
      }
    }
  }
}