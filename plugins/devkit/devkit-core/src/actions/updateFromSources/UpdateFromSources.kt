// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(ExperimentalPathApi::class, LowLevelLocalMachineAccess::class)
package org.jetbrains.idea.devkit.actions.updateFromSources

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.OSProcessUtil
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputType
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
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.restartOrNotify
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts.NotificationContent
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.Restarter
import com.intellij.util.execution.ParametersListUtil
import com.intellij.util.system.LowLevelLocalMachineAccess
import com.intellij.util.system.OS
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.devkit.DevKitBundle
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.deleteRecursively
import kotlin.io.path.inputStream
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.notExists
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.io.path.writeText

private val LOG = Logger.getInstance("org.jetbrains.idea.devkit.actions.updateFromSources.UpdateFromSourcesKt")

private const val BAZEL_REGISTRY_KEY = "devkit.update.from.sources.bazel"

/**
 * When `true`, the action installs the distribution that Bazel builds through `build/dist.cmd update-ide`,
 * and not the output of the build scripts.
 */
internal fun isBazelUpdateEnabled(): Boolean = RegistryManager.getInstance().`is`(BAZEL_REGISTRY_KEY)

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

  if (isBazelUpdateEnabled()) {
    return runBazelUpdate(project, devIdeaHome, workIdeHome, state.buildDisabledPlugins, restartAutomatically, beforeRestart)
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
      .filter { it.isBundled && PluginManagerCore.isDisabled(it.pluginId) }
      .map { it.pluginPath }
      .filter { it.isDirectory() }
      .mapTo(pluginDirectoriesToSkip) { it.name }
    val list = pluginDirectoriesToSkip.toMutableList()
    state.pluginDirectoriesForDisabledPlugins = list
    bundledPluginDirsToSkip = list
    nonBundledPluginDirsToInclude = enabledNonBundledPluginDirs()
  }
  else {
    bundledPluginDirsToSkip = emptyList()
    nonBundledPluginDirsToInclude = emptyList()
  }

  val deployDir = devIdeaHome.resolve("out/deploy")
  val builtDistDir = deployDir.resolve("dist")
  val commandLine = createUpdateFromSourcesCommandLine(
    devIdeaHome, deployDir, builtDistDir, buildEnabledPluginsOnly, bundledPluginDirsToSkip, nonBundledPluginDirsToInclude,
    state.additionalVmOptionsForBuildScripts
  )
  runUpdateScript(
    commandLine = commandLine,
    project = project,
    beforeRun = { indicator ->
      indicator.text2 = DevKitBundle.message("action.UpdateIdeFromSourcesAction.update.progress.delete", builtDistDir)
      builtDistDir.deleteRecursively()
    },
    failureLogFile = deployDir.resolve("log/debug.log"),
    failureLogActionText = DevKitBundle.message("action.UpdateIdeFromSourcesAction.notification.action.view.debug.log"),
    stopProcessOnCancel = false,
  ) {
    if (!builtDistDir.isDirectory() || builtDistDir.listDirectoryEntries().isEmpty()) {
      showError(project, DevKitBundle.message("action.UpdateIdeFromSourcesAction.error.empty.dir", builtDistDir))
      return@runUpdateScript
    }

    if (workIdeHome != Path.of(PathManager.getHomePath())) {
      startCopyingFiles(builtDistDir, workIdeHome, project)
    }
    else {
      val command = generateUpdateCommand(builtDistDir, workIdeHome)
      restartOrNotify(project, restartAutomatically) {
        restartWithCommand(command, deployDir.resolve("artifacts/${ApplicationInfo.getInstance().build.productCode}-plugins"), beforeRestart)
      }
    }
  }
}

private fun enabledNonBundledPluginDirs(): List<String> {
  return PluginManagerCore.plugins
    .asSequence()
    .filterNot { it.isBundled || PluginManagerCore.isDisabled(it.pluginId) }
    .map { it.pluginPath }
    .filter { it.isDirectory() }
    .map { it.name }
    .toList()
}

