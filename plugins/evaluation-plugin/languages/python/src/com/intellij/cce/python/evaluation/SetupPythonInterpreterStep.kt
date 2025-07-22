// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.python.evaluation

import com.intellij.cce.core.Language
import com.intellij.cce.evaluation.EvaluationStep
import com.intellij.cce.evaluation.ForegroundEvaluationStep
import com.intellij.cce.evaluation.SetupSdkPreferences
import com.intellij.cce.evaluation.SetupSdkStepFactory
import com.intellij.cce.workspace.EvaluationWorkspace
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.python.PYTHON_FREE_PLUGIN_ID
import com.jetbrains.python.PYTHON_PROF_PLUGIN_ID
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.packaging.PyRequirementParser
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.ui.PythonPackageManagerUI
import com.jetbrains.python.packaging.management.ui.installPyRequirementsBackground
import com.jetbrains.python.packaging.pip.PipPythonPackageManager
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfiguration
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

class SetupPythonInterpreterStepFactory(private val project: Project) : SetupSdkStepFactory {
  override fun isApplicable(language: Language): Boolean = language == Language.PYTHON
  override fun steps(preferences: SetupSdkPreferences): List<EvaluationStep> = listOf(
    SetupPythonInterpreterStep(project, preferences)
  )
}

private class SetupPythonInterpreterStep(
  private val project: Project,
  private val preferences: SetupSdkPreferences,
) : ForegroundEvaluationStep {
  companion object {

  }

  override val name: String = "Set up Python Interpreter step"
  override val description: String = "Configure project Python Interpreter and install deps from requirements.txt"

  override fun start(workspace: EvaluationWorkspace): EvaluationWorkspace? {
    val projectSdk = ProjectRootManager.getInstance(project).projectSdk

    val sdk = if (projectSdk != null && (!preferences.projectLocal || isProjectLocal(projectSdk.homePath))) {
      println("Project SDK already configured")
      projectSdk
    }
    else {
      println("Project SDK not configured")
      configureSdk()
    }

    if (sdk == null) {
      return null
    }

    if (preferences.resolveDeps) {
      runBlockingCancellable {
        installPackages(sdk)
      }
    }

    return workspace
  }

  private fun configureSdk(): Sdk? {
    val projectLocalVenvDir = Path.of(project.basePath ?: error("Project path is not found")).resolve(".venv")
    val existingSdkPath = providedSdkPath() ?: projectLocalVenvSdkPath(projectLocalVenvDir)

    val sdkPath =
      if (existingSdkPath == null) systemSdkPath()?.let { initProjectLocalVenv(it, projectLocalVenvDir) }
      else {
        if (preferences.projectLocal) {
          if (isProjectLocal(existingSdkPath)) existingSdkPath
          else initProjectLocalVenv(existingSdkPath, projectLocalVenvDir)
        }
        else existingSdkPath
      }


    if (sdkPath == null) {
      println("Project SDK path was not provided. Setup `EVALUATION_PYTHON` or `PYTHONPATH` env variable")
      return null
    }

    return getSdk(sdkPath)
  }

  private fun getSdk(sdkHomePath: String): Sdk? {
    val pythonPluginEnabled = PluginManagerCore.getPlugin(PluginId.getId(PYTHON_FREE_PLUGIN_ID))?.isEnabled ?: false
    val pythonPluginProEnabled = PluginManagerCore.getPlugin(PluginId.getId(PYTHON_PROF_PLUGIN_ID))?.isEnabled ?: false
    if (!pythonPluginEnabled && !pythonPluginProEnabled) {
      println("Python plugin isn't installed. Install it before evaluation on python project")
      return null
    }

    var resultSdk: Sdk? = null
    ApplicationManager.getApplication().invokeAndWait {
      val sdkHome = WriteAction.compute<VirtualFile, RuntimeException> {
        LocalFileSystem.getInstance().refreshAndFindFileByPath(sdkHomePath)
      }
      if (sdkHome == null) {
        println("Failed to find SDK home directory at path: $sdkHomePath")
        return@invokeAndWait
      }
      val sdk = SdkConfigurationUtil.setupSdk(emptyArray(), sdkHome, PythonSdkType.getInstance(), true, null, sdkHome.path)
      if (sdk != null) {
        WriteAction.run<Throwable> {
          val sdkTable = ProjectJdkTable.getInstance()
          val existingSdk = sdkTable.findJdk(sdk.name)
          if (existingSdk?.homePath != sdk.homePath) {
            if (existingSdk != null) {
              sdkTable.removeJdk(existingSdk)
            }
            sdkTable.addJdk(sdk)
          }
          for (module in ModuleManager.getInstance(project).modules) {
            PyProjectSdkConfiguration.setReadyToUseSdkSync(project, module, sdk)
          }
          if (ProjectRootManager.getInstance(project).projectSdk == null) {
            ProjectRootManager.getInstance(project).projectSdk = sdk
          }
        }
        println("Python interpreter \"${sdk.name}\" (${sdk.homePath}) will be used as a project SDK")
        resultSdk = sdk
      }
    }
    return resultSdk
  }

  private suspend fun installPackages(sdk: Sdk) {
    val packages = readRequiredPackages()
    if (packages.isEmpty()) {
      println("No packages to install. Skipping.")
      return
    }

    // resolves `'runBlockingCancellable' is forbidden in the Write Action` from PythonSdkUpdater.scheduleUpdate
    keepTasksAsynchronousInHeadlessMode {
      val cacheOptions = if (preferences.cacheDir == null) emptyList()
      else when (PythonPackageManager.forSdk(project, sdk)) {
        is PipPythonPackageManager -> listOf("--cache-dir=${preferences.cacheDir}/pip")
        else -> emptyList()
      }
      val packageManager = PythonPackageManagerUI.forSdk(project, sdk)
      packageManager.installPyRequirementsBackground(packages, cacheOptions) ?: return@keepTasksAsynchronousInHeadlessMode
      println("Installed packages: ${packages.joinToString(", ") { it.name }}")
    }
  }

  private fun readRequiredPackages(): List<PyRequirement> {
    val projectPath = project.basePath ?: return emptyList()
    val requirementsTxt = Path.of(projectPath).resolve("requirements.txt")
    if (!requirementsTxt.exists()) {
      return emptyList()
    }
    return PyRequirementParser.fromText(requirementsTxt.readText())
  }

  private fun isProjectLocal(path: String?): Boolean {
    if (path == null) return false
    val projectDir = project.basePath ?: return false
    return Path.of(path).startsWith(Path.of(projectDir))
  }
}

