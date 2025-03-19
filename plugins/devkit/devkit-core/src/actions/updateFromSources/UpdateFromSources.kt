// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(ExperimentalPathApi::class)
package org.jetbrains.idea.devkit.actions.updateFromSources

import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.ide.plugins.PluginInstaller
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginNode
import com.intellij.ide.plugins.marketplace.MarketplaceRequests
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.updateSettings.impl.restartOrNotify
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts.NotificationContent
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.task.ProjectTaskManager
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.Restarter
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.devkit.DevKitBundle
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.io.path.*

private val LOG = Logger.getInstance("org.jetbrains.idea.devkit.actions.updateFromSources.UpdateFromSourcesKt")

@ApiStatus.Internal
fun updateFromSources(project: Project, beforeRestart: () -> Unit, restartAutomatically: Boolean) {
  LOG.debug("Update from sources requested")

  val state = UpdateFromSourcesSettings.getState()
  val devIdeaHome = project.basePath?.let { Path.of(it) } ?: return
  val workIdeHome = Path.of(state.actualIdePath)
  if (!ApplicationManager.getApplication().isRestartCapable && state.actualIdePath.equals(PathManager.getHomePath(), ignoreCase = !SystemInfo.isFileSystemCaseSensitive)) {
    return showError(project, DevKitBundle.message("action.UpdateIdeFromSourcesAction.error.ide.cannot.restart"))
  }
  val notIdeHomeMessage = checkIdeHome(workIdeHome)
  if (notIdeHomeMessage != null) {
    return showError(project, DevKitBundle.message("action.UpdateIdeFromSourcesAction.error.work.home.not.valid.ide.home", workIdeHome, notIdeHomeMessage))
  }
  if (PathManager.getConfigDir().startsWith(workIdeHome)) {
    return showError(project, DevKitBundle.message("action.UpdateIdeFromSourcesAction.error.config.or.system.directory.under.home", workIdeHome, PathManager.PROPERTY_CONFIG_PATH))
  }
  if (PathManager.getSystemDir().startsWith(workIdeHome)) {
    return showError(project, DevKitBundle.message("action.UpdateIdeFromSourcesAction.error.config.or.system.directory.under.home", workIdeHome, PathManager.PROPERTY_SYSTEM_PATH))
  }

  val bundledPluginDirsToSkip: List<String>
  val nonBundledPluginDirsToInclude: List<String>
  val buildEnabledPluginsOnly = !state.buildDisabledPlugins
  if (buildEnabledPluginsOnly) {
    val pluginDirectoriesToSkip = LinkedHashSet(state.pluginDirectoriesForDisabledPlugins)
    pluginDirectoriesToSkip.removeAll(
      PluginManagerCore.loadedPlugins.asSequence()
        .filter { it.isBundled }
        .map { it.pluginPath }
        .filter { it.isDirectory() }
        .map { it.name }
        .toHashSet()
    )
    PluginManagerCore.plugins
      .filter { it.isBundled && !it.isEnabled }
      .map { it.pluginPath }
      .filter { it.isDirectory() }
      .mapTo(pluginDirectoriesToSkip) { it.name }
    val list = pluginDirectoriesToSkip.toMutableList()
    state.pluginDirectoriesForDisabledPlugins = list
    bundledPluginDirsToSkip = list
    nonBundledPluginDirsToInclude = PluginManagerCore.plugins
      .asSequence()
      .filter { !it.isBundled && it.isEnabled }
      .map { it.pluginPath }
      .filter { it.isDirectory() }
      .map { it.name }
      .toList()
  }
  else {
    bundledPluginDirsToSkip = emptyList()
    nonBundledPluginDirsToInclude = emptyList()
  }

  val deployDir = devIdeaHome.resolve("out/deploy")
  val builtDistDir = deployDir.resolve("dist")
  val params = createScriptJavaParameters(
    project, deployDir, builtDistDir, buildEnabledPluginsOnly, bundledPluginDirsToSkip, nonBundledPluginDirsToInclude
  ) ?: return
  val taskManager = ProjectTaskManager.getInstance(project)
  taskManager
    .run(taskManager.createModulesBuildTask(ModuleManager.getInstance(project).modules, true, true, true, false))
    .onSuccess {
      if (!it.isAborted && !it.hasErrors()) {
        runUpdateScript(params, project, workIdeHome, deployDir, builtDistDir, restartAutomatically, beforeRestart)
      }
    }
}

