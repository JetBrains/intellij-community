// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.actions.updateFromSources

import com.intellij.CommonBundle
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.ide.plugins.PluginInstaller
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.marketplace.MarketplaceRequests
import com.intellij.notification.*
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.UpdateInBackground
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.libraries.LibraryUtil
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.task.ProjectTaskManager
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.PathUtil
import com.intellij.util.Restarter
import com.intellij.util.TimeoutUtil
import com.intellij.util.io.inputStream
import com.intellij.util.io.isFile
import org.jetbrains.annotations.NonNls
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.util.PsiUtil
import java.io.File
import java.nio.file.Paths
import java.util.*

private val LOG = logger<UpdateIdeFromSourcesAction>()

private val notificationGroup by lazy {
  NotificationGroup(displayId = "Update from Sources", displayType = NotificationDisplayType.STICKY_BALLOON)
}

internal open class UpdateIdeFromSourcesAction
@JvmOverloads constructor(private val forceShowSettings: Boolean = false)
  : AnAction(if (forceShowSettings) DevKitBundle.message("action.UpdateIdeFromSourcesAction.update.show.settings.text")
             else DevKitBundle.message("action.UpdateIdeFromSourcesAction.update.text"),
             DevKitBundle.message("action.UpdateIdeFromSourcesAction.update.description"), null), DumbAware, UpdateInBackground {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    if (forceShowSettings || UpdateFromSourcesSettings.getState().showSettings) {
      val oldWorkIdePath = UpdateFromSourcesSettings.getState().actualIdePath
      val ok = UpdateFromSourcesDialog(project, forceShowSettings).showAndGet()
      if (!ok) return
      val updatedState = UpdateFromSourcesSettings.getState()
      if (oldWorkIdePath != updatedState.actualIdePath) {
        updatedState.workIdePathsHistory.remove(oldWorkIdePath)
        updatedState.workIdePathsHistory.remove(updatedState.actualIdePath)
        updatedState.workIdePathsHistory.add(0, updatedState.actualIdePath)
        updatedState.workIdePathsHistory.add(0, oldWorkIdePath)
      }
    }

    fun error(@NlsContexts.DialogMessage message : String) {
      Messages.showErrorDialog(project, message, CommonBundle.getErrorTitle())
    }

    val state = UpdateFromSourcesSettings.getState()
    val devIdeaHome = project.basePath ?: return
    val workIdeHome = state.actualIdePath
    val restartAutomatically = state.restartAutomatically
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

    val scriptFile = File(devIdeaHome, "build/scripts/idea_ultimate.gant")
    if (!scriptFile.exists()) {
      return error(DevKitBundle.message("action.UpdateIdeFromSourcesAction.error.build.scripts.not.exists", scriptFile))
    }
    if (!scriptFile.readText().contains(includeBinAndRuntimeProperty)) {
      return error(DevKitBundle.message("action.UpdateIdeFromSourcesAction.error.build.scripts.out.of.date"))
    }

    val bundledPluginDirsToSkip: List<String>
    val nonBundledPluginDirsToInclude: List<String>
    val buildEnabledPluginsOnly = !state.buildDisabledPlugins
    if (buildEnabledPluginsOnly) {
      val pluginDirectoriesToSkip = LinkedHashSet(state.pluginDirectoriesForDisabledPlugins)
      pluginDirectoriesToSkip.removeAll(PluginManagerCore.getLoadedPlugins().asSequence().filter { it.isBundled }.map { it.path }.filter { it.isDirectory }.map { it.name })
      PluginManagerCore.getPlugins().filter { it.isBundled && !it.isEnabled }.map { it.path }.filter { it.isDirectory }.mapTo(pluginDirectoriesToSkip) { it.name }
      val list = pluginDirectoriesToSkip.toMutableList()
      state.pluginDirectoriesForDisabledPlugins = list
      bundledPluginDirsToSkip = list
      nonBundledPluginDirsToInclude = PluginManagerCore.getPlugins().filter {
        !it.isBundled && it.isEnabled
      }.map { it.path }.filter { it.isDirectory }.map { it.name }
    }
    else {
      bundledPluginDirsToSkip = emptyList()
      nonBundledPluginDirsToInclude = emptyList()
    }

    val deployDir = "$devIdeaHome/out/deploy" // NON-NLS
    val distRelativePath = "dist" // NON-NLS
    val backupDir = "$devIdeaHome/out/backup-before-update-from-sources" // NON-NLS
    val params = createScriptJavaParameters(devIdeaHome, project, deployDir, distRelativePath, scriptFile,
                                            buildEnabledPluginsOnly, bundledPluginDirsToSkip, nonBundledPluginDirsToInclude) ?: return
    ProjectTaskManager.getInstance(project)
      .buildAllModules()
      .onSuccess {
        if (!it.isAborted && !it.hasErrors()) {
          runUpdateScript(params, project, workIdeHome, deployDir, distRelativePath, backupDir, restartAutomatically)
        }
      }
  }

  private fun checkIdeHome(workIdeHome: String): String? {
    val homeDir = File(workIdeHome)
    if (!homeDir.exists()) return null

    if (homeDir.isFile) return DevKitBundle.message("action.UpdateIdeFromSourcesAction.error.work.home.not.valid.ide.home.not.directory")
    val buildTxt = if (SystemInfo.isMac) "Resources/build.txt" else "build.txt" // NON-NLS
    for (name in listOf("bin", buildTxt)) {
      if (!File(homeDir, name).exists()) {
        return DevKitBundle.message("action.UpdateIdeFromSourcesAction.error.work.home.not.valid.ide.home.not.exists", name)
      }
    }
    return null
  }

  private fun runUpdateScript(params: JavaParameters,
                              project: Project,
                              workIdeHome: String,
                              deployDirPath: String,
                              distRelativePath: String,
                              backupDir: String,
                              restartAutomatically: Boolean) {
    val builtDistPath = "$deployDirPath/$distRelativePath"
    object : Task.Backgroundable(project, DevKitBundle.message("action.UpdateIdeFromSourcesAction.task.title"), true) {
      override fun run(indicator: ProgressIndicator) {
        indicator.text = DevKitBundle.message("action.UpdateIdeFromSourcesAction.update.progress.text")
        backupImportantFilesIfNeeded(workIdeHome, backupDir, indicator)
        indicator.text2 = DevKitBundle.message("action.UpdateIdeFromSourcesAction.update.progress.delete", builtDistPath)
        FileUtil.delete(File(builtDistPath))
        indicator.text2 = DevKitBundle.message("action.UpdateIdeFromSourcesAction.update.progress.start.gant.script")
        val commandLine = params.toCommandLine()
        commandLine.isRedirectErrorStream = true
        val scriptHandler = OSProcessHandler(commandLine)
        val output = Collections.synchronizedList(ArrayList<@NlsSafe String>())
        scriptHandler.addProcessListener(object : ProcessAdapter() {
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
              notificationGroup.createNotification(title = DevKitBundle.message("action.UpdateIdeFromSourcesAction.task.failed.title"),
                                                   content = DevKitBundle.message("action.UpdateIdeFromSourcesAction.task.failed.content",
                                                                                  event.exitCode),
                                                   type = NotificationType.ERROR)
                .addAction(NotificationAction.createSimple(DevKitBundle.message("action.UpdateIdeFromSourcesAction.notification.action.view.output")) {
                  FileEditorManager.getInstance(project).openFile(LightVirtualFile("output.txt", output.joinToString("")), true)
                })
                .addAction(NotificationAction.createSimple(DevKitBundle.message("action.UpdateIdeFromSourcesAction.notification.action.view.debug.log")) {
                  val logFile = LocalFileSystem.getInstance().refreshAndFindFileByPath("$deployDirPath/log/debug.log") ?: return@createSimple // NON-NLS
                  logFile.refresh(true, false)
                  FileEditorManager.getInstance(project).openFile(logFile, true)
                })
                .notify(project)
              return
            }

            if (!FileUtil.pathsEqual(workIdeHome, PathManager.getHomePath())) {
              startCopyingFiles(builtDistPath, workIdeHome, project)
              return
            }

            val command = generateUpdateCommand(builtDistPath, workIdeHome)
            if (restartAutomatically) {
              ApplicationManager.getApplication().invokeLater { scheduleRestart(command, deployDirPath, project) }
            }
            else {
              showRestartNotification(command, deployDirPath, project)
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

  private fun showRestartNotification(command: Array<String>, deployDirPath: String, project: Project) {
    notificationGroup
      .createNotification(DevKitBundle.message("action.UpdateIdeFromSourcesAction.task.success.title"), DevKitBundle.message("action.UpdateIdeFromSourcesAction.task.success.content"), NotificationType.INFORMATION)
      .setListener(NotificationListener { _, _ -> restartWithCommand(command, deployDirPath) })
      .notify(project)
  }

  private fun scheduleRestart(command: Array<String>, deployDirPath: String, project: Project) {
    object : Task.Modal(project, DevKitBundle.message("action.UpdateIdeFromSourcesAction.task.success.title"), true) {
      override fun run(indicator: ProgressIndicator) {
        indicator.isIndeterminate = false
        var progress = 0
        for (i in 10 downTo 1) {
          indicator.text = DevKitBundle.message(
            "action.UpdateIdeFromSourcesAction.progress.text.new.installation.prepared.ide.will.restart", i)
          repeat(10) {
            indicator.fraction = 0.01 * progress++
            indicator.checkCanceled()
            TimeoutUtil.sleep(100)
          }
        }
        restartWithCommand(command, deployDirPath)
      }

      override fun onCancel() {
        showRestartNotification(command, deployDirPath, project)
      }
    }.setCancelText(DevKitBundle.message("action.UpdateIdeFromSourcesAction.button.postpone")).queue()
  }

  private fun backupImportantFilesIfNeeded(workIdeHome: String,
                                           backupDirPath: String,
                                           indicator: ProgressIndicator) {
    val backupDir = File(backupDirPath)
    if (backupDir.exists()) {
      LOG.debug("$backupDir already exists, skipping backup")
      return
    }

    LOG.debug("Backing up files from $workIdeHome to $backupDir")
    indicator.text2 = DevKitBundle.message("action.UpdateIdeFromSourcesAction.backup.progress.text")
    FileUtil.createDirectory(backupDir)
    File(workIdeHome, "bin").listFiles()
      ?.filter { it.name !in safeToDeleteFilesInBin && it.extension !in safeToDeleteExtensions }
      ?.forEach { FileUtil.copy(it, File(backupDir, "bin/${it.name}")) }

    File(workIdeHome).listFiles()
      ?.filter { it.name !in safeToDeleteFilesInHome }
      ?.forEach { FileUtil.copyFileOrDir(it, File(backupDir, it.name)) }
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

  @Suppress("HardCodedStringLiteral")
  private fun generateUpdateCommand(builtDistPath: String, workIdeHome: String): Array<String> {
    if (SystemInfo.isWindows) {
      val restartLogFile = File(PathManager.getLogPath(), "update-from-sources.log")
      val updateScript = FileUtil.createTempFile("update", ".cmd", false)
      val workHomePath = File(workIdeHome).absolutePath
      /* deletion of the IDE files may fail to delete some executable files because they are still used by the IDE process,
         so the script wait for some time and try to delete again;
         'ping' command is used instead of 'timeout' because the latter doesn't work from batch files;
         removal of the script file is performed in separate process to avoid errors while executing the script */
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

  private fun restartWithCommand(command: Array<String>, deployDirPath: String) {
    updatePlugins(deployDirPath)
    Restarter.doNotLockInstallFolderOnRestart()
    (ApplicationManager.getApplication() as ApplicationImpl).restart(ApplicationEx.FORCE_EXIT or ApplicationEx.EXIT_CONFIRMED or ApplicationEx.SAVE, command)
  }

  private fun updatePlugins(deployDirPath: String) {
    val pluginsDir = Paths.get(deployDirPath).resolve("artifacts/${ApplicationInfo.getInstance().build.productCode}-plugins")
    val pluginsXml = pluginsDir.resolve("plugins.xml")
    if (!pluginsXml.isFile()) {
      LOG.warn("Cannot read non-bundled plugins from $pluginsXml, they won't be updated")
      return
    }
    val plugins = try {
      pluginsXml.inputStream().use {
        MarketplaceRequests.parsePluginList(it)
      }
    }
    catch (e: Exception) {
      LOG.error("Failed to parse $pluginsXml", e)
      return
    }
    val existingCustomPlugins =
      PluginManagerCore.getLoadedPlugins().asSequence().filter { !it.isBundled }.associateBy { it.pluginId.idString }
    LOG.debug("Existing custom plugins: $existingCustomPlugins")
    val pluginsToUpdate =
      plugins.mapNotNull { node -> existingCustomPlugins[node.pluginId.idString]?.let { it to node } }
    for ((existing, update) in pluginsToUpdate) {
      val pluginFile = pluginsDir.resolve(update.downloadUrl)
      LOG.debug("Adding update command: ${existing.pluginPath} to $pluginFile")
      PluginInstaller.installAfterRestart(pluginFile, false, existing.pluginPath, update)
    }
  }

  private fun createScriptJavaParameters(devIdeaHome: String,
                                         project: Project,
                                         deployDir: String,
                                         @Suppress("SameParameterValue") distRelativePath: String,
                                         scriptFile: File,
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

    params.mainClass = "org.codehaus.groovy.tools.GroovyStarter"
    params.programParametersList.add("--classpath")
    val buildScriptsModuleName = "intellij.idea.ultimate.build"
    val buildScriptsModule = ModuleManager.getInstance(project).findModuleByName(buildScriptsModuleName)
    if (buildScriptsModule == null) {
      LOG.warn("Build scripts module $buildScriptsModuleName is not found in the project")
      return null
    }
    val classpath = OrderEnumerator.orderEntries(buildScriptsModule)
      .recursively().withoutSdk().runtimeOnly().productionOnly().classes().pathsList

    val classesFromCoreJars = listOf(
      params.mainClass,
      "org.apache.tools.ant.BuildException",   //ant
      "org.apache.tools.ant.launch.AntMain",   //ant-launcher
      "org.apache.commons.cli.ParseException", //commons-cli
      "groovy.util.CliBuilder",                //groovy-cli-commons
      "org.codehaus.groovy.runtime.NioGroovyMethods", //groovy-nio
    )
    val coreClassPath = classpath.rootDirs.filter { root ->
      classesFromCoreJars.any { LibraryUtil.isClassAvailableInLibrary(listOf(root), it) }
    }.mapNotNull { PathUtil.getLocalPath(it) }
    params.classPath.addAll(coreClassPath)
    coreClassPath.forEach { classpath.remove(FileUtil.toSystemDependentName(it)) }

    params.programParametersList.add(classpath.pathsString)
    params.programParametersList.add("--main")
    params.programParametersList.add("gant.Gant")
    params.programParametersList.add("--debug")
    params.programParametersList.add("-Dsome_unique_string_42_239")
    params.programParametersList.add("--file")
    params.programParametersList.add(scriptFile.absolutePath)
    params.programParametersList.add("update-from-sources")
    params.vmParametersList.add("-D$includeBinAndRuntimeProperty=true")
    params.vmParametersList.add("-Dintellij.build.bundled.jre.prefix=jbrsdk-")

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

  override fun update(e: AnActionEvent) {
    val project = e.project
    e.presentation.isEnabledAndVisible = project != null && PsiUtil.isIdeaProject(project)
  }
}

private const val includeBinAndRuntimeProperty = "intellij.build.generate.bin.and.runtime.for.unpacked.dist"

internal class UpdateIdeFromSourcesSettingsAction : UpdateIdeFromSourcesAction(true)

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
  "restarter"
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
