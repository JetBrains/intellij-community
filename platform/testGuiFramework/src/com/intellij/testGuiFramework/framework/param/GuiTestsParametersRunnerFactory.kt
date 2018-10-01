// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.framework.param

import com.intellij.testGuiFramework.framework.GuiTestSuiteRunner
import org.junit.runner.Runner
import org.junit.runners.model.RunnerBuilder
import org.junit.runners.model.TestClass
import org.junit.runners.parameterized.ParametersRunnerFactory
import org.junit.runners.parameterized.TestWithParameters

class GuiTestsParametersRunnerFactory : ParametersRunnerFactory {

  override fun createRunnerForTestWithParameters(test: TestWithParameters?): Runner {
    test ?: throw Exception("Unable to build runner for a NULL test")
    return GuiTestSuiteRunner(test.testClass.javaClass, createRunnerBuilder(test)).apply { customName = "parameters: [${test.parameters[0]}]" }
  }

  private fun createRunnerBuilder(test: TestWithParameters): CustomRunnerBuilder {
    return object : CustomRunnerBuilder() {
      override fun runnerForClass(testClass: Class<*>?): Runner = GuiTestLocalRunnerParam(
        TestWithParameters(testClass!!.simpleName, TestClass(testClass), test.parameters))
    }
  }
}

abstract class CustomRunnerBuilder : RunnerBuilder()