private fun showError(project: Project, message: @NotificationContent String, vararg actions: NotificationAction) {
  Notification("Update from Sources", DevKitBundle.message("action.UpdateIdeFromSourcesAction.task.failed.title"), message, NotificationType.ERROR)
    .addActions(actions.asList())
    .notify(project)
}

private fun checkIdeHome(workIdeHome: Path): String? {
  if (workIdeHome.notExists()) {
    return null
  }
  if (!workIdeHome.isDirectory()) {
    return DevKitBundle.message("action.UpdateIdeFromSourcesAction.error.work.home.not.valid.ide.home.not.directory")
  }
  if (workIdeHome.listDirectoryEntries().isEmpty()) {
    return null
  }
  for (name in listOf("bin", if (SystemInfo.isMac) "Resources/build.txt" else "build.txt")) {
    if (workIdeHome.resolve(name).notExists()) {
      return DevKitBundle.message("action.UpdateIdeFromSourcesAction.error.work.home.not.valid.ide.home.not.exists", name)
    }
  }
  return null
}

private fun runUpdateScript(
  params: JavaParameters,
  project: Project,
  workIdeHome: Path,
  deployDir: Path,
  builtDistDir: Path,
  restartAutomatically: Boolean,
  beforeRestart: () -> Unit,
) {
  @Suppress("UsagesOfObsoleteApi")
  object : Task.Backgroundable(project, DevKitBundle.message("action.UpdateIdeFromSourcesAction.task.title"), true) {
    override fun run(indicator: ProgressIndicator) {
      indicator.text = DevKitBundle.message("action.UpdateIdeFromSourcesAction.update.progress.text")

      indicator.text2 = DevKitBundle.message("action.UpdateIdeFromSourcesAction.update.progress.delete", builtDistDir)
      builtDistDir.deleteRecursively()

      indicator.text2 = DevKitBundle.message("action.UpdateIdeFromSourcesAction.update.progress.start.script")
      val commandLine = params.toCommandLine().withRedirectErrorStream(true)
      val scriptHandler = OSProcessHandler(commandLine)
      val output = Collections.synchronizedList(ArrayList<@NlsSafe String>())
      scriptHandler.addProcessListener(object : ProcessListener {
        override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
          output.add(event.text)
          if (outputType == ProcessOutputTypes.STDOUT) {
            indicator.text2 = event.text
          }
        }

        override fun processTerminated(event: ProcessEvent) {
          if (indicator.isCanceled) {
            return
          }

          if (event.exitCode != 0) {
            showError(
              project,
              DevKitBundle.message("action.UpdateIdeFromSourcesAction.task.failed.content", event.exitCode),
              NotificationAction.createSimple(DevKitBundle.message("action.UpdateIdeFromSourcesAction.notification.action.view.output")) {
                FileEditorManager.getInstance(project).openFile(LightVirtualFile("output.txt", output.joinToString("")), true)
              },
              NotificationAction.createSimple(DevKitBundle.message("action.UpdateIdeFromSourcesAction.notification.action.view.debug.log")) {
                LocalFileSystem.getInstance().refreshAndFindFileByNioFile(deployDir.resolve("log/debug.log"))?.let { logFile ->
                  logFile.refresh(true, false)
                  FileEditorManager.getInstance(project).openFile(logFile, true)
                }
              }
            )
            return
          }

          if (!builtDistDir.isDirectory() || builtDistDir.listDirectoryEntries().isEmpty()) {
            showError(project, DevKitBundle.message("action.UpdateIdeFromSourcesAction.error.empty.dir", builtDistDir))
            return
          }

          if (workIdeHome != Path.of(PathManager.getHomePath())) {
            startCopyingFiles(builtDistDir, workIdeHome, project)
          }
          else {
            val command = generateUpdateCommand(builtDistDir, workIdeHome)
            restartOrNotify(project, restartAutomatically) { restartWithCommand(command, deployDir, beforeRestart) }
          }
        }
      })

      scriptHandler.startNotify()
      while (!scriptHandler.isProcessTerminated) {
        scriptHandler.waitFor(300)
        indicator.checkCanceled()
      }
    }
  }.queue()
}

