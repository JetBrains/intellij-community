package com.intellij.cce.execution.manager

import com.intellij.cce.core.Language
import com.intellij.cce.evaluable.AIA_EXECUTION_SUCCESS_RATIO
import com.intellij.cce.evaluable.AIA_TEST_BRANCH_COVERAGE
import com.intellij.cce.evaluable.AIA_TEST_FILE_PROVIDED
import com.intellij.cce.evaluable.AIA_TEST_LINE_COVERAGE
import com.intellij.cce.execution.output.ProcessExecutionLog
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import java.io.File

abstract class CodeExecutionManager {
  companion object {
    val EP_NAME: ExtensionPointName<CodeExecutionManager> = ExtensionPointName.create("com.intellij.cce.codeExecutionManager")
    fun getForLanguage(language: Language): CodeExecutionManager? = EP_NAME.findFirstSafe { it.language == language }
  }

  abstract val language: Language
  private var shouldSetup: Boolean = true

  private val executionBasedMetrics = listOf(
    AIA_EXECUTION_SUCCESS_RATIO,
    AIA_TEST_LINE_COVERAGE,
    AIA_TEST_BRANCH_COVERAGE,
    AIA_TEST_FILE_PROVIDED)

  protected val collectedInfo: MutableMap<String, Any> = mutableMapOf()

  protected abstract fun getGeneratedCodeFile(basePath: String, code: String): File
  protected abstract fun compileGeneratedCode(): ProcessExecutionLog
  protected abstract fun setupEnvironment(basePath: String, sdk: Sdk?): ProcessExecutionLog
  protected abstract fun executeGeneratedCode(target: String, basePath: String, codeFilePath: File, sdk: Sdk?): ProcessExecutionLog

  // Protected since there can be language-specific metrics
  protected fun clear() {
    executionBasedMetrics.forEach {
      // Setting default value for every metric
      collectedInfo[it] = 0
    }
  }

  fun compileAndExecute(project: Project, code: String, target: String): ProcessExecutionLog {
    // Clear collectedInfo
    clear()
    val basePath = project.basePath
    val sdk: Sdk? = ProjectRootManager.getInstance(project).projectSdk

    if (sdk == null && language.needSdk) return ProcessExecutionLog("", "No SDK found", -1)
    basePath ?: return ProcessExecutionLog("", "No project base path found", -1)

    // Get the path to the temp file that the generated code should be saved in
    val codeFile = getGeneratedCodeFile(basePath, code)
    // Save code in a temp file
    codeFile.writeText(code)
    // If this is the first execution, the plugin might need to set up the environment
    if (shouldSetup) {
      setupEnvironment(basePath, sdk)
      shouldSetup = false
    }
    // Compile
    val compilationExecutionLog = compileGeneratedCode()
    if (compilationExecutionLog.exitCode != 0) return compilationExecutionLog
    // Execute
    val executionLog = executeGeneratedCode(target, basePath, codeFile, sdk)
    // Clean the temp file containing the generated code
    codeFile.delete()

    return executionLog
  }

  fun getMetricsInfo(): Map<String, Any> = collectedInfo.toMap()
}