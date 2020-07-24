// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.actions.updateFromSources

import com.intellij.CommonBundle
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.notification.*
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.libraries.LibraryUtil
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.systemIndependentPath
import com.intellij.task.ProjectTaskManager
import com.intellij.util.PathUtil
import com.intellij.util.Restarter
import com.intellij.util.SystemProperties
import org.jetbrains.idea.devkit.util.PsiUtil
import java.io.File
import java.util.*
import kotlin.collections.LinkedHashSet

private val LOG = logger<UpdateIdeFromSourcesAction>()

private val notificationGroup by lazy {
  NotificationGroup(displayId = "Update from Sources", displayType = NotificationDisplayType.STICKY_BALLOON)
}

internal open class UpdateIdeFromSourcesAction
 @JvmOverloads constructor(private val forceShowSettings: Boolean = false)
  : AnAction(if (forceShowSettings) "Update IDE from Sources Settings..." else "Update IDE from Sources...",
             "Builds an installation of IntelliJ IDEA from the currently opened sources and replace the current installation by it.", null), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    if (forceShowSettings || UpdateFromSourcesSettings.getState().showSettings) {
      val ok = UpdateFromSourcesDialog(project, forceShowSettings).showAndGet()
      if (!ok) return
    }

    fun error(message: String) {
      Messages.showErrorDialog(project, message, CommonBundle.getErrorTitle())
    }

    val state = UpdateFromSourcesSettings.getState()
    val devIdeaHome = project.basePath ?: return
    val workIdeHome = state.workIdePath ?: PathManager.getHomePath()
    if (!ApplicationManager.getApplication().isRestartCapable && FileUtil.pathsEqual(workIdeHome, PathManager.getHomePath())) {
      return error("This IDE cannot restart itself so updating from sources isn't supported")
    }

    val notIdeHomeMessage = checkIdeHome(workIdeHome)
    if (notIdeHomeMessage != null) {
      return error("$workIdeHome is not a valid IDE home: $notIdeHomeMessage")
    }

    val scriptFile = File(devIdeaHome, "build/scripts/idea_ultimate.gant")
    if (!scriptFile.exists()) {
      return error("$scriptFile doesn't exist")
    }
    if (!scriptFile.readText().contains(includeBinAndRuntimeProperty)) {
      return error("The build scripts is out-of-date, please update to the latest 'master' sources.")
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
        !it.isBundled && it.isEnabled && it.version != null && it.version.contains("SNAPSHOT")
      }.map { it.path }.filter { it.isDirectory }.map { it.name }
    }
    else {
      bundledPluginDirsToSkip = emptyList()
      nonBundledPluginDirsToInclude = emptyList()
    }

    val deployDir = "$devIdeaHome/out/deploy"
    val distRelativePath = "dist"
    val backupDir = "$devIdeaHome/out/backup-before-update-from-sources"
    val params = createScriptJavaParameters(devIdeaHome, project, deployDir, distRelativePath, scriptFile,
                                            buildEnabledPluginsOnly, bundledPluginDirsToSkip, nonBundledPluginDirsToInclude) ?: return
    ProjectTaskManager.getInstance(project)
      .buildAllModules()
      .onSuccess {
        if (!it.isAborted && !it.hasErrors()) {
          runUpdateScript(params, project, workIdeHome, "$deployDir/$distRelativePath", backupDir)
        }
      }
  }

  private fun checkIdeHome(workIdeHome: String): String? {
    val homeDir = File(workIdeHome)
    if (!homeDir.exists()) return null

    if (homeDir.isFile) return "it is not a directory"
    val buildTxt = if (SystemInfo.isMac) "Resources/build.txt" else "build.txt"
    for (name in listOf("bin", buildTxt)) {
      if (!File(homeDir, name).exists()) {
        return "'$name' doesn't exist"
      }
    }
    return null
  }

  private fun runUpdateScript(params: JavaParameters,
                              project: Project,
                              workIdeHome: String,
                              builtDistPath: String,
                              backupDir: String) {
    object : Task.Backgroundable(project, "Updating from Sources", true) {
      override fun run(indicator: ProgressIndicator) {
        indicator.text = "Updating IDE from sources..."
        backupImportantFilesIfNeeded(workIdeHome, backupDir, indicator)
        indicator.text2 = "Deleting $builtDistPath"
        FileUtil.delete(File(builtDistPath))
        indicator.text2 = "Starting gant script"
        val scriptHandler = params.createOSProcessHandler()
        val errorLines = Collections.synchronizedList(ArrayList<String>())
        scriptHandler.addProcessListener(object : ProcessAdapter() {
          override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
            LOG.debug("script: ${event.text}")
            if (outputType == ProcessOutputTypes.STDERR) {
              errorLines.add(event.text)
            }
            else if (outputType == ProcessOutputTypes.STDOUT) {
              indicator.text2 = event.text
            }
          }

          override fun processTerminated(event: ProcessEvent) {
            if (indicator.isCanceled) {
              return
            }

            if (event.exitCode != 0) {
              val errorText = errorLines.joinToString("\n")
              notificationGroup.createNotification(title = "Update from Sources Failed",
                                                   content = "Build script finished with ${event.exitCode}: $errorText",
                                                   type = NotificationType.ERROR).notify(project)
              return
            }

            if (!FileUtil.pathsEqual(workIdeHome, PathManager.getHomePath())) {
              startCopyingFiles(builtDistPath, workIdeHome, project)
              return
            }

            val command = generateUpdateCommand(builtDistPath, workIdeHome)
            if (indicator.isShowing) {
              restartWithCommand(command)
            }
            else {
              notificationGroup.createNotification(title = "Update from Sources",
                                                   content = "New installation is prepared from sources. <a href=\"#\">Restart</a>?",
                                                   listener = NotificationListener { _, _ -> restartWithCommand(command) }).notify(project)
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

  private fun backupImportantFilesIfNeeded(workIdeHome: String,
                                           backupDirPath: String,
                                           indicator: ProgressIndicator) {
    val backupDir = File(backupDirPath)
    if (backupDir.exists()) {
      LOG.debug("$backupDir already exists, skipping backup")
      return
    }

    LOG.debug("Backing up files from $workIdeHome to $backupDir")
    indicator.text2 = "Backing up files"
    FileUtil.createDirectory(backupDir)
    File(workIdeHome, "bin").listFiles()
      ?.filter { it.name !in safeToDeleteFilesInBin && it.extension !in safeToDeleteExtensions }
      ?.forEach { FileUtil.copy(it, File(backupDir, "bin/${it.name}")) }

    File(workIdeHome).listFiles()
      ?.filter { it.name !in safeToDeleteFilesInHome }
      ?.forEach { FileUtil.copyFileOrDir(it, File(backupDir, it.name)) }
  }

  private fun startCopyingFiles(builtDistPath: String, workIdeHome: String, project: Project) {
    object : Task.Backgroundable(project, "Updating from Sources", true) {
      override fun run(indicator: ProgressIndicator) {
        indicator.text = "Copying files to IDE distribution..."
        indicator.text2 = "Deleting old files"
        FileUtil.delete(File(workIdeHome))
        indicator.checkCanceled()
        indicator.text2 = "Copying new files"
        FileUtil.copyDir(File(builtDistPath), File(workIdeHome))
        indicator.checkCanceled()
        Notification("Update from Sources", "Update from Sources", "New installation is prepared at $workIdeHome.",
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

  private fun restartWithCommand(command: Array<String>) {
    Restarter.doNotLockInstallFolderOnRestart()
    (ApplicationManager.getApplication() as ApplicationImpl).restart(ApplicationEx.FORCE_EXIT or ApplicationEx.EXIT_CONFIRMED or ApplicationEx.SAVE, command)
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
      "groovy.util.CliBuilder"                 //groovy-cli-commons
    )
    val coreClassPath = classpath.rootDirs.filter { root ->
      classesFromCoreJars.any { LibraryUtil.isClassAvailableInLibrary(listOf(root), it) }
    }.mapNotNull { PathUtil.getLocalPath(it) }
    params.classPath.addAll(coreClassPath)
    coreClassPath.forEach { classpath.remove(FileUtil.toSystemDependentName(it)) }

    params.programParametersList.add(classpath.pathsString)
    params.programParametersList.add("--main")
    params.programParametersList.add("gant.Gant")
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
    e.presentation.isEnabledAndVisible = project != null && isIdeaProject(project)
  }

  private fun isIdeaProject(project: Project) = try {
    DumbService.getInstance(project).computeWithAlternativeResolveEnabled<Boolean, RuntimeException> { PsiUtil.isIdeaProject(project) }
  }
  catch (e: IndexNotReadyException) {
    false
  }
}

private const val includeBinAndRuntimeProperty = "intellij.build.generate.bin.and.runtime.for.unpacked.dist"

internal class UpdateIdeFromSourcesSettingsAction : UpdateIdeFromSourcesAction(true)

private val safeToDeleteFilesInHome = setOf(
  "bin", "help", "jre", "jre64", "jbr", "lib", "license", "plugins", "redist", "MacOS", "Resources",
  "build.txt", "product-info.json", "Install-Linux-tar.txt", "Install-Windows-zip.txt", "ipr.reg"
)

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

private val safeToDeleteExtensions = setOf("exe", "dll", "dylib", "so", "ico", "svg", "png", "py")