// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.warmup.util

import com.intellij.conversion.ConversionListener
import com.intellij.conversion.ConversionService
import com.intellij.ide.CommandLineInspectionProjectConfigurator
import com.intellij.ide.impl.*
import com.intellij.ide.warmup.WarmupConfigurator
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.progress.durationStep
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.JdkOrderEntry
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.asSafely
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexImpl
import com.intellij.warmup.impl.WarmupConfiguratorOfCLIConfigurator
import kotlinx.coroutines.*
import java.nio.file.Path
import java.util.*

fun importOrOpenProject(args: OpenProjectArgs, indicator: ProgressIndicator): Project {
  WarmupLogger.logInfo("Opening project from ${args.projectDir}...")
  // most of the sensible operations would run in the same thread
  return runUnderModalProgressIfIsEdt {
    runTaskAndLogTime("open project") {
      importOrOpenProjectImpl(args)
    }
  }
}

suspend fun importOrOpenProjectAsync(args: OpenProjectArgs, indicator: ProgressIndicator): Project {
  WarmupLogger.logInfo("Opening project from ${args.projectDir}...")
  // most of the sensible operations would run in the same thread
  return runTaskAndLogTime("open project") {
    importOrOpenProjectImpl(args)
  }
}

private suspend fun importOrOpenProjectImpl(args: OpenProjectArgs): Project {
  val vfsProject = blockingContext {
    VirtualFileManager.getInstance().refreshAndFindFileByNioPath(args.projectDir)
    ?: throw RuntimeException("Project path ${args.projectDir} is not found")
  }

  runTaskAndLogTime("refresh VFS") {
    WarmupLogger.logInfo("Refreshing VFS ${args.projectDir}...")
    blockingContext {
      VfsUtil.markDirtyAndRefresh(false, true, true, args.projectDir.toFile())
    }
  }
  yieldThroughInvokeLater()

  callProjectConversion(args)

  callProjectConfigurators(args) {
    this.prepareEnvironment(args.projectDir)
  }

  val project = runTaskAndLogTime("open project") {
    ProjectUtil.openOrImportAsync(vfsProject.toNioPath(), OpenProjectTask())
  } ?: throw RuntimeException("Failed to open project, null is returned")
  yieldThroughInvokeLater()

  runTaskAndLogTime("patching project") {
    withContext(Dispatchers.EDT) {
      //TODO[jo]: allow to configure that from commandline parameters
      PatchProjectUtil.patchProject(project)
    }
  }

  yieldAndWaitForDumbModeEnd(project)

  callProjectConfigurators(args) {
    this.runWarmup(project)

    FileBasedIndex.getInstance().asSafely<FileBasedIndexImpl>()?.changedFilesCollector?.ensureUpToDate()
    //the configuration may add more dumb tasks to complete
    //we flush the queue to avoid a deadlock between a modal progress & invokeLater
    yieldAndWaitForDumbModeEnd(project)
  }

  runTaskAndLogTime("check project roots") {
    val errors = TreeSet<String>()
    val missingSDKs = TreeSet<String>()
    readAction {
      ProjectRootManager.getInstance(project).contentRoots.forEach { file ->
        if (!file.exists()) {
          errors += "Missing root: $file"
        }
      }

      ProjectRootManager.getInstance(project).orderEntries().forEach { root ->
        OrderRootType.getAllTypes().flatMap { root.getFiles(it).toList() }.forEach { file ->
          if (!file.exists()) {
            errors += "Missing root: $file for ${root.ownerModule.name} for ${root.presentableName}"
          }
        }

        if (root is JdkOrderEntry && root.jdk == null) {
          root.jdkName?.let { missingSDKs += it }
        }

        true
      }
    }

    errors += missingSDKs.map { "Missing JDK entry: ${it}" }
    errors.forEach { WarmupLogger.logInfo(it) }
  }

  WarmupLogger.logInfo("Project is ready for the import")
  return project
}

private val listener = object : ConversionListener {

  override fun error(message: String) {
    WarmupLogger.logInfo("PROGRESS: $message")
  }

  override fun conversionNeeded() {
    WarmupLogger.logInfo("PROGRESS: Project conversion is needed")
  }

  override fun successfullyConverted(backupDir: Path) {
    WarmupLogger.logInfo("PROGRESS: Project was successfully converted")
  }

  override fun cannotWriteToFiles(readonlyFiles: List<Path>) {
    WarmupLogger.logInfo("PROGRESS: Project conversion failed for:\n" + readonlyFiles.joinToString("\n"))
  }
}

private suspend fun callProjectConversion(projectArgs: OpenProjectArgs) {
  if (!projectArgs.convertProject) {
    return
  }

  val conversionService = ConversionService.getInstance() ?: return
  runTaskAndLogTime("convert project") {
    WarmupLogger.logInfo("Checking if conversions are needed for the project")
    val conversionResult = withContext(Dispatchers.EDT) {
      blockingContext {
        conversionService.convertSilently(projectArgs.projectDir, listener)
      }
    }

    if (conversionResult.openingIsCanceled()) {
      throw RuntimeException("Failed to run project conversions before open")
    }

    if (conversionResult.conversionNotNeeded()) {
      WarmupLogger.logInfo("No conversions were needed")
    }
  }

  yieldThroughInvokeLater()
}

private suspend fun callProjectConfigurators(
  projectArgs: OpenProjectArgs,
  action: suspend WarmupConfigurator.() -> Unit
) {

  if (!projectArgs.configureProject) return

  val activeConfigurators = getAllConfigurators().filter {
    if (it.configuratorPresentableName in projectArgs.disabledConfigurators) {
      WarmupLogger.logInfo("Configurator ${it.configuratorPresentableName} is disabled in the settings")
      false
    } else {
      true
    }
  }

  val fraction = 1.0 / activeConfigurators.size.toDouble()

  withLoggingProgressReporter {
    for (configuration in activeConfigurators) {
      durationStep(fraction, "Configurator ${configuration.configuratorPresentableName} is in action..." /* NON-NLS */) {
        runTaskAndLogTime("Configure " + configuration.configuratorPresentableName) {
          try {
            action(configuration)
          } catch (e : CancellationException) {
            val message = (e.message ?: e.stackTraceToString()).lines().joinToString("\n") { "[${configuration.configuratorPresentableName}]: $it" }
            WarmupLogger.logInfo("Configurator '${configuration.configuratorPresentableName}' was cancelled with the following outcome:\n$message")
          }
        }
      }
    }
  }

  yieldThroughInvokeLater()
}

private fun getAllConfigurators() : List<WarmupConfigurator> {
  return WarmupConfigurator.EP_NAME.extensionList +
         CommandLineInspectionProjectConfigurator.EP_NAME.extensionList
           .filter { it.name.startsWith("qodana").not() }
           .map(::WarmupConfiguratorOfCLIConfigurator)
}
