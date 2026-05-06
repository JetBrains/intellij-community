// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.events

import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.testframework.JavaSMTRunnerTestTreeView
import com.intellij.execution.testframework.sm.runner.MockRuntimeConfiguration
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.execution.testframework.sm.runner.events.TestDurationStrategy
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerTestTreeView
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerTestTreeViewProvider
import com.intellij.openapi.project.Project
import com.intellij.testFramework.assertInstanceOf
import org.assertj.core.api.Assertions
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.GradleTestExecutionTestCase
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.junit.jupiter.params.ParameterizedTest

class GradleSuiteWallTimeTest : GradleTestExecutionTestCase() {

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test suite wall time includes class setup`(gradleVersion: GradleVersion) {
    testJunit4Project(gradleVersion) {
      writeText("src/test/java/org/example/SuiteWithSetup.java", """
        |package org.example;
        |import org.junit.BeforeClass;
        |import org.junit.Test;
        |public class SuiteWithSetup {
        |  @BeforeClass
        |  public static void setUpClass() throws InterruptedException {
        |    Thread.sleep(200);
        |  }
        |  @Test
        |  public void test() throws InterruptedException {
        |    Thread.sleep(100);
        |  }
        |}
      """.trimMargin())

      executeTasks(":test", isRunAsTest = true)
      assertTestViewTree {
        assertNode("SuiteWithSetup") {
          assertValue { suite ->
            assertInstanceOf<SMTestProxy>(suite)
            Assertions.assertThat((suite as SMTestProxy).durationStrategy)
              .describedAs("Suite should use AUTOMATIC strategy")
              .isEqualTo(TestDurationStrategy.AUTOMATIC)
            Assertions.assertThat(suite.getCustomizedDuration(WallTimeAwareConsoleProperties(project)))
              .describedAs("Suite wall time (endTime - startTime) should include @BeforeClass (200ms) + @Test (100ms)")
              .isGreaterThanOrEqualTo(300L)
          }
          assertNode("test")
        }
      }
    }
  }

  private class WallTimeAwareConsoleProperties internal constructor(project: Project) :
    SMTRunnerConsoleProperties(MockRuntimeConfiguration(project), "JUnit", DefaultRunExecutor.getRunExecutorInstance()),
    SMTRunnerTestTreeViewProvider {
    override fun createSMTRunnerTestTreeView(): SMTRunnerTestTreeView {
      return JavaSMTRunnerTestTreeView(this)
    }
  }
}
