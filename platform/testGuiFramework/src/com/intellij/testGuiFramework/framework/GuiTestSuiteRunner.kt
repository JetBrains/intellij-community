// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.framework

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

class GuiTestSuiteRunner(private val suiteClass: Class<*>, builder: RunnerBuilder) : Suite(suiteClass, builder) {

  //IDE type to run suite tests with
  var isFirstStart: Boolean = true

  private val myIde: Ide = getIdeFromAnnotation(suiteClass)
  private val UNDEFINED_FIRST_CLASS = "undefined"
  private val myFirstStartClassName: String by lazy {
    val annotation = suiteClass.getAnnotation(FirstStartWith::class.java)
    val value = annotation?.value
    if (value != null) value.java.canonicalName else UNDEFINED_FIRST_CLASS
  }
  private val LOG: Logger = org.apache.log4j.Logger.getLogger("#com.intellij.testGuiFramework.framework.GuiTestSuiteRunner")!!

  private val testsFilter by lazy {
    object: Filter() {

      val filteredClassNameSet: Set<String> by lazy {
        GuiTestOptions.getFilteredListOfTests().split(",").toSet()
      }

      override fun shouldRun(description: Description?): Boolean {
        description?: return true
        return filteredClassNameSet.contains(description.testClass.simpleName)
      }

      override fun describe() = "It filters test classes by their short names listed in system property `${GuiTestOptions.FILTER_KEY}`"
    }
  }

  init {
    try {
      if (GuiTestOptions.shouldTestsBeFiltered()) {
        LOG.info("Tests filter is applied, next tests will be run: ${testsFilter.filteredClassNameSet.joinToString(", ")}")
        filter(testsFilter)
      }
    }
    catch (e: NoTestsRemainException) {
      e.printStackTrace()
    }
  }


  override fun runChild(runner: Runner, notifier: RunNotifier?) {
    try {
      //let's start IDE to complete installation, import configs and etc before running tests
      if (isFirstStart) firstStart()
      val testClass = runner.description.testClass
      val guiTestLocalRunner = GuiTestLocalRunner(testClass, suiteClass, myIde)
      super.runChild(guiTestLocalRunner, notifier)
    }
    catch (e: Exception) {
      LOG.error(e)
      notifier?.fireTestFailure(Failure(runner.description, e))
    }
  }

  private fun firstStart() {
    if (myFirstStartClassName == UNDEFINED_FIRST_CLASS) return
    LOG.info("IDE is configuring for the first time...")
    GuiTestLocalLauncher.firstStartIdeLocally(myIde, myFirstStartClassName)
    isFirstStart = false
  }

}
