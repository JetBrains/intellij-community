// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.recorder.compile

object ScriptWrapper {

  const val TEST_METHOD_NAME: String = "testMe"

  private fun classWrap(function: () -> (String)): String = "class CurrentTest: GuiTestCase() {\n${function.invoke()}\n}"
  private fun funWrap(function: () -> String): String = "fun $TEST_METHOD_NAME(){\n${function.invoke()}\n}"

  private fun importsWrap(vararg imports: String, function: () -> String): String {
    val sb = StringBuilder()
    imports.forEach { sb.append("$it\n") }
    sb.append(function.invoke())
    return sb.toString()
  }

  fun wrapScript(code: String): String =
    importsWrap(
      "import com.intellij.testGuiFramework.* ",
      "import com.intellij.testGuiFramework.fixtures.*",
      "import com.intellij.testGuiFramework.framework.*",
      "import com.intellij.testGuiFramework.impl.*",
      "import org.fest.swing.core.Robot",
      "import java.awt.Component",
      "import com.intellij.openapi.application.ApplicationManager",
      "import org.fest.swing.fixture.*",
      "import com.intellij.testGuiFramework.util.Key.*",
      "import com.intellij.testGuiFramework.util.Modifier.*",
      "import com.intellij.testGuiFramework.util.plus",
      "import com.intellij.testGuiFramework.impl.GuiTestUtilKt.waitProgressDialogUntilGone")
    {
      classWrap {
        funWrap {
          code
        }
      }
    }
}