/**
 * Builds and stages the Bazel distribution, so the build never changes [workIdeHome].
 * For the running home, the restart applies the staged home. For another work home, a short second process applies it.
 */
private fun runBazelUpdate(
  project: Project,
  devIdeaHome: Path,
  workIdeHome: Path,
  buildDisabledPlugins: Boolean,
  restartAutomatically: Boolean,
  beforeRestart: () -> Unit,
) {
  val outDir = devIdeaHome.resolve("out/update-from-sources")
  val logFile = outDir.resolve("update.log")
  val resultFile = outDir.resolve("result.json")
  val commandLine = createBazelUpdateCommandLine(
    devIdeaHome = devIdeaHome,
    workIdeHome = workIdeHome,
    pluginDirs = if (buildDisabledPlugins) emptyList() else enabledNonBundledPluginDirs(),
    allPlugins = buildDisabledPlugins,
    resultFile = resultFile,
    logFile = logFile,
  )
  runUpdateScript(
    commandLine = commandLine,
    project = project,
    beforeRun = {
      outDir.createDirectories()
      logFile.deleteIfExists()
      resultFile.deleteIfExists()
    },
    failureLogFile = logFile,
    failureLogActionText = DevKitBundle.message("action.UpdateIdeFromSourcesAction.notification.action.view.log"),
    stopProcessOnCancel = true,
  ) {
    val result = try {
      parseBazelUpdateResult(resultFile.readText())
    }
    catch (e: Exception) {
      rethrowControlFlowException(e)
      LOG.warn("Cannot read $resultFile", e)
      val reason = e.message?.lineSequence()?.firstOrNull() ?: e.javaClass.name
      showError(project, DevKitBundle.message("action.UpdateIdeFromSourcesAction.error.dev.dist.result", resultFile, reason))
      return@runUpdateScript
    }

    if (result.skippedPlugins.isNotEmpty()) {
      LOG.warn("The Bazel distribution has no build of these plugins: ${result.skippedPlugins}")
      Notification(
        "Update from Sources",
        DevKitBundle.message("action.UpdateIdeFromSourcesAction.notification.title"),
        DevKitBundle.message("action.UpdateIdeFromSourcesAction.notification.skipped.plugins", result.skippedPlugins.joinToString(", ")),
        NotificationType.WARNING
      ).notify(project)
    }

    if (workIdeHome != Path.of(PathManager.getHomePath())) {
      applyStagedHome(result.apply, workIdeHome, logFile, project)
    }
    else {
      val command = result.apply.toTypedArray()
      val pluginsDir = result.repository?.let { Path.of(it).parent }
      restartOrNotify(project, restartAutomatically && result.skippedPlugins.isEmpty()) {
        restartWithCommand(command, pluginsDir, beforeRestart)
      }
    }
  }
}

/**
 * Runs the [apply] command of the result for a work home that does not run.
 * The task cannot be cancelled, because the command renames the directories and deletes the old home.
 */
private fun applyStagedHome(apply: List<String>, workIdeHome: Path, logFile: Path, project: Project) {
  @Suppress("UsagesOfObsoleteApi")
  object : Task.Backgroundable(project, DevKitBundle.message("action.UpdateIdeFromSourcesAction.task.title"), false) {
    override fun run(indicator: ProgressIndicator) {
      indicator.text = DevKitBundle.message("action.UpdateIdeFromSourcesAction.apply.progress.text")
      val commandLine = GeneralCommandLine(apply).withRedirectErrorStream(true)
      LOG.info("Applying the staged home: ${commandLine.commandLineString}")
      val output = try {
        CapturingProcessHandler(commandLine).runProcess()
      }
      catch (e: ExecutionException) {
        rethrowControlFlowException(e)
        LOG.warn("Cannot apply the staged home", e)
        showError(project, DevKitBundle.message("action.UpdateIdeFromSourcesAction.apply.failed.start", e.message ?: e.javaClass.name))
        return
      }
      if (output.exitCode != 0) {
        val actions = failureActions(
          project = project,
          output = output.stdout,
          logFile = logFile,
          logActionText = DevKitBundle.message("action.UpdateIdeFromSourcesAction.notification.action.view.log"),
        )
        showError(project, DevKitBundle.message("action.UpdateIdeFromSourcesAction.apply.failed.content", output.exitCode), *actions.toTypedArray())
        return
      }

      Notification(
        "Update from Sources",
        DevKitBundle.message("action.UpdateIdeFromSourcesAction.notification.title"),
        DevKitBundle.message("action.UpdateIdeFromSourcesAction.notification.content", workIdeHome),
        NotificationType.INFORMATION
      ).notify(project)
    }
  }.queue()
}

