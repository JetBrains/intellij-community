// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.events

import com.intellij.execution.PsiLocation
import com.intellij.psi.*
import com.intellij.testFramework.utils.vfs.getPsiFile
import junit.framework.TestCase.assertEquals
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.GradleExecutionTestCase
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.util.createTestFilterFrom
import org.jetbrains.plugins.gradle.util.findChildByType
import org.jetbrains.plugins.gradle.util.findChildrenByType
import org.jetbrains.plugins.gradle.util.runReadActionAndWait
import org.junit.jupiter.params.ParameterizedTest

class RobolectricGradleTestNavigationTest : GradleExecutionTestCase() {

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test display name and navigation with Groovy and Spock`(gradleVersion: GradleVersion) {
    testRobolectricProject(gradleVersion) {

      val file = writeText("src/test/java/org/example/TestCase.java", JAVA_CLASS_WITH_ROBOLECTRIC_FRAMEWORK)

      runReadActionAndWait {
        val psiFile = file.getPsiFile(project)
        val testClass = psiFile.findChildByType<PsiClass>()

        val testMethods = testClass.findChildrenByType<PsiMethod>()
        assertEquals("""--tests "org.example.TestCase.test"""", createTestFilterFrom(testMethods[0]))
        val location = PsiLocation.fromPsiElement<PsiElement>(project, testMethods[0])

        assertEquals(createTestFilterFrom(location, testClass, testMethods[0]), "--tests \"org.example.TestCase.test[*]\"")
      }
    }
  }

  companion object {

    private val JAVA_CLASS_WITH_ROBOLECTRIC_FRAMEWORK = """
      |package org.example;
      |
      |import org.junit.Test;
      |import org.junit.runner.RunWith;
      |import org.robolectric.ParameterizedRobolectricTestRunner;
      |
      |@RunWith(ParameterizedRobolectricTestRunner.class)
      |public class TestCase {
      |  @Test public void test() {}
      |}
    """.trimMargin()
  }
}