private fun providedSdkPath(): String? {
  val provided = System.getenv("EVALUATION_PYTHON") ?: System.getenv("PYTHONPATH")
  return if (provided?.isNotBlank() == true) provided else null
}

private fun systemSdkPath(): String? {
  val home = "/usr/bin/python3"

  try {
    val process = ProcessBuilder(home, "--version")
      .redirectOutput(ProcessBuilder.Redirect.DISCARD)
      .redirectError(ProcessBuilder.Redirect.DISCARD)
      .start()

    process.waitFor()

    return home.takeIf { process.exitValue() == 0 }
  }
  catch (_: IOException) {
    return null
  }
}

private fun projectLocalVenvSdkPath(venvDir: Path): String? {
  if (!venvDir.exists()) return null
  return venvDir.resolve("bin/python").toAbsolutePath().toString()
}

private fun initProjectLocalVenv(parentSdkPath: String, venvDir: Path): String? {
  println("Creating virtual environment in $venvDir")

  val process = ProcessBuilder(parentSdkPath, "-m", "venv", venvDir.fileName.toString())
    .directory(venvDir.parent.toFile())
    .redirectOutput(ProcessBuilder.Redirect.INHERIT)
    .redirectError(ProcessBuilder.Redirect.INHERIT)
    .start()

  process.waitFor()

  if (process.exitValue() != 0) {
    println("Failed to create virtual environment in $venvDir/.venv")
    return null
  }

  return projectLocalVenvSdkPath(venvDir)
}

private suspend fun <T> keepTasksAsynchronousInHeadlessMode(f: suspend () -> T): T {
  val propertyName = "intellij.progress.task.ignoreHeadless"
  val previousValue = System.getProperty(propertyName)
  try {
    System.setProperty(propertyName, "true")
    return f()
  }
  finally {
    previousValue?.let {
      System.setProperty(propertyName, it)
    }
  }
}