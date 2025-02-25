// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.python.evaluation

import com.intellij.cce.core.Language
import com.intellij.cce.evaluation.SetupSdkStep
import com.intellij.cce.workspace.EvaluationWorkspace
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.python.sdk.PythonSdkType

class SetupPythonInterpreterStep(private val project: Project) : SetupSdkStep() {
  companion object {
    private const val pythonPluginId = "PythonCore"
    private const val pythonPluginProId = "Pythonid"
  }

  override val name: String = "Set up Python Interpreter step"
  override val description: String = "Configure project Python Interpreter if needed"

  override fun isApplicable(language: Language): Boolean = language == Language.PYTHON

  override fun start(workspace: EvaluationWorkspace): EvaluationWorkspace? {
    val pythonPluginEnabled = PluginManagerCore.getPlugin(PluginId.getId(pythonPluginId))?.isEnabled ?: false
    val pythonPluginProEnabled = PluginManagerCore.getPlugin(PluginId.getId(pythonPluginProId))?.isEnabled ?: false
    if (!pythonPluginEnabled && !pythonPluginProEnabled) {
      println("Python plugin isn't installed. Install it before evaluation on python project")
      return null
    }

    ApplicationManager.getApplication().invokeAndWait {
      val projectRootManager = ProjectRootManager.getInstance(project)
      val projectSdk = projectRootManager.projectSdk
      if (projectSdk != null) {
        println("Project SDK already configured")
      }
      else {
        println("Project SDK not configured")
        val sdkHomePath = System.getenv("EVALUATION_PYTHON") ?: System.getenv("PYTHONPATH")
        if (sdkHomePath == null) {
          println("Project SDK path was not provided. Setup `EVALUATION_PYTHON` or `PYTHONPATH` env variable")
        }
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
            sdkTable.addJdk(sdk)
            projectRootManager.projectSdk = sdk
          }
          println("Python interpreter \"${sdk.name}\" (${sdk.homePath}) will be used as a project SDK")
        }
      }
    }

    return workspace
  }
}
