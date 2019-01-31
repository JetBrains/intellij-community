// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.framework.dtrace

import com.intellij.testGuiFramework.framework.GuiTestLocalRunner
import com.intellij.testGuiFramework.impl.GuiTestCase
import org.junit.runner.RunWith
import java.io.InputStream

@RunWith(GuiTestLocalRunner::class)
abstract class GuiDTTestCase : GuiTestCase() {
  open fun getTestSrcPath(): String {
    return System.getProperties().get("user.dir") as String
  }
  abstract fun getDTScriptName(): String
  abstract fun checkDtraceLog(inStream: InputStream)
}
