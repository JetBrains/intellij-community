// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.util

import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiManager
import org.jetbrains.plugins.gradle.execution.test.runner.applyTestConfiguration
import org.jetbrains.plugins.gradle.importing.GradleBuildScriptBuilderEx
import org.jetbrains.plugins.gradle.importing.GradleImportingTestCase
import org.junit.Test
import org.junit.runners.Parameterized

class TestGradleConfigurationProducerUtilTest : GradleImportingTestCase() {

  @Test
  fun `test generation of gradle test settings`() {
    createProjectSubFile("src/test/java/AbstractSuite.java", """
      import org.junit.Assert;
      import org.junit.Test;

      public abstract class AbstractSuite {
          protected abstract int x();

          @Test
          public void testX() {
              Assert.assertEquals(3, x());
          }
      }
    """.trimIndent())
    val aSimpleTestCaseFile = createProjectSubFile("src/test/java/SimpleTestCase.java", """
      import org.junit.Assert;
      import org.junit.Test;

      public class SimpleTestCase extends AbstractSuite {
          @Override
          protected int x() {
              return 1;
          }
      }
    """.trimIndent())
    val buildScript = GradleBuildScriptBuilderEx()
      .withJavaPlugin()
      .withJUnit("4.12")
    importProject(buildScript.generate())
    assertModules("project", "project.test", "project.main")

    runReadActionAndWait {
      val psiManager = PsiManager.getInstance(myProject)
      val aSimpleTestCasePsiFile = psiManager.findFile(aSimpleTestCaseFile)!!
      val aSimpleTestCase = aSimpleTestCasePsiFile.findChildByType<PsiClass>()

      ExternalSystemTaskExecutionSettings().let { settings ->
        val isApplied = applyTestConfiguration(myProject, settings, arrayOf(aSimpleTestCase)) { psiClass ->
          GradleExecutionSettingsUtil.createTestFilterFrom(psiClass, false)
        }
        assertTrue(isApplied)
        assertEquals(":cleanTest :test --tests \"SimpleTestCase\"", settings.toString())
      }
    }
  }

  companion object {
    /**
     * It's sufficient to run the test against one gradle version
     */
    @Parameterized.Parameters(name = "with Gradle-{0}")
    @JvmStatic
    fun tests(): Collection<Array<out String>> = arrayListOf(arrayOf(GradleImportingTestCase.BASE_GRADLE_VERSION))
  }
}