package com.intellij.cce.execution.manager

import com.intellij.cce.core.Language
import com.intellij.cce.execution.output.ProcessExecutionLog
import com.intellij.openapi.extensions.ExtensionPointName

interface CodeExecutionManager {
  companion object {
    val EP_NAME: ExtensionPointName<CodeExecutionManager> = ExtensionPointName.create("com.intellij.cce.codeExecutionManager")
    fun getForLanguage(language: Language): CodeExecutionManager? = EP_NAME.findFirstSafe { it.language == language }
  }
  val language: Language
  var isTest: Boolean

  fun saveFile(code: String)
  fun compile(): ProcessExecutionLog
  fun execute(): ProcessExecutionLog
}