private fun startCopyingFiles(builtDistPath: Path, workIdeHome: Path, project: Project) {
  @Suppress("UsagesOfObsoleteApi")
  object : Task.Backgroundable(project, DevKitBundle.message("action.UpdateIdeFromSourcesAction.task.title"), true) {
    override fun run(indicator: ProgressIndicator) {
      indicator.text = DevKitBundle.message("action.UpdateIdeFromSourcesAction.copy.progress.text")

      indicator.text2 = DevKitBundle.message("action.UpdateIdeFromSourcesAction.copy.delete.old.files.text")
      workIdeHome.deleteRecursively()
      indicator.checkCanceled()

      indicator.text2 = DevKitBundle.message("action.UpdateIdeFromSourcesAction.copy.copy.new.files.text")
      workIdeHome.createDirectories()
      builtDistPath.copyToRecursively(workIdeHome, followLinks = false, overwrite = false)
      indicator.checkCanceled()

      Notification(
        "Update from Sources",
        DevKitBundle.message("action.UpdateIdeFromSourcesAction.notification.title"),
        DevKitBundle.message("action.UpdateIdeFromSourcesAction.notification.content", workIdeHome),
        NotificationType.INFORMATION
      ).notify(project)
    }
  }.queue()
}

private fun generateUpdateCommand(builtDistDir: Path, workIdeHome: Path): Array<String> {
  if (SystemInfo.isWindows) {
    val restartLogFile = PathManager.getLogDir().resolve("update-from-sources.log")
    val updateScript = Files.createTempFile("update-from-sources", ".cmd")
    val builtDistPath = builtDistDir.toAbsolutePath().toString()
    val workIdeHomePath = workIdeHome.toAbsolutePath().toString()
    /* deletion of the IDE files may fail to delete some executable files because they are still used by the IDE process,
       so the script waits for some time and tries to delete them again;
       'ping' command is used instead of 'timeout' because the latter doesn't work from batch files;
       removal of the script file is performed in a separate process to avoid errors while executing the script */
    updateScript.writeText("""
        @echo off
        SET count=20
        SET time_to_wait=1
        :DELETE_DIR
        RMDIR /Q /S "${workIdeHomePath}"
        IF EXIST "${workIdeHomePath}" (
          IF %count% GEQ 0 (
            ECHO "${workIdeHomePath}" still exists, wait %time_to_wait%s and try delete again
            SET /A time_to_wait=%time_to_wait%+1
            PING 127.0.0.1 -n %time_to_wait% >NUL
            SET /A count=%count%-1
            ECHO %count% attempts remain
            GOTO DELETE_DIR
          )
          ECHO Failed to delete "${workIdeHomePath}", IDE wasn't updated. You may delete it manually and copy files from "${builtDistPath}" by hand  
          GOTO CLEANUP_AND_EXIT 
        )
        XCOPY "${builtDistPath}" "${workIdeHomePath}"\ /Q /E /Y
        :CLEANUP_AND_EXIT
        START /b "" cmd /c DEL /Q /F "${updateScript.toAbsolutePath()}" & EXIT /b
      """.trimIndent())
    return arrayOf("cmd", "/c", updateScript.toAbsolutePath().toString(), ">${restartLogFile.toAbsolutePath()}", "2>&1")
  }
  else {
    return arrayOf("/bin/sh", "-c", """rm -rf "${workIdeHome}"/* && cp -R "${builtDistDir}"/* "${workIdeHome}"""")
  }
}

private fun restartWithCommand(command: Array<String>, deployDirPath: Path, beforeRestart: () -> Unit) {
  val pluginsDir = deployDirPath.resolve("artifacts/${ApplicationInfo.getInstance().build.productCode}-plugins")

  val nonBundledPluginsPaths = lazy { nonBundledPluginsPaths() }
  readPluginsDir(pluginsDir).forEach { newPluginNode ->
    updateNonBundledPlugin(newPluginNode, pluginsDir) { nonBundledPluginsPaths.value[it] }
  }

  Restarter.setCopyRestarterFiles()
  beforeRestart()
  (ApplicationManagerEx.getApplicationEx() as ApplicationImpl).restart(
    ApplicationEx.FORCE_EXIT or ApplicationEx.EXIT_CONFIRMED or ApplicationEx.SAVE,
    command
  )
}

private fun readPluginsDir(pluginsDirPath: Path): List<PluginNode> {
  val pluginsXml = pluginsDirPath.resolve("plugins.xml")
  if (!pluginsXml.isRegularFile()) {
    LOG.warn("Cannot read non-bundled plugins from ${pluginsXml}, they won't be updated")
    return emptyList()
  }

  return try {
    pluginsXml.inputStream().use {
      MarketplaceRequests.parsePluginList(it)
    }
  }
  catch (e: Exception) {
    LOG.error("Failed to parse $pluginsXml", e)
    emptyList()
  }
}

private fun nonBundledPluginsPaths(): Map<PluginId, Path> = PluginManagerCore.loadedPlugins
  .asSequence()
  .filterNot { it.isBundled }
  .associate { it.pluginId to it.pluginPath }
  .also { LOG.debug("Existing custom plugins: $it") }

private fun updateNonBundledPlugin(newDescriptor: PluginNode, pluginDir: Path, oldPluginPathProvider: (PluginId) -> Path?) {
  assert(!newDescriptor.isBundled)
  val oldPluginPath = oldPluginPathProvider(newDescriptor.pluginId) ?: return

  val newPluginPath = pluginDir.resolve(newDescriptor.downloadUrl)
    .also { LOG.debug("Adding update command: ${oldPluginPath} to ${it}") }

  PluginInstaller.installAfterRestart(newDescriptor, newPluginPath, oldPluginPath, false)
}

private fun createScriptJavaParameters(
  project: Project,
  deployDir: Path,
  builtDistPath: Path,
  buildEnabledPluginsOnly: Boolean,
  bundledPluginDirsToSkip: List<String>,
  nonBundledPluginDirsToInclude: List<String>,
): JavaParameters? {
  val sdk = ProjectRootManager.getInstance(project).projectSdk
  if (sdk == null) {
    showError(project, DevKitBundle.message("action.UpdateIdeFromSourcesAction.error.no.sdk"))
    return null
  }

  val moduleManager = ModuleManager.getInstance(project)
  val ultimate = moduleManager.findModuleByName("intellij.idea.ultimate.main") != null
  val params = JavaParameters()
  params.isUseClasspathJar = true
  params.setDefaultCharset(project)
  params.jdk = sdk

  params.mainClass = if (ultimate) "UltimateUpdateFromSourcesBuildTarget" else "OpenSourceCommunityUpdateFromSourcesBuildTarget"
  params.programParametersList.add("--classpath")
  val buildScriptsModuleName = if (ultimate) "intellij.idea.ultimate.build" else "intellij.idea.community.build"
  val buildScriptsModule = moduleManager.findModuleByName(buildScriptsModuleName)
  if (buildScriptsModule == null) {
    showError(project, DevKitBundle.message("action.UpdateIdeFromSourcesAction.error.no.module", buildScriptsModuleName))
    return null
  }
  val classpath = OrderEnumerator.orderEntries(buildScriptsModule)
    .recursively().withoutSdk().runtimeOnly().productionOnly().classes().pathsList.pathList

  params.classPath.addAll(classpath)

  params.vmParametersList.add("-Dintellij.build.bundled.jre.prefix=jbrsdk_jcef-")

  if (buildEnabledPluginsOnly) {
    if (bundledPluginDirsToSkip.isNotEmpty()) {
      params.vmParametersList.add("-Dintellij.build.bundled.plugin.dirs.to.skip=${bundledPluginDirsToSkip.joinToString(",")}")
    }
    val nonBundled = if (nonBundledPluginDirsToInclude.isNotEmpty()) nonBundledPluginDirsToInclude.joinToString(",") else "none"
    params.vmParametersList.add("-Dintellij.build.non.bundled.plugin.dirs.to.include=$nonBundled")
  }

  if (!buildEnabledPluginsOnly || nonBundledPluginDirsToInclude.isNotEmpty()) {
    params.vmParametersList.add("-Dintellij.build.local.plugins.repository=true")
  }
  params.vmParametersList.add("-Dintellij.build.output.root=${deployDir}")
  params.vmParametersList.add("-DdistOutputRelativePath=${deployDir.relativize(builtDistPath)}")
  return params
}
