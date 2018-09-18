// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.framework.dtrace

import com.intellij.testGuiFramework.framework.GuiTestLocalRunner
import com.intellij.testGuiFramework.launcher.ide.Ide
import org.junit.runner.notification.RunNotifier
import org.junit.runners.model.FrameworkMethod

class GuiDTTestLocalRunner: GuiTestLocalRunner {

  constructor(testClass: Class<*>, suiteClass: Class<*>, ide: Ide?) : super(testClass, suiteClass, ide) {
    setTestRunner(GuiDTTestRunner(this))
  }

  constructor(testClass: Class<*>, ideFromTest: Ide?) : super(testClass, ideFromTest) {
    setTestRunner(GuiDTTestRunner(this))
  }

  constructor(testClass: Class<*>) : this(testClass, null)

  override fun runChild(method: FrameworkMethod, notifier: RunNotifier) {
    super.runChild(method, notifier)
    (runner as GuiDTTestRunner).finishTest(method)
  }

}