// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.framework.dtrace

import com.intellij.testGuiFramework.framework.GuiTestRunner
import com.intellij.testGuiFramework.framework.GuiTestRunnerInterface
import com.intellij.testGuiFramework.launcher.GuiTestLocalLauncher
import com.intellij.testGuiFramework.launcher.ide.Ide
import com.intellij.testGuiFramework.remote.IdeControl
import com.intellij.testGuiFramework.remote.server.JUnitServerHolder
import org.junit.internal.runners.model.EachTestNotifier
import org.junit.runners.model.FrameworkMethod


class GuiDTTestRunner internal constructor(runner: GuiTestRunnerInterface) : GuiTestRunner(runner) {

  private val LOGGER = org.apache.log4j.Logger.getLogger("#com.intellij.testGuiFramework.framework.dtrace.GuiDTTestRunner")!!

  private val testClassNames: List<String> = runner.getTestClassesNames()
  private var currentClass = 0
  private var additionalJvmOptions: List<Pair<String, String>>? = null

  override fun runIde(ide: Ide, additionalJvmOptions: List<Pair<String, String>>) {
    if (this.additionalJvmOptions == null)
      this.additionalJvmOptions = additionalJvmOptions

    if (currentClass < testClassNames.size) {
      LOGGER.info("" + currentClass + ". running test " + testClassNames[currentClass])
      GuiTestLocalLauncher.runIdeWithDTraceLocally(port = JUnitServerHolder.getServer().getPort(),
                                                   ide = ide,
                                                   testClassName = testClassNames[currentClass],
                                                   additionalJvmOptions = additionalJvmOptions)
    }
  }

  fun finishTest(method: FrameworkMethod) {
    if (++currentClass < testClassNames.size) {
      IdeControl.restartIde(getIdeFromMethod(method), additionalJvmOptions!!, ::runIde)
    } else
      IdeControl.closeIde()
  }

  override fun processTestFinished(eachNotifier: EachTestNotifier) {
    val inputStream = IdeControl.getInputStream()
    IdeControl.closeIde()

    try {
      val testInstance = Class.forName(testClassNames[currentClass]).newInstance() as GuiDTTestCase
      GuiDTTestCase::checkDtraceLog.invoke(testInstance, inputStream)
    }
    catch (e: AssertionError) {
      eachNotifier.addFailure(e);
    }
    eachNotifier.fireTestFinished();
  }
}