/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.testGuiFramework.framework

import com.intellij.testGuiFramework.launcher.GuiTestLocalLauncher
import org.junit.runner.Runner
import org.junit.runner.notification.Failure
import org.junit.runner.notification.RunNotifier
import org.junit.runners.Parameterized
import org.junit.runners.parameterized.BlockJUnit4ClassRunnerWithParameters
import org.junit.runners.parameterized.TestWithParameters

class GuiTestSuiteParam(private val klass: Class<*>) : Parameterized(klass) {

  //IDE type to run suite tests with
  val myIde = getIdeFromAnnotation(klass)
  var isFirstStart = true
  val UNDEFINED_FIRST_CLASS = "undefined"
  val myFirstStartClassName: String by lazy {
    val annotation = klass.getAnnotation(FirstStartWith::class.java)
    val value = annotation?.value
    if (value != null) value.java.canonicalName else UNDEFINED_FIRST_CLASS
  }
  val LOG = org.apache.log4j.Logger.getLogger("#com.intellij.testGuiFramework.framework.GuiTestSuiteParam")!!

  override fun runChild(runner: Runner, notifier: RunNotifier?) {
    try {
      //let's start IDE to complete installation, import configs and etc before running tests
      if (isFirstStart) firstStart()
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

  private fun firstStart() {
    if (myFirstStartClassName == UNDEFINED_FIRST_CLASS) return
    LOG.info("IDE is configuring for the first time...")
    GuiTestLocalLauncher.firstStartIdeLocally(myIde, myFirstStartClassName)
    isFirstStart = false
  }
}