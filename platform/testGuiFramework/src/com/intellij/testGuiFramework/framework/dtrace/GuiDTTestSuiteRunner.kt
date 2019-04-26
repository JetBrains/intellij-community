// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.framework.dtrace

import com.intellij.testGuiFramework.framework.GuiTestLocalRunner
import com.intellij.testGuiFramework.framework.GuiTestSuiteRunner
import com.intellij.testGuiFramework.launcher.GuiTestLocalLauncher
import com.intellij.testGuiFramework.launcher.ide.Ide
import com.intellij.testGuiFramework.remote.IdeControl
import org.apache.log4j.Logger
import org.junit.runners.model.RunnerBuilder

class GuiDTTestSuiteRunner(suiteClass: Class<*>, builder: RunnerBuilder) : GuiTestSuiteRunner(suiteClass, builder) {
  private val LOG: Logger = org.apache.log4j.Logger.getLogger("#com.intellij.testGuiFramework.framework.dtrace.GuiDTTestSuiteRunner")!!

  override fun createGuiTestLocalRunner(testClass:Class<*>, suiteClass:Class<*>, myIde: Ide): GuiTestLocalRunner {
    IdeControl.closeIde()
    return GuiDTTestLocalRunner(testClass, suiteClass, myIde)
  }

}
