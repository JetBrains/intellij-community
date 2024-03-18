// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project.impl

import com.intellij.ide.actions.OpenFileAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ActionRuntimeRegistrar
import com.intellij.openapi.actionSystem.impl.ActionConfigurationCustomizer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.CustomConfigMigrationOption
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.ex.LowLevelProjectOpenProcessor
import com.intellij.openapi.project.ex.PerProjectInstancePaths
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.Restarter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CancellationException
import kotlin.io.path.div
import kotlin.io.path.exists

const val PER_PROJECT_INSTANCE_TEST_SCRIPT: String = "test_script.txt"

private class SeparateProcessActionsCustomizer : ActionConfigurationCustomizer, ActionConfigurationCustomizer.AsyncLightCustomizeStrategy {
  override suspend fun customize(actionRegistrar: ActionRuntimeRegistrar) {
    if (!ProjectManagerEx.IS_CHILD_PROCESS) {
      return
    }

    // see com.jetbrains.thinclient.ThinClientActionsCustomizer

    // we don't remove this action in case some code uses it
    actionRegistrar.replaceAction("OpenFile", NewProjectActionDisabler())
    val fileOpenGroup = actionRegistrar.getActionOrStub("FileOpenGroup") as DefaultActionGroup
    fileOpenGroup.removeAll()
    actionRegistrar.unregisterAction("RecentProjectListGroup")
  }
}

private class NewProjectActionDisabler : OpenFileAction() {
  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = false
  }

  override fun actionPerformed(e: AnActionEvent) {
  }
}

internal suspend fun checkChildProcess(projectStoreBaseDir: Path): Boolean {
  if (shouldOpenInChildProcess(projectStoreBaseDir)) {
    openInChildProcess(projectStoreBaseDir)
    if (!ProjectManagerEx.IS_CHILD_PROCESS) {
      withContext(Dispatchers.EDT) {
        ApplicationManagerEx.getApplicationEx().exit(true, true)
      }
    }
    else {
      return true
    }
  }
  else if (ProjectManagerEx.IS_PER_PROJECT_INSTANCE_ENABLED) {
    for (processor in LowLevelProjectOpenProcessor.EP_NAME.extensions) {
      if (processor.beforeProjectOpened(projectStoreBaseDir) == LowLevelProjectOpenProcessor.PrepareProjectResult.CANCEL) {
        logger<ProjectManagerImpl>().info("Project opening preparation has been cancelled")
        throw ProcessCanceledException()
      }
    }
  }
  return false
}

private suspend fun openInChildProcess(projectStoreBaseDir: Path) {
  try {
    withContext(Dispatchers.IO) {
      setConfigImportOptionForPerProject(projectStoreBaseDir)
      ProcessBuilder(openProjectInstanceCommand(projectStoreBaseDir))
        .redirectErrorStream(true)
        .redirectOutput(ProcessBuilder.Redirect.appendTo(PathManager.getLogDir().resolve("idea.log").toFile()))
        .start()
        .also {
          logger<ProjectManagerImpl>().info("Child process started, PID: ${it.pid()}")
        }
    }
  }
  catch (e: CancellationException) {
    throw e
  }
  catch (e: Exception) {
    logger<ProjectManagerImpl>().error(e)
  }
}

private suspend fun shouldOpenInChildProcess(projectStoreBaseDir: Path): Boolean {
  if (!ProjectManagerEx.IS_PER_PROJECT_INSTANCE_READY) {
    return false
  }

  if (!ApplicationManager.getApplication().isHeadlessEnvironment && java.lang.Boolean.getBoolean("ide.per.project.instance.debug")) {
    withContext(Dispatchers.EDT) {
      @Suppress("HardCodedStringLiteral")
      Messages.showMessageDialog(
        null,
        "Start `Remote JVM Debug` configuration now",
        "Debugger Guard",
        null
      )
    }
  }

  val sameSystemPath = withContext(Dispatchers.IO) {
    val childSystemPath = PerProjectInstancePaths(projectStoreBaseDir).getSystemDir()
    childSystemPath.exists() && Files.isSameFile(PathManager.getSystemDir(), childSystemPath)
  }

  if (sameSystemPath) {
    return false
  }

  return LowLevelProjectOpenProcessor.EP_NAME.extensions.any { it.shouldOpenInNewProcess(projectStoreBaseDir) }
}

private fun openProjectInstanceArgs(projectStoreBaseDir: Path): List<String> {
  val instancePaths = PerProjectInstancePaths(projectStoreBaseDir)

  return buildList {
    addAll(mapOf(
      PathManager.PROPERTY_SYSTEM_PATH to instancePaths.getSystemDir(),
      PathManager.PROPERTY_CONFIG_PATH to instancePaths.getConfigDir(),
      PathManager.PROPERTY_LOG_PATH to instancePaths.getLogDir(),
      PathManager.PROPERTY_PLUGINS_PATH to PathManager.getPluginsDir(),
      ProjectManagerEx.PER_PROJECT_OPTION_NAME to ProjectManagerEx.PerProjectState.ENABLED
    ).map { (key, value) ->
      "-D$key=$value"
    }.toTypedArray())

    if (ApplicationManagerEx.isInIntegrationTest()) {
      val customTestScriptPath = PerProjectInstancePaths(projectStoreBaseDir).getSystemDir().resolve(PER_PROJECT_INSTANCE_TEST_SCRIPT)
      @Suppress("SpellCheckingInspection")
      add("-Dtestscript.filename=${customTestScriptPath}")

      // Do not write metrics from the 2nd+ instances to the main json file
      if (ProjectManagerEx.IS_CHILD_PROCESS) {
        add("-Didea.diagnostic.opentelemetry.file=${instancePaths.getLogDir() / "opentelemetry.json"}")
      }
    }
  }
}

private fun macOpenProjectInstanceCommand(projectStoreBaseDir: Path): List<String> {
  return buildList {
    add("open")
    add("-n")
    add(Restarter.getIdeStarter().toString())
    add("--args")
    addAll(openProjectInstanceArgs(projectStoreBaseDir))
    add(projectStoreBaseDir.toString())
  }
}

private fun linuxOpenProjectInstanceCommand(projectStoreBaseDir: Path): List<String> {
  return buildList {
    add(Restarter.getIdeStarter().toString())
    addAll(openProjectInstanceArgs(projectStoreBaseDir))
    add(projectStoreBaseDir.toString())
  }
}

private fun openProjectInstanceCommand(projectStoreBaseDir: Path): List<String> {
  return when {
    SystemInfo.isMac -> macOpenProjectInstanceCommand(projectStoreBaseDir)
    SystemInfo.isLinux -> linuxOpenProjectInstanceCommand(projectStoreBaseDir)
    else -> emptyList()
  }
}

private fun setConfigImportOptionForPerProject(projectStoreBaseDir: Path) {
  val currentConfigDir = PathManager.getConfigDir()
  val newConfigDir = PerProjectInstancePaths(projectStoreBaseDir).getConfigDir()

  // do not copy config if it already exists
  if (Files.isDirectory(newConfigDir) && !isEmpty(newConfigDir)) {
    return
  }

  CustomConfigMigrationOption.MigrateFromCustomPlace(currentConfigDir).writeConfigMarkerFile(newConfigDir)
}

private fun isEmpty(path: Path): Boolean {
  if (Files.isDirectory(path)) {
    Files.list(path).use { entries -> return !entries.findFirst().isPresent }
  }
  return false
}
