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
package com.intellij.testGuiFramework.recorder

import com.intellij.openapi.command.WriteCommandAction

object Writer {

  val indent = 2

  private fun writeln(str: String) {
    write(str + "\n")
  }

  private fun write(str: String) {
    print(str)
    writeToEditor(str)
  }

  private fun writeToEditor(str: String) {
    val document = GuiRecorderManager.getEditor().document
    WriteCommandAction.runWriteCommandAction(null, { document.insertString(document.textLength, str) })
  }

  fun writeWithIndent(code: String){
    val indentedString = (0..(indent * ContextChecker.getContextDepth() - 1)).map { ' ' }.joinToString(separator = "")
    Writer.writeln("$indentedString$code")
  }

}