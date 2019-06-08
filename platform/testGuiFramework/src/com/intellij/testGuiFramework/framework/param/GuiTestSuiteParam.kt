// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.framework.param

import com.intellij.testGuiFramework.framework.getIdeFromAnnotation
import com.intellij.testGuiFramework.launcher.GuiTestLocalLauncher
import com.intellij.testGuiFramework.launcher.ide.Ide
import org.apache.log4j.Logger
import org.junit.runner.RunWith
import org.junit.runner.Runner
import org.junit.runner.notification.Failure
import org.junit.runner.notification.RunNotifier
import org.junit.runners.Parameterized
import org.junit.runners.parameterized.BlockJUnit4ClassRunnerWithParameters
import org.junit.runners.parameterized.TestWithParameters

@RunWith(GuiTestSuiteParamRunner::class)
@Parameterized.UseParametersRunnerFactory(GuiTestsParametersRunnerFactory::class)
open class GuiTestSuiteParam(klass: Class<*>) : Parameterized(klass) {

  //IDE type to run suite tests with
  val myIde: Ide = getIdeFromAnnotation(klass)
  val LOG: Logger = org.apache.log4j.Logger.getLogger("#com.intellij.testGuiFramework.framework.param.GuiTestSuiteParam")!!

  override fun runChild(runner: Runner, notifier: RunNotifier?) {
    try {
      val runnerWithParameters = runner as BlockJUnit4ClassRunnerWithParameters

      val testNameField = BlockJUnit4ClassRunnerWithParameters::class.java.getDeclaredField("name")
      testNameField.isAccessible = true
      val testName: String = testNameField.get(runnerWithParameters) as String

      val parametersListField = BlockJUnit4ClassRunnerWithParameters::class.java.getDeclaredField("parameters")
      parametersListField.isAccessible = true
      val parametersList = (parametersListField.get(runnerWithParameters) as Array<*>).toMutableList()

      val testWithParams = TestWithParameters(testName, runner.testClass, parametersList)
      val guiTestLocalRunnerParam = GuiTestLocalRunnerParam(testWithParams, myIde)
      super.runChild(guiTestLocalRunnerParam, notifier)
    }
    catch (e: Exception) {
      LOG.error(e)
      notifier?.fireTestFailure(Failure(runner.description, e))
    }
  }

}