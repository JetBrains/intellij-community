package com.intellij.cce.execution.manager

import com.intellij.cce.core.Language
import com.intellij.cce.evaluable.AIA_EXECUTION_SUCCESS_RATIO
import com.intellij.cce.evaluable.AIA_TEST_BRANCH_COVERAGE
import com.intellij.cce.evaluable.AIA_TEST_FILE_PROVIDED
import com.intellij.cce.evaluable.AIA_TEST_LINE_COVERAGE
import com.intellij.cce.evaluation.data.ExecutionMode
import com.intellij.cce.execution.output.ProcessExecutionLog
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi.PsiNamedElement
import com.intellij.util.io.delete
import java.io.File
import java.nio.file.Path
import kotlin.io.path.writeText

abstract class CodeExecutionManager {
  companion object {
    val EP_NAME: ExtensionPointName<CodeExecutionManager> = ExtensionPointName.create("com.intellij.cce.codeExecutionManager")
    fun getForLanguage(language: Language, executionMode: ExecutionMode): CodeExecutionManager? = EP_NAME.findFirstSafe {
      it.language == language && it.executionMode == executionMode
    }
  }

  abstract val language: Language
  abstract val executionMode: ExecutionMode?

  private val executionBasedMetrics = listOf(AIA_EXECUTION_SUCCESS_RATIO, AIA_TEST_LINE_COVERAGE, AIA_TEST_BRANCH_COVERAGE, AIA_TEST_FILE_PROVIDED)

  val collectedInfo: MutableMap<String, Any> = mutableMapOf()

  abstract fun removeEnvironment()


  protected abstract fun getGeneratedCodeFile(basePath: String, code: String): Path
  protected abstract fun compileGeneratedCode(): ProcessExecutionLog
  abstract fun setupEnvironment(project: Project, sdk: Sdk?, setupCommands: List<String>)
  protected abstract fun executeGeneratedCode(target: String, basePath: String, codeFilePath: Path, sdk: Sdk?, unitUnderTest: PsiNamedElement?): ProcessExecutionLog

  // Protected since there can be language-specific metrics
  protected fun clear() {
    executionBasedMetrics.forEach { // Setting default value for every metric
      collectedInfo[it] = 0
    }
  }

  protected fun constructScriptFiles(basePath: String, setupCommands: List<String>) {
    val setupFile = File("$basePath/setup_tests.sh")
    var content = StringBuilder("#!/bin/bash\n" +
                                "\n" +
                                "# Set the path to your virtual environment\n" +
                                "VENV_PATH=\"./venv\"  # Adjust this path if your virtual environment is located elsewhere\n" +
                                "\n" +
                                "# Remove the existing virtual environment to ensure a clean setup\n" +
                                "if [ -d \"\$VENV_PATH\" ]; then\n" +
                                "    echo \"Removing existing virtual environment...\"\n" +
                                "    rm -rf \"\$VENV_PATH\"\n" +
                                "fi\n" +
                                "\n" +
                                "# Create a new virtual environment\n" +
                                "echo \"Creating a new virtual environment...\"\n" +
                                "PYTHON_ENV=\${PYTHON:-\"python3\"}\n" +
                                "\"\$PYTHON_ENV\" -m venv \"\$VENV_PATH\"\n" +
                                "\n" +
                                "# Activate the virtual environment\n" +
                                "echo \"Activating virtual environment...\"\n" +
                                "source \"\$VENV_PATH/bin/activate\"\n" +
                                "\n" +
                                "\n" +
                                "# Install dependencies from README.md\n" +
                                "echo \"Installing requirements...\"\n")
    content.append(setupCommands.joinToString("\n"))
    setupFile.writeText(content.toString())

    val runFile = File("$basePath/run_tests.sh")
    content = StringBuilder("#!/bin/bash\n" +
                            "\n" +
                            "echo \"Activating virtual environment...\"\n" +
                            "VENV_PATH=\"./venv\"\n" +
                            "source \"\$VENV_PATH/bin/activate\"\n" +
                            "\n" +
                            "# Check if a specific test file or module is passed as an argument\n" +
                            "if [ -z \"\$1\" ]; then\n" +
                            "    TEST_TARGET=\"\"\n" +
                            "else\n" +
                            "    TEST_TARGET=\"\$1\"\n" +
                            "fi\n" +
                            "\n" +
                            "# Run tests (with optional specific file/module)\n" +
                            "echo \"Running tests...\"\n" +
                            "PYTHONPATH=. pytest -v \"\$TEST_TARGET.py\" --rootdir=. --junit-xml=\$TEST_TARGET-junit --cov=\"\$2\" --cov-branch --cov-report json:\$TEST_TARGET-coverage\n")
    runFile.writeText(content.toString())
  }


  fun compileAndExecute(project: Project, code: String, target: String, unitUnderTest: PsiNamedElement?): ProcessExecutionLog {
    // Clear collectedInfo
    clear()
    val basePath = project.basePath
    val sdk: Sdk? = ProjectRootManager.getInstance(project).projectSdk

    basePath ?: return ProcessExecutionLog("", "No project base path found", -1)

    // Get the path to the temp file that the generated code should be saved in
    val codeFile = getGeneratedCodeFile(basePath, code)
    // Save code in a temp file
    codeFile.writeText(code)
    // Compile
    val compilationExecutionLog = compileGeneratedCode()
    if (compilationExecutionLog.exitCode != 0) return compilationExecutionLog
    // Execute
    val executionLog = executeGeneratedCode(target, basePath, codeFile, sdk, unitUnderTest)
    // Clean the temp file containing the generated code
    codeFile.delete()

    return executionLog
  }

  fun getMetricsInfo(): Map<String, Any> = collectedInfo.toMap()
}