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
package com.intellij.testGuiFramework.recorder.compile

object ScriptWrapper {

  val TEST_METHOD_NAME = "testMe"

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
      "import com.intellij.testGuiFramework.impl.GuiTestUtilKt.waitProgressDialogUntilGone")
    {
      classWrap {
        funWrap {
          code
        }
      }
    }
}