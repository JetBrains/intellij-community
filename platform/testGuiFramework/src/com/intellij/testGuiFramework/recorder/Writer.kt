// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.recorder

import com.intellij.openapi.command.WriteCommandAction

object Writer {

  const val indent: Int = 2

  private fun writeln(str: String) {
    write(str + "\n")
  }

  private fun write(str: String) {
    print(str)
    GeneratedCodeReceiver.sendCode(str)
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