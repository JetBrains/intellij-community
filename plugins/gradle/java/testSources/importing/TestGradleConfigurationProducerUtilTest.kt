// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.importing

import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiManager
import org.jetbrains.plugins.gradle.execution.test.runner.applyTestConfiguration
import org.jetbrains.plugins.gradle.testFramework.util.createBuildFile
import org.jetbrains.plugins.gradle.util.createTestFilterFrom
import org.jetbrains.plugins.gradle.util.findChildByType
import org.jetbrains.plugins.gradle.util.runReadActionAndWait
import org.junit.Test
import org.junit.runners.Parameterized

class TestGradleConfigurationProducerUtilTest : GradleImportingTestCase() {

  @Test
  fun `test generation of gradle test settings`() {
    createProjectSubFile("module/src/test/java/AbstractSuite.java", """
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
    val aSimpleTestCaseFile = createProjectSubFile("module/src/test/java/SimpleTestCase.java", """
      import org.junit.Assert;
      import org.junit.Test;

      public class SimpleTestCase extends AbstractSuite {
          @Override
          protected int x() {
              return 1;
          }
      }
    """.trimIndent())
    val aDepSimpleTestCaseFile = createProjectSubFile("dep-module/src/test/java/DepSimpleTestCase.java", """
      import org.junit.Assert;
      import org.junit.Test;

      public class DepSimpleTestCase extends AbstractSuite {
          @Override
          protected int x() {
              return 1;
          }

          @Test
          public void test() {
              Assert.fail();
          }
      }
    """.trimIndent())
    val archivesBaseName = if (isGradleAtLeast("9.0")) {
      "project.base.archivesName"
    } else {
      "project.archivesBaseName"
    }
    createBuildFile("module") {
      withJavaPlugin()
      withJUnit4()
      addPostfix("""
        task myTestsJar(type: Jar, dependsOn: testClasses) {
            archiveBaseName = "test-${'$'}{$archivesBaseName}"
            from sourceSets.test.output
        }

        configurations {
            testArtifacts
        }

        artifacts {
            testArtifacts  myTestsJar
        }
      """.trimIndent())
    }
    createBuildFile("dep-module") {
      withJavaPlugin()
      withJUnit4()
      addImplementationDependency(project(":module"))
      addImplementationDependency(project(":module", "testArtifacts"))
    }
    createSettingsFile("""
      rootProject.name = 'project'
      include 'module'
      include 'dep-module'
    """.trimIndent())
    importProject()
    assertModules("project",
                  "project.module", "project.module.test", "project.module.main",
                  "project.dep-module", "project.dep-module.test", "project.dep-module.main")

    runReadActionAndWait {
      val psiManager = PsiManager.getInstance(myProject)
      val aSimpleTestCasePsiFile = psiManager.findFile(aSimpleTestCaseFile)!!
      val aDepSimpleTestCasePsiFile = psiManager.findFile(aDepSimpleTestCaseFile)!!
      val aSimpleTestCase = aSimpleTestCasePsiFile.findChildByType<PsiClass>()
      val aDepSimpleTestCase = aDepSimpleTestCasePsiFile.findChildByType<PsiClass>()

      assertClassRunConfigurationSettings(
        ":module:test --tests \"SimpleTestCase\"", aSimpleTestCase)
      assertClassRunConfigurationSettings(
        ":dep-module:test --tests \"DepSimpleTestCase\"", aDepSimpleTestCase)
      assertClassRunConfigurationSettings(
        ":module:test --tests \"SimpleTestCase\" " +
        ":dep-module:test --tests \"DepSimpleTestCase\" " +
        "--continue", aSimpleTestCase, aDepSimpleTestCase)
      assertClassRunConfigurationSettings(
        ":module:test --tests \"SimpleTestCase\" " +
        ":dep-module:test --tests \"DepSimpleTestCase\" " +
        "--continue", aSimpleTestCase, aDepSimpleTestCase, aDepSimpleTestCase, aSimpleTestCase, aSimpleTestCase)
    }
  }

  private fun assertClassRunConfigurationSettings(expectedSettings: String, vararg classes: PsiClass) {
    val settings = ExternalSystemTaskExecutionSettings()
    val isApplied = settings.applyTestConfiguration(getModule("project"), *classes) { createTestFilterFrom(it) }
    assertTrue(isApplied)
    assertEquals(expectedSettings, settings.toString().trim())
  }

  companion object {
    /**
     * It's sufficient to run the test against one gradle version
     */
    @Parameterized.Parameters(name = "with Gradle-{0}")
    @JvmStatic
    fun tests(): Collection<Array<out String>> = arrayListOf(arrayOf(BASE_GRADLE_VERSION))
  }
}