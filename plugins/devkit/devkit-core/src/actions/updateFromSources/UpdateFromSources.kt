// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.util.NlsContexts.DialogMessage
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.task.ProjectTaskManager
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.Restarter
import org.jetbrains.annotations.NonNls
import org.jetbrains.idea.devkit.DevKitBundle
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

private val LOG = Logger.getInstance("org.jetbrains.idea.devkit.actions.updateFromSources.UpdateFromSourcesKt")

fun updateFromSources(project: Project, beforeRestart: () -> Unit, error: (@DialogMessage String) -> Unit, restartAutomatically: Boolean) {
  LOG.debug("Update from sources requested")
  val state = UpdateFromSourcesSettings.getState()
  val devIdeaHome = project.basePath ?: return
  val workIdeHome = state.actualIdePath
  if (!ApplicationManager.getApplication().isRestartCapable && FileUtil.pathsEqual(workIdeHome, PathManager.getHomePath())) {
    return error(DevKitBundle.message("action.UpdateIdeFromSourcesAction.error.ide.cannot.restart"))
  }

  val notIdeHomeMessage = checkIdeHome(workIdeHome)
  if (notIdeHomeMessage != null) {
    return error(DevKitBundle.message("action.UpdateIdeFromSourcesAction.error.work.home.not.valid.ide.home",
                                      workIdeHome, notIdeHomeMessage))
  }

  if (FileUtil.isAncestor(workIdeHome, PathManager.getConfigPath(), false)) {
    return error(DevKitBundle.message("action.UpdateIdeFromSourcesAction.error.config.or.system.directory.under.home", workIdeHome, PathManager.PROPERTY_CONFIG_PATH))
  }
  if (FileUtil.isAncestor(workIdeHome, PathManager.getSystemPath(), false)) {
    return error(DevKitBundle.message("action.UpdateIdeFromSourcesAction.error.config.or.system.directory.under.home", workIdeHome, PathManager.PROPERTY_SYSTEM_PATH))
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

  val deployDir = "$devIdeaHome/out/deploy" // NON-NLS
  val distRelativePath = "dist" // NON-NLS
  val backupDir = "$devIdeaHome/out/backup-before-update-from-sources" // NON-NLS
  val params = createScriptJavaParameters(project = project,
                                          deployDir = deployDir,
                                          distRelativePath = distRelativePath,
                                          buildEnabledPluginsOnly = buildEnabledPluginsOnly,
                                          bundledPluginDirsToSkip = bundledPluginDirsToSkip,
                                          nonBundledPluginDirsToInclude = nonBundledPluginDirsToInclude) ?: return
  val taskManager = ProjectTaskManager.getInstance(project)
  taskManager
    .run(taskManager.createModulesBuildTask(ModuleManager.getInstance(project).modules, true, true, true, false))
    .onSuccess {
      if (!it.isAborted && !it.hasErrors()) {
        runUpdateScript(params, project, workIdeHome, deployDir, distRelativePath, backupDir, restartAutomatically, beforeRestart)
      }
    }
}

private fun checkIdeHome(workIdeHome: String): String? {
  val homeDir = Path.of(workIdeHome)
  if (Files.notExists(homeDir)) {
    return null
  }

  if (!Files.isDirectory(homeDir)) {
    return DevKitBundle.message("action.UpdateIdeFromSourcesAction.error.work.home.not.valid.ide.home.not.directory")
  }

  val buildTxt = if (SystemInfo.isMac) "Resources/build.txt" else "build.txt" // NON-NLS
  for (name in listOf("bin", buildTxt)) {
    if (Files.notExists(homeDir.resolve(name))) {
      return DevKitBundle.message("action.UpdateIdeFromSourcesAction.error.work.home.not.valid.ide.home.not.exists", name)
    }
  }
  return null
}

@Suppress("SameParameterValue")
private fun runUpdateScript(params: JavaParameters,
                            project: Project,
                            workIdeHome: String,
                            deployDirPath: String,
                            distRelativePath: String,
                            backupDir: String,
                            restartAutomatically: Boolean,
                            beforeRestart: () -> Unit) {
  val builtDistPath = "$deployDirPath/$distRelativePath"
  object : Task.Backgroundable(project, DevKitBundle.message("action.UpdateIdeFromSourcesAction.task.title"), true) {
    override fun run(indicator: ProgressIndicator) {
      indicator.text = DevKitBundle.message("action.UpdateIdeFromSourcesAction.update.progress.text")
      backupImportantFilesIfNeeded(workIdeHome, backupDir, indicator)
      indicator.text2 = DevKitBundle.message("action.UpdateIdeFromSourcesAction.update.progress.delete", builtDistPath)
      NioFiles.deleteRecursively(Path.of(builtDistPath))
      indicator.text2 = DevKitBundle.message("action.UpdateIdeFromSourcesAction.update.progress.start.script", ULTIMATE_UPDATE_FROM_SOURCES_BUILD_TARGET)
      val commandLine = params.toCommandLine()
      commandLine.isRedirectErrorStream = true
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
            Notification(
              "Update from Sources",
              DevKitBundle.message("action.UpdateIdeFromSourcesAction.task.failed.title"),
              DevKitBundle.message("action.UpdateIdeFromSourcesAction.task.failed.content",
                                   event.exitCode),
              NotificationType.ERROR
            ).addAction(NotificationAction.createSimple(DevKitBundle.message("action.UpdateIdeFromSourcesAction.notification.action.view.output")) {
              FileEditorManager.getInstance(project).openFile(LightVirtualFile("output.txt", output.joinToString("")), true)
            }
            ).addAction(NotificationAction.createSimple(DevKitBundle.message("action.UpdateIdeFromSourcesAction.notification.action.view.debug.log")) {
              val logFile = LocalFileSystem.getInstance().refreshAndFindFileByPath("$deployDirPath/log/debug.log") ?: return@createSimple // NON-NLS
              logFile.refresh(true, false)
              FileEditorManager.getInstance(project).openFile(logFile, true)
            }
            ).notify(project)
            return
          }

          if (!FileUtil.pathsEqual(workIdeHome, PathManager.getHomePath())) {
            startCopyingFiles(builtDistPath, workIdeHome, project)
            return
          }

          val command = generateUpdateCommand(builtDistPath, workIdeHome)
          restartOrNotify(project, restartAutomatically) { restartWithCommand(command, deployDirPath, beforeRestart) }
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

private fun backupImportantFilesIfNeeded(workIdeHome: String,
                                         backupDirPath: String,
                                         indicator: ProgressIndicator) {
  val backupDir = Path.of(backupDirPath)
  if (Files.exists(backupDir)) {
    LOG.debug("$backupDir already exists, skipping backup")
    return
  }

  LOG.debug("Backing up files from $workIdeHome to $backupDir")
  indicator.text2 = DevKitBundle.message("action.UpdateIdeFromSourcesAction.backup.progress.text")
  Files.createDirectories(backupDir)
  File(workIdeHome, "bin").listFiles()
    ?.filter { it.name !in safeToDeleteFilesInBin && it.extension !in safeToDeleteExtensions }
    ?.forEach { FileUtil.copy(it, backupDir.resolve("bin/${it.name}").toFile()) }

  File(workIdeHome).listFiles()
    ?.filter { it.name !in safeToDeleteFilesInHome }
    ?.forEach { FileUtil.copyFileOrDir(it, backupDir.resolve(it.name).toFile()) }
}

private fun startCopyingFiles(builtDistPath: String, workIdeHome: String, project: Project) {
  object : Task.Backgroundable(project, DevKitBundle.message("action.UpdateIdeFromSourcesAction.task.title"), true) {
    override fun run(indicator: ProgressIndicator) {
      indicator.text = DevKitBundle.message("action.UpdateIdeFromSourcesAction.copy.progress.text")
      indicator.text2 = DevKitBundle.message("action.UpdateIdeFromSourcesAction.copy.delete.old.files.text")
      FileUtil.delete(File(workIdeHome))
      indicator.checkCanceled()
      indicator.text2 = DevKitBundle.message("action.UpdateIdeFromSourcesAction.copy.copy.new.files.text")
      FileUtil.copyDir(File(builtDistPath), File(workIdeHome))
      indicator.checkCanceled()
      Notification("Update from Sources", DevKitBundle.message("action.UpdateIdeFromSourcesAction.notification.title"),
                   DevKitBundle.message("action.UpdateIdeFromSourcesAction.notification.content", workIdeHome),
                   NotificationType.INFORMATION).notify(project)
    }
  }.queue()
}

private fun generateUpdateCommand(builtDistPath: String, workIdeHome: String): Array<String> {
  if (SystemInfo.isWindows) {
    val restartLogFile = File(PathManager.getLogPath(), "update-from-sources.log")
    val updateScript = FileUtil.createTempFile("update", ".cmd", false)
    val workHomePath = File(workIdeHome).absolutePath
    /* deletion of the IDE files may fail to delete some executable files because they are still used by the IDE process,
       so the script waits for some time and tries to delete again;
       'ping' command is used instead of 'timeout' because the latter doesn't work from batch files;
       removal of the script file is performed in a separate process to avoid errors while executing the script */
    FileUtil.writeToFile(updateScript, """
        @echo off
        SET count=20
        SET time_to_wait=1
        :DELETE_DIR
        RMDIR /Q /S "$workHomePath"
        IF EXIST "$workHomePath" (
          IF %count% GEQ 0 (
            ECHO "$workHomePath" still exists, wait %time_to_wait%s and try delete again
            SET /A time_to_wait=%time_to_wait%+1
            PING 127.0.0.1 -n %time_to_wait% >NUL
            SET /A count=%count%-1
            ECHO %count% attempts remain
            GOTO DELETE_DIR
          )
          ECHO Failed to delete "$workHomePath", IDE wasn't updated. You may delete it manually and copy files from "${File(builtDistPath).absolutePath}" by hand  
          GOTO CLEANUP_AND_EXIT 
        )
        XCOPY "${File(builtDistPath).absolutePath}" "$workHomePath"\ /Q /E /Y
        :CLEANUP_AND_EXIT
        START /b "" cmd /c DEL /Q /F "${updateScript.absolutePath}" & EXIT /b
      """.trimIndent())
    return arrayOf("cmd", "/c", updateScript.absolutePath, ">${restartLogFile.absolutePath}", "2>&1")
  }

  val command = arrayOf(
    "rm -rf \"$workIdeHome\"/*",
    "cp -R \"$builtDistPath\"/* \"$workIdeHome\""
  )

  return arrayOf("/bin/sh", "-c", command.joinToString(" && "))
}

private fun restartWithCommand(command: Array<String>, deployDirPath: String, beforeRestart: () -> Unit) {
  val pluginsDir = Path.of(deployDirPath)
    .resolve("artifacts/${ApplicationInfo.getInstance().build.productCode}-plugins")

  val nonBundledPluginsPaths = lazy { nonBundledPluginsPaths() }
  readPluginsDir(pluginsDir).forEach { newPluginNode ->
    updateNonBundledPlugin(newPluginNode, pluginsDir) { nonBundledPluginsPaths.value[it] }
  }

  Restarter.setCopyRestarterFiles()
  beforeRestart()
  (ApplicationManagerEx.getApplicationEx() as ApplicationImpl).restart(
    ApplicationEx.FORCE_EXIT or ApplicationEx.EXIT_CONFIRMED or ApplicationEx.SAVE,
    command,
  )
}

private fun readPluginsDir(pluginsDirPath: Path): List<PluginNode> {
  val pluginsXml = pluginsDirPath.resolve("plugins.xml")
  if (!pluginsXml.isRegularFile()) {
    LOG.warn("Cannot read non-bundled plugins from $pluginsXml, they won't be updated")
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

private fun nonBundledPluginsPaths(): Map<PluginId, Path> {
  return PluginManagerCore.loadedPlugins
    .asSequence()
    .filterNot { it.isBundled }
    .associate { it.pluginId to it.pluginPath }
    .also { LOG.debug("Existing custom plugins: $it") }
}

private fun updateNonBundledPlugin(
  newDescriptor: PluginNode,
  pluginDir: Path,
  oldPluginPathProvider: (PluginId) -> Path?,
) {
  assert(!newDescriptor.isBundled)
  val oldPluginPath = oldPluginPathProvider(newDescriptor.pluginId) ?: return

  val newPluginPath = pluginDir.resolve(newDescriptor.downloadUrl)
    .also { LOG.debug("Adding update command: $oldPluginPath to $it") }

  PluginInstaller.installAfterRestart(
    newDescriptor,
    newPluginPath,
    oldPluginPath,
    false,
  )
}

private fun createScriptJavaParameters(project: Project,
                                       deployDir: String,
                                       @Suppress("SameParameterValue") distRelativePath: String,
                                       buildEnabledPluginsOnly: Boolean,
                                       bundledPluginDirsToSkip: List<String>,
                                       nonBundledPluginDirsToInclude: List<String>): JavaParameters? {
  val sdk = ProjectRootManager.getInstance(project).projectSdk
  if (sdk == null) {
    LOG.warn("Project SDK is not defined")
    return null
  }
  val params = JavaParameters()
  params.isUseClasspathJar = true
  params.setDefaultCharset(project)
  params.jdk = sdk

  params.mainClass = ULTIMATE_UPDATE_FROM_SOURCES_BUILD_TARGET
  params.programParametersList.add("--classpath")
  val buildScriptsModuleName = "intellij.idea.ultimate.build"
  val buildScriptsModule = ModuleManager.getInstance(project).findModuleByName(buildScriptsModuleName)
  if (buildScriptsModule == null) {
    LOG.warn("Build scripts module $buildScriptsModuleName is not found in the project")
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
  params.vmParametersList.add("-Dintellij.build.output.root=$deployDir")
  params.vmParametersList.add("-DdistOutputRelativePath=$distRelativePath")
  return params
}


@NonNls
private val safeToDeleteFilesInHome = setOf(
  "bin", "help", "jre", "jre64", "jbr", "lib", "license", "plugins", "redist", "MacOS", "Resources",
  "build.txt", "product-info.json", "Install-Linux-tar.txt", "Install-Windows-zip.txt", "ipr.reg"
)

@NonNls
private val safeToDeleteFilesInBin = setOf(
  "append.bat", "appletviewer.policy", "format.sh", "format.bat",
  "fsnotifier", "fsnotifier64",
  "inspect.bat", "inspect.sh",
  "restarter", "icons",
  /*
  "idea.properties",
  "idea.sh",
  "idea.bat",
  "idea.exe.vmoptions",
  "idea64.exe.vmoptions",
  "idea.vmoptions",
  "idea64.vmoptions",
  "log.xml",
*/
)

@NonNls
private val safeToDeleteExtensions = setOf("exe", "dll", "dylib", "so", "ico", "svg", "png", "py")

private const val ULTIMATE_UPDATE_FROM_SOURCES_BUILD_TARGET = "UltimateUpdateFromSourcesBuildTarget"
