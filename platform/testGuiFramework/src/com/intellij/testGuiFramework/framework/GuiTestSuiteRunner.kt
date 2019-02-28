// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.framework

import com.intellij.testGuiFramework.framework.param.CustomRunnerBuilder
import com.intellij.testGuiFramework.launcher.GuiTestLocalLauncher
import com.intellij.testGuiFramework.launcher.GuiTestOptions
import com.intellij.testGuiFramework.launcher.ide.Ide
import org.apache.log4j.Logger
import org.junit.runner.Description
import org.junit.runner.Runner
import org.junit.runner.manipulation.Filter
import org.junit.runner.manipulation.NoTestsRemainException
import org.junit.runner.notification.Failure
import org.junit.runner.notification.RunNotifier
import org.junit.runners.Suite
import org.junit.runners.model.RunnerBuilder

open class GuiTestSuiteRunner(private val suiteClass: Class<*>, private val builder: RunnerBuilder) : Suite(suiteClass, builder) {

  @Volatile
  var customName: String? = null

  protected val myIde: Ide = getIdeFromAnnotation(suiteClass)
  private val LOG: Logger = org.apache.log4j.Logger.getLogger("#com.intellij.testGuiFramework.framework.GuiTestSuiteRunner")!!

  private val testsFilter by lazy {
    object : Filter() {

      val filteredClassNameSet: Set<String> by lazy {
        GuiTestOptions.filteredListOfTests.split(",").toSet()
      }

      override fun shouldRun(description: Description?): Boolean {
        description ?: return true
        return filteredClassNameSet.contains(description.testClass.simpleName)
      }

      override fun describe() = "It filters test classes by their short names listed in system property `${GuiTestOptions.FILTER_KEY}`"
    }
  }

  init {
    try {
      if (GuiTestOptions.shouldTestsBeFiltered) {
        LOG.info("Tests filter is applied, next tests will be run: ${testsFilter.filteredClassNameSet.joinToString(", ")}")
        filter(testsFilter)
      }
    }
    catch (e: NoTestsRemainException) {
      e.printStackTrace()
    }
  }

  protected open fun createGuiTestLocalRunner(testClass: Class<*>, suiteClass: Class<*>, myIde: Ide): GuiTestLocalRunner {
    return GuiTestLocalRunner(testClass, suiteClass, myIde)
  }

  override fun getName(): String {
    return customName ?: super.getName()
  }

  override fun runChild(runner: Runner, notifier: RunNotifier?) {
    if (builder is CustomRunnerBuilder) super.runChild(runner, notifier)
    else {
      try {
        LOG.info("DEBUG_TEST_DESCRIPTION: ${runner.description.displayName} ${description.className} ${description.testClass} ${description.methodName}")
        val testClass = runner.description.testClass
        val guiTestLocalRunner = createGuiTestLocalRunner(testClass, suiteClass, myIde)
        super.runChild(guiTestLocalRunner, notifier)
      }
      catch (e: Exception) {
        LOG.error(e)
        notifier?.fireTestFailure(Failure(runner.description, e))
      }
    }
  }

}
