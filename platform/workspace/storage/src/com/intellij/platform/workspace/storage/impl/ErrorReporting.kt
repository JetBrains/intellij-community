// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.impl.serialization.EntityStorageSerializerImpl
import com.intellij.platform.workspace.storage.impl.serialization.getCacheMetadata
import com.intellij.platform.workspace.storage.impl.serialization.registration.registerEntitiesClasses
import com.intellij.platform.workspace.storage.impl.url.VirtualFileUrlManagerImpl
import com.intellij.util.io.Compressor
import org.jetbrains.annotations.ApiStatus
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.io.path.createFile
import kotlin.io.path.readBytes

@ApiStatus.Internal
public fun reportErrorAndAttachStorage(message: String) {
  reportConsistencyIssue(message = message,
                         e = IllegalStateException(),
                         sourceFilter = null,
                         left = null,
                         right = null)
}

internal fun getStoreDumpDirectory(): Path {
  val property = System.getProperty("ide.new.project.model.store.dump.directory")
  return if (property == null) {
    val pathPrefix = "storeDump-" + formatTime(System.currentTimeMillis())
    val workspaceModelDumps = Paths.get(PathManager.getLogPath(), "workspaceModel")
    cleanOldFiles(workspaceModelDumps.toFile())
    val currentDumpDir = workspaceModelDumps.resolve(pathPrefix)
    FileUtil.createDirectory(currentDumpDir.toFile())
    return currentDumpDir
  }
  else Paths.get(property)
}

internal fun executingOnTC() = System.getProperty("ide.new.project.model.store.dump.directory") != null

private fun cleanOldFiles(parentDir: File) {
  val children = parentDir.listFiles() ?: return
  Arrays.sort(children)
  for (i in children.indices) {
    val child = children[i]
    // Store the latest 30 items in the folder and not older than one week
    if (i < children.size - 30 || ageInDays(child) > 7) FileUtil.delete(child)
  }
}

private fun formatTime(timeMs: Long) = SimpleDateFormat("yyyyMMdd-HHmmss").format(Date(timeMs))

private fun ageInDays(file: File) = TimeUnit.DAYS.convert(System.currentTimeMillis() - file.lastModified(), TimeUnit.MILLISECONDS)


private fun serializeContentToFolder(contentFolder: Path,
                                     left: EntityStorage?,
                                     right: EntityStorage?,
                                     sourceFilter: ((EntitySource) -> Boolean)?): Path? {
  val operationName = if (sourceFilter == null) "Add_Diff" else "Replace_By_Source"
  val operationFile = contentFolder.resolve(operationName)
  Files.createFile(operationFile)
  if (!isWrapped()) {
    val entitySourceFilter = if (sourceFilter != null) {
      val allEntitySources = (left as? AbstractEntityStorage)?.indexes?.entitySourceIndex?.entries()?.toHashSet() ?: hashSetOf()
      allEntitySources.addAll((right as? AbstractEntityStorage)?.indexes?.entitySourceIndex?.entries() ?: emptySet())
      allEntitySources.sortedBy { it.toString() }.fold("") { acc, source -> acc + if (sourceFilter(source)) "1" else "0" }
    }
    else {
      ""
    }
    Files.writeString(operationFile, entitySourceFilter)
  }

  if (isWrapped()) contentFolder.resolve("Report_Wrapped").createFile()

  return if (!executingOnTC()) {
    val zipFile = contentFolder.parent.resolve("${contentFolder.fileName}.zip")
    Compressor.Zip(zipFile).use { it.addDirectory(contentFolder) }
    NioFiles.deleteRecursively(contentFolder)
    zipFile
  }
  else null
}

internal fun reportConsistencyIssue(message: String,
                                    e: Throwable,
                                    sourceFilter: ((EntitySource) -> Boolean)?,
                                    left: EntityStorage?,
                                    right: EntityStorage?) {
  var finalMessage = "$message\n\n"
  finalMessage += "\nVersion: ${EntityStorageSerializerImpl.STORAGE_SERIALIZATION_VERSION}"

  val zipFile = if (ConsistencyCheckingMode.current != ConsistencyCheckingMode.DISABLED) {
    val dumpDirectory = getStoreDumpDirectory()
    finalMessage += "\nSaving store content at: $dumpDirectory"
    serializeContentToFolder(dumpDirectory, left, right, sourceFilter)
  }
  else null

  if (zipFile != null) {
    val attachment = Attachment("workspaceModelDump.zip", zipFile.readBytes(), "Zip of workspace model store")
    attachment.isIncluded = true
    AbstractEntityStorage.LOG.error(finalMessage, e, attachment)
  }
  else {
    AbstractEntityStorage.LOG.error(finalMessage, e)
  }
}

private fun isWrapped(): Boolean = Registry.`is`("ide.new.project.model.report.wrapped", true)