private fun failureActions(project: Project, output: @NlsSafe String, logFile: Path, logActionText: @NotificationContent String): List<NotificationAction> {
  val actions = mutableListOf(
    NotificationAction.createSimple(DevKitBundle.message("action.UpdateIdeFromSourcesAction.notification.action.view.output")) {
      FileEditorManager.getInstance(project).openFile(LightVirtualFile("output.txt", output), true)
    },
  )
  VirtualFileManager.getInstance().refreshAndFindFileByNioPath(logFile)?.let { file ->
    file.refresh(true, false)
    actions += NotificationAction.createSimple(logActionText) {
      FileEditorManager.getInstance(project).openFile(file, true)
    }
  }
  return actions
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
  for (name in listOf("bin", if (OS.CURRENT == OS.macOS) "Resources/build.txt" else "build.txt")) {
    if (workIdeHome.resolve(name).notExists()) {
      return DevKitBundle.message("action.UpdateIdeFromSourcesAction.error.work.home.not.valid.ide.home.not.exists", name)
    }
  }
  return null
}

/**
 * Runs [commandLine] in a background task and calls [onSuccess] when the process exits with code 0.
 * On another exit code, the error notification opens the output and [failureLogFile] when it exists.
 * With [stopProcessOnCancel], a cancel of the task kills the process tree, because the command starts nested processes.
 */
