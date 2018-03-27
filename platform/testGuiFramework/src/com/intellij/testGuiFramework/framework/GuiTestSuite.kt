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
import org.junit.runners.Suite
import org.junit.runners.model.RunnerBuilder

class GuiTestSuite(private val suiteClass: Class<*>, builder: RunnerBuilder) : Suite(suiteClass, builder) {

  //IDE type to run suite tests with
  val myIde = getIdeFromAnnotation(suiteClass)
  var isFirstStart = true
  val UNDEFINED_FIRST_CLASS = "undefined"
  val myFirstStartClassName: String by lazy {
    val annotation = suiteClass.getAnnotation(FirstStartWith::class.java)
    val value = annotation?.value
    if (value != null) value.java.canonicalName else UNDEFINED_FIRST_CLASS
  }
  val LOG = org.apache.log4j.Logger.getLogger("#com.intellij.testGuiFramework.framework.GuiTestSuite")!!

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
