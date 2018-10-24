// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.framework.param

import com.intellij.testGuiFramework.framework.GuiTestRunner
import com.intellij.testGuiFramework.framework.GuiTestRunnerInterface
import com.intellij.testGuiFramework.launcher.ide.Ide
import org.junit.runner.Description
import org.junit.runner.notification.RunNotifier
import org.junit.runners.model.FrameworkMethod
import org.junit.runners.parameterized.BlockJUnit4ClassRunnerWithParameters
import org.junit.runners.parameterized.TestWithParameters


class GuiTestLocalRunnerParam(private val testWithParameters: TestWithParameters,
                              private val ideFromConstructor: Ide? = null) : BlockJUnit4ClassRunnerWithParameters(
  testWithParameters), GuiTestRunnerInterface {

  override val ide: Ide? = ideFromConstructor
  private val runner: GuiTestRunner = GuiTestRunner(this)
  override var mySuiteClass: Class<*>? = null

  override fun testName(method: FrameworkMethod?): String {
    return "${method!!.name}${getParameters()}"
  }

  override fun getTestName(method: String): String {
    return "$method$name"
  }

  override fun runChild(method: FrameworkMethod, notifier: RunNotifier) {
    runner.runChild(method, notifier)
  }

  override fun doRunChild(method: FrameworkMethod, notifier: RunNotifier) {
    super.runChild(method, notifier)
  }

  override fun describeChild(method: FrameworkMethod): Description {
    return super.describeChild(method)
  }

  override fun getTestClassesNames(): List<String> {
    return listOf(this.testClass.javaClass.canonicalName)
  }

  fun getParameters(): MutableList<Any> {
    return testWithParameters.parameters
  }

  companion object {
    const val PARAMETERS: String = "parameters"
  }
}