package com.intellij.cce.execution.manager

import com.intellij.cce.core.Language
import com.intellij.cce.execution.output.ProcessExecutionLog
import com.intellij.cce.execution.output.ProcessExecutionLogImpl
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import java.io.File

abstract class CodeExecutionManager {
  companion object {
    val EP_NAME: ExtensionPointName<CodeExecutionManager> = ExtensionPointName.create("com.intellij.cce.codeExecutionManager")
    fun getForLanguage(language: Language): CodeExecutionManager? = EP_NAME.findFirstSafe { it.language == language }
  }

  abstract val language: Language
  var isTest: Boolean = true

  val collectedInfo: MutableMap<String, Any> = mutableMapOf()

  lateinit var project: Project
  lateinit var codeFilePath: File

  protected abstract fun getGeneratedCodeFile(code: String): File
  protected abstract fun saveGeneratedCodeFile(code: String)
  protected abstract fun compileGeneratedCode(): ProcessExecutionLog
  protected abstract fun executeGeneratedCode(target: String): ProcessExecutionLog

  fun compileAndExecute(project: Project, code: String, target: String): ProcessExecutionLog {
    if (project.basePath == null)
      return ProcessExecutionLogImpl("", "No project base path found", -1)

    this.project = project
    codeFilePath = getGeneratedCodeFile(code)

    saveGeneratedCodeFile(code)

    val compilationExecutionLog = compileGeneratedCode()
    if (compilationExecutionLog.exitCode != 0) return compilationExecutionLog

    return executeGeneratedCode(target)
  }
}