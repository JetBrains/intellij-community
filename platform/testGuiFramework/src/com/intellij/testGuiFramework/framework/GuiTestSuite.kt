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
import org.junit.runner.notification.RunNotifier
import org.junit.runners.Suite
import org.junit.runners.model.RunnerBuilder

class GuiTestSuite(suiteClass: Class<*>, val builder: RunnerBuilder): Suite(suiteClass, builder) {

  //IDE type to run suite tests with
  val myIde = GuiTestLocalRunner.getIdeFromAnnotation(suiteClass)
  var myFirstStart = true

  override fun runChild(runner: Runner, notifier: RunNotifier?) {
    //let's start IDE to complete installation, import configs and etc before running tests
    if (myFirstStart) firstStart()

    val testClass = runner.description.testClass
    //check that ide types are equal
    check(GuiTestLocalRunner.getIdeFromAnnotation(testClass).ideType == myIde.ideType)
    val guiTestLocalRunner = GuiTestLocalRunner(testClass)
    super.runChild(guiTestLocalRunner, notifier)
  }

  private fun firstStart() {
    GuiTestLocalLauncher.firstStartIdeLocally(myIde)
    myFirstStart = false
  }

}
