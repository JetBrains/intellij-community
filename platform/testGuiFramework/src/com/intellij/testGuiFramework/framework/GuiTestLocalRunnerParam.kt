// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.framework

import com.intellij.testGuiFramework.launcher.ide.Ide
import org.junit.runner.Description
import org.junit.runner.notification.RunNotifier
import org.junit.runners.model.FrameworkMethod
import org.junit.runners.parameterized.BlockJUnit4ClassRunnerWithParameters
import org.junit.runners.parameterized.TestWithParameters


class GuiTestLocalRunnerParam : BlockJUnit4ClassRunnerWithParameters, GuiTestRunnerInterface {

  override val ide: Ide?
  private val runner: GuiTestRunner
  override var mySuiteClass: Class<*>? = null

  constructor(testWithParameters: TestWithParameters, ideFromTest: Ide?) : super(testWithParameters) {
    ide = ideFromTest
    runner = GuiTestRunner(this)
  }

  constructor(testWithParameters: TestWithParameters) : this(testWithParameters, null)

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
}