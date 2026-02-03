// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.warmup.util

import com.intellij.conversion.ConversionListener
import com.intellij.conversion.ConversionService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.waitForSmartMode
import com.intellij.openapi.roots.JdkOrderEntry
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.util.TreeSet

fun <Y : Any> runAndCatchNotNull(errorMessage: String, action: () -> Y?): Y {
  try {
    return action() ?: error("<null> was returned!")
  }
  catch (t: Throwable) {
    throw Error("Failed to $errorMessage. ${t.message}", t)
  }
}

private fun assertInnocentThreadToWait() {
  require(!ApplicationManager.getApplication().isWriteAccessAllowed) { "Must not leak write action" }
  require(!ApplicationManager.getApplication().isWriteIntentLockAcquired) { "Must not run in Write Thread" }
  ApplicationManager.getApplication().assertIsNonDispatchThread()
  ApplicationManager.getApplication().assertReadAccessNotAllowed()
}

@ApiStatus.Internal
suspend fun yieldThroughInvokeLater() {
  assertInnocentThreadToWait()

  runTaskAndLogTime("Later Invocations in EDT") {
    withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      yield()
    }
  }
}

private suspend fun completeJustSubmittedDumbServiceTasks(project: Project) {
  assertInnocentThreadToWait()
  runTaskAndLogTime("Completing just submitted DumbService tasks") {
    withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      DumbService.getInstance(project).completeJustSubmittedTasks()
    }
  }
}

internal suspend fun yieldAndWaitForDumbModeEnd(project: Project) {
  assertInnocentThreadToWait()
  completeJustSubmittedDumbServiceTasks(project)

  runTaskAndLogTime("Awaiting smart mode") {
    project.waitForSmartMode()
  }

  yieldThroughInvokeLater()
}

internal suspend fun checkProjectRoots(project: Project) {
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
}


internal suspend fun getProjectFile(args: OpenProjectArgs): VirtualFile {
  val vfsProject = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(args.projectDir)
                   ?: throw RuntimeException("Project path ${args.projectDir} is not found")

  runTaskAndLogTime("refresh VFS") {
    WarmupLogger.logInfo("Refreshing VFS ${args.projectDir}...")
    VfsUtil.markDirtyAndRefresh(false, true, true, args.projectDir.toFile())
  }
  return vfsProject
}

internal suspend fun callProjectConversion(projectArgs: OpenProjectArgs) {
  if (!projectArgs.convertProject) {
    return
  }

  val conversionService = ConversionService.getInstance() ?: return
  runTaskAndLogTime("convert project") {
    WarmupLogger.logInfo("Checking if conversions are needed for the project")
    val conversionResult = withContext(Dispatchers.EDT) {
      conversionService.convertSilently(projectArgs.projectDir, listener)
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