private fun runUpdateScript(
  commandLine: GeneralCommandLine,
  project: Project,
  beforeRun: (ProgressIndicator) -> Unit,
  failureLogFile: Path,
  failureLogActionText: @NotificationContent String,
  stopProcessOnCancel: Boolean,
  onSuccess: () -> Unit,
) {
  @Suppress("UsagesOfObsoleteApi")
  object : Task.Backgroundable(project, DevKitBundle.message("action.UpdateIdeFromSourcesAction.task.title"), true) {
    override fun run(indicator: ProgressIndicator) {
      indicator.text = DevKitBundle.message("action.UpdateIdeFromSourcesAction.update.progress.text")

      beforeRun(indicator)

      indicator.text2 = DevKitBundle.message("action.UpdateIdeFromSourcesAction.update.progress.start.script")
      commandLine.withRedirectErrorStream(true)
      LOG.info("Running update from sources from '${commandLine.workingDirectory}': ${commandLine.commandLineString}")
      val scriptHandler = OSProcessHandler(commandLine)
      val output = Collections.synchronizedList(ArrayList<@NlsSafe String>())
      scriptHandler.addProcessListener(object : ProcessListener {
        override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
          output.add(event.text)
          if (ProcessOutputType.isStdout(outputType)) {
            indicator.text2 = event.text
          }
        }

        override fun processTerminated(event: ProcessEvent) {
          if (indicator.isCanceled) {
            return
          }

          if (event.exitCode != 0) {
            val actions = failureActions(project, output.joinToString(""), failureLogFile, failureLogActionText)
            showError(project, DevKitBundle.message("action.UpdateIdeFromSourcesAction.task.failed.content", event.exitCode), *actions.toTypedArray())
            return
          }

          onSuccess()
        }
      })

      scriptHandler.startNotify()
      while (!scriptHandler.isProcessTerminated) {
        scriptHandler.waitFor(300)
        if (stopProcessOnCancel && indicator.isCanceled) {
          OSProcessUtil.killProcessTree(scriptHandler.process)
        }
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
  if (OS.CURRENT == OS.Windows) {
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

  // on macOS, the IDE home is the `Contents` directory of the app bundle; the copies stay next to the bundle, so its root holds only `Contents`
  val stagingBase = if (OS.CURRENT == OS.macOS) workIdeHome.parent else workIdeHome
  if (!Files.isWritable(stagingBase.parent) || !Files.isWritable(workIdeHome.parent)) {
    return arrayOf("/bin/sh", "-c", """rm -rf "${workIdeHome}"/* && cp -R "${builtDistDir}"/* "${workIdeHome}"""")
  }

  // the new build is copied next to the IDE home and replaces it with two renames, so the IDE home stays complete until the copy succeeds
  val newHome = stagingBase.resolveSibling("${stagingBase.name}.new")
  val oldHome = stagingBase.resolveSibling("${stagingBase.name}.old")
  val copy = if (OS.CURRENT == OS.macOS) "cp -cR" else "cp -R"
  return arrayOf("/bin/sh", "-c",
                 """rm -rf "${newHome}" "${oldHome}" && if ${copy} "${builtDistDir}" "${newHome}"; then """ +
                 """if mv "${workIdeHome}" "${oldHome}"; then if mv "${newHome}" "${workIdeHome}"; then rm -rf "${oldHome}"; """ +
                 """else mv "${oldHome}" "${workIdeHome}"; rm -rf "${newHome}"; exit 1; fi; else rm -rf "${newHome}"; exit 1; fi; """ +
                 """else rm -rf "${newHome}"; exit 1; fi""")
}

/**
 * Installs the new builds of the installed non-bundled plugins from the `plugins.xml` in [pluginsDir], and then restarts with [command].
 * A `null` [pluginsDir] means that no plugin repository exists.
 */
private fun restartWithCommand(command: Array<String>, pluginsDir: Path?, beforeRestart: () -> Unit) {
  if (pluginsDir != null) {
    val nonBundledPluginsPaths by lazy { nonBundledPluginsPaths() }
    readPluginsDir(pluginsDir).forEach { newPluginNode ->
      updateNonBundledPlugin(newPluginNode, pluginsDir) { nonBundledPluginsPaths[it] }
    }
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
    }.map { it.getDescriptor() as PluginNode }
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

private fun createUpdateFromSourcesCommandLine(
  devIdeaHome: Path,
  deployDir: Path,
  builtDistPath: Path,
  buildEnabledPluginsOnly: Boolean,
  bundledPluginDirsToSkip: List<String>,
  nonBundledPluginDirsToInclude: List<String>,
  additionalVmOptionsForBuildScripts: String?,
): GeneralCommandLine {
  val vmProperties = mutableListOf<String>()
  vmProperties.add("-Dintellij.build.bundled.jre.prefix=jbrsdk_jcef-")

  if (buildEnabledPluginsOnly) {
    if (bundledPluginDirsToSkip.isNotEmpty()) {
      vmProperties.add("-Dintellij.build.bundled.plugin.dirs.to.skip=${bundledPluginDirsToSkip.joinToString(",")}")
    }
    val nonBundled = if (nonBundledPluginDirsToInclude.isNotEmpty()) nonBundledPluginDirsToInclude.joinToString(",") else "none"
    vmProperties.add("-Dintellij.build.non.bundled.plugin.dirs.to.include=$nonBundled")
  }

  if (!buildEnabledPluginsOnly || nonBundledPluginDirsToInclude.isNotEmpty()) {
    vmProperties.add("-Dintellij.build.local.plugins.repository=true")
  }
  vmProperties.add("-Dintellij.build.output.root=${deployDir.invariantSeparatorsPathString}")
  vmProperties.add("-DdistOutputRelativePath=${deployDir.relativize(builtDistPath).invariantSeparatorsPathString}")
  // the Rider backend binaries take gigabytes and are usually not needed in a locally updated IDEA installation (IJPL-201660)
  vmProperties.add("-Dintellij.clion.radler.skip.packaging=true")
  // must be added last, so the user-provided options override the defaults above
  vmProperties.addAll(ParametersListUtil.parse(additionalVmOptionsForBuildScripts ?: ""))

  val commandLine = GeneralCommandLine()
  commandLine.withExePath(bazelExecutable(devIdeaHome).pathString)
  commandLine.withWorkingDirectory(devIdeaHome)
  commandLine.addParameter("run")
  for (property in vmProperties) {
    commandLine.addParameter("--jvmopt=$property")
  }
  commandLine.addParameter("//build:update_from_sources")
  return commandLine
}

/**
 * The command that builds the Bazel distribution and stages it next to [workIdeHome].
 * The command writes a [BazelUpdateResult] to [resultFile], and the `apply` command of the result replaces the home.
 * [allPlugins] builds every plugin, so [pluginDirs] must be empty with it.
 */
internal fun createBazelUpdateCommandLine(
  devIdeaHome: Path,
  workIdeHome: Path,
  pluginDirs: List<String>,
  allPlugins: Boolean,
  resultFile: Path,
  logFile: Path,
): GeneralCommandLine {
  require(!allPlugins || pluginDirs.isEmpty()) { "allPlugins and pluginDirs exclude each other" }
  val script = devIdeaHome.resolve("build/dist.cmd")
  require(script.isRegularFile()) {
    "Distribution script not found at ${script.toAbsolutePath()}"
  }

  val commandLine = GeneralCommandLine()
  commandLine.withExePath(script.pathString)
  commandLine.withWorkingDirectory(devIdeaHome)
  commandLine.addParameter("update-ide")
  commandLine.addParameter("--home=${toolPath(workIdeHome)}")
  for (pluginDir in pluginDirs) {
    commandLine.addParameter("--plugin=$pluginDir")
  }
  if (allPlugins) {
    commandLine.addParameter("--all-plugins")
  }
  commandLine.addParameter("--stage-only")
  commandLine.addParameter("--result=${toolPath(resultFile)}")
  commandLine.addParameter("--log=${toolPath(logFile)}")
  return commandLine
}

/** The tool refuses a relative path and a path with `.` or `..`. */
private fun toolPath(path: Path): String = path.toAbsolutePath().normalize().pathString

/**
 * The result file of `dist.cmd update-ide --stage-only`.
 *
 * @property apply the command that replaces the home with the staged home
 * @property repository the absolute path of the `plugins.xml` file of the local plugin repository, or `null` if no plugin is built.
 *   The zips are relative to the directory of this file, and [restartWithCommand] reads `plugins.xml` from that directory.
 * @property skippedPlugins the plugin directories that the Bazel distribution does not build
 */
@Serializable
internal data class BazelUpdateResult(
  val version: Int,
  val apply: List<String>,
  val repository: String? = null,
  val skippedPlugins: List<String> = emptyList(),
  val fingerprint: String? = null,
)

private val bazelResultJson = Json { ignoreUnknownKeys = true }

internal fun parseBazelUpdateResult(text: String): BazelUpdateResult {
  val result = bazelResultJson.decodeFromString<BazelUpdateResult>(text)
  require(result.version == 1) { "Unsupported result version ${result.version}" }
  require(result.apply.isNotEmpty()) { "The result has no apply command" }
  return result
}

private fun bazelExecutable(devIdeaHome: Path): Path {
  val wrapper = devIdeaHome.resolve("bazel.cmd")
  require(wrapper.isRegularFile()) {
    "Bazel wrapper script not found at ${wrapper.toAbsolutePath()}"
  }
  return wrapper
}
