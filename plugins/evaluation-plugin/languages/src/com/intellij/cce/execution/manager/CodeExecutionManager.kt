package com.intellij.cce.execution.manager

import com.intellij.cce.core.Language
import com.intellij.cce.execution.output.ProcessExecutionLog
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import java.io.File

abstract class CodeExecutionManager {
  companion object {
    val EP_NAME: ExtensionPointName<CodeExecutionManager> = ExtensionPointName.create("com.intellij.cce.codeExecutionManager")
    fun getForLanguage(language: Language): CodeExecutionManager? = EP_NAME.findFirstSafe { it.language == language }
  }

  abstract val language: Language
  private var shouldSetup: Boolean = true

  protected val collectedInfo: MutableMap<String, Any> = mutableMapOf()

  lateinit var project: Project

  protected abstract fun getGeneratedCodeFile(code: String): File
  protected abstract fun compileGeneratedCode(): ProcessExecutionLog
  protected abstract fun setupEnvironment(): ProcessExecutionLog
  protected abstract fun executeGeneratedCode(target: String, codeFilePath: File): ProcessExecutionLog

  fun compileAndExecute(project: Project, code: String, target: String): ProcessExecutionLog {
    if (project.basePath == null)
      return ProcessExecutionLog("", "No project base path found", -1, collectedInfo.toMap())
    this.project = project

    // Get the path to the temp file that the generated code should be saved in
    val codeFile = getGeneratedCodeFile(code)
    // Save code in a temp file
    codeFile.writeText(code)
    // If this is the first execution, the plugin might need to set up the environment
    if (shouldSetup) {
      setupEnvironment()
      shouldSetup= false
    }
    // Compile
    val compilationExecutionLog = compileGeneratedCode()
    if (compilationExecutionLog.exitCode != 0) return compilationExecutionLog
    // Execute
    val executionLog = executeGeneratedCode(target, codeFile)
    // Clean the temp file containing the generated code
    codeFile.delete()

    return executionLog
  }
}