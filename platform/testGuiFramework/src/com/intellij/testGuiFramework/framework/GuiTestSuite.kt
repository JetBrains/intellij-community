// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.framework

import com.intellij.testGuiFramework.launcher.GuiTestOptions
import com.intellij.testGuiFramework.remote.IdeControl
import com.intellij.util.io.delete
import org.junit.AfterClass
import org.junit.BeforeClass
import java.io.File

open class GuiTestSuite {
  companion object {
    @BeforeClass
    @JvmStatic
    fun setUp() {
    }

    @AfterClass
    @JvmStatic
    fun tearDown() {
      IdeControl.closeIde()
      collectJvmErrors()
      GuiTestOptions.projectsDir.delete()
    }

    private fun collectJvmErrors() {
      GuiTestOptions.projectsDir.toFile().walk()
        .maxDepth(3)
        .filter { it.name.startsWith("hs_err") }
        .forEach {
          it.copyTo(File(GuiTestPaths.failedTestScreenshotDir, it.name))
        }
    }
  }
}
