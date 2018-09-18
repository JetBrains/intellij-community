// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.framework.dtrace

import com.intellij.testGuiFramework.framework.GuiTestRunner
import com.intellij.testGuiFramework.framework.GuiTestRunnerInterface
import com.intellij.testGuiFramework.launcher.GuiTestLocalLauncher
import com.intellij.testGuiFramework.launcher.ide.Ide
import com.intellij.testGuiFramework.remote.IdeProcessControlManager
import com.intellij.testGuiFramework.remote.server.JUnitServerHolder
import org.junit.internal.runners.model.EachTestNotifier
import org.junit.runners.model.FrameworkMethod


class GuiDTTestRunner internal constructor(runner: GuiTestRunnerInterface) : GuiTestRunner(runner) {

  private val LOGGER = org.apache.log4j.Logger.getLogger("#com.intellij.testGuiFramework.framework.dtrace.GuiDTTestRunner")!!

  private val testClassNames: List<String> = runner.getTestClassesNames()
  private var currentClass = 0
  private var additionalJvmOptions: Array<Pair<String, String>>? = null

  override fun runIde(port: Int, ide: Ide, additionalJvmOptions: Array<Pair<String, String>>) {
    if (this.additionalJvmOptions == null)
      this.additionalJvmOptions = additionalJvmOptions

    if (currentClass < testClassNames.size) {
      LOGGER.info("" + currentClass + ". running test " + testClassNames[currentClass])
      GuiTestLocalLauncher.runIdeWithDTraceLocally(port = port,
                                                   ide = ide,
                                                   testClassName = testClassNames[currentClass],
                                                   additionalJvmOptions = additionalJvmOptions)
    }
  }

  fun finishTest(method: FrameworkMethod) {

    val server = JUnitServerHolder.getServer()

    if (++currentClass < testClassNames.size) {
      IdeProcessControlManager.killIdeProcess()
      server.stopServer()

      runIde(port = server.getPort(), ide = getIdeFromMethod(method), additionalJvmOptions = additionalJvmOptions!!)
      server.start()
    } else
      stopServerAndKillIde(server)
  }

  override fun processTestFinished(eachNotifier: EachTestNotifier,
                                   testIsRunning: Boolean): Boolean {
    val inputStream = IdeProcessControlManager.getInputStream()

    val server = JUnitServerHolder.getServer()
    closeIde(server)

    try {
      val testInstance = Class.forName(testClassNames[currentClass]).newInstance() as GuiDTTestCase
      GuiDTTestCase::checkDtraceLog.invoke(testInstance, inputStream)
    }
    catch (e: AssertionError) {
      eachNotifier.addFailure(e);
    }
    eachNotifier.fireTestFinished();
    return false
  }
}