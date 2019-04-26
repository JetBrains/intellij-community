/*
 * Copyright 2000-2018 JetBrains s.r.o.
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

import com.intellij.testGuiFramework.launcher.GuiTestOptions
import com.intellij.testGuiFramework.remote.IdeControl
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
      GuiTestOptions.projectsDir.deleteRecursively()
    }

    private fun collectJvmErrors() {
      GuiTestOptions.projectsDir.walk()
        .maxDepth(3)
        .filter { it.name.startsWith("hs_err") }
        .forEach {
          it.copyTo(File(GuiTestPaths.failedTestScreenshotDir, it.name))
        }
    }
  }
}
