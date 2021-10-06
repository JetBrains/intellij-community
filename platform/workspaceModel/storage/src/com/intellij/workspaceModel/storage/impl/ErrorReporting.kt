// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.Compressor
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.impl.url.VirtualFileUrlManagerImpl
import org.jetbrains.annotations.ApiStatus
import java.io.File
import java.io.OutputStream
import java.nio.file.Path
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.io.path.name

@ApiStatus.Internal
fun reportErrorAndAttachStorage(message: String, storage: WorkspaceEntityStorage) {
  reportConsistencyIssue(message, IllegalStateException(), null, null, null, storage)
}

internal fun serializeContent(path: Path, howToSerialize: (EntityStorageSerializerImpl, OutputStream) -> Unit) {
  val serializer = EntityStorageSerializerImpl(SimpleEntityTypesResolver, VirtualFileUrlManagerImpl())
  path.toFile().outputStream().use { howToSerialize(serializer, it) }
}

internal fun serializeEntityStorage(path: Path, storage: WorkspaceEntityStorage) {
  serializeContent(path) { serializer, stream ->
    serializer.serializeCache(stream, storage.makeSureItsStore())
  }
}

private fun WorkspaceEntityStorage.makeSureItsStore(): WorkspaceEntityStorage {
  return if (this is WorkspaceEntityStorageBuilderImpl) this.toStorage() else this
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
    // Store latest 30 items in the folder and not older than one week
    if (i < children.size - 30 || ageInDays(child) > 7) FileUtil.delete(child)
  }
}

private fun formatTime(timeMs: Long) = SimpleDateFormat("yyyyMMdd-HHmmss").format(Date(timeMs))

private fun ageInDays(file: File) = TimeUnit.DAYS.convert(System.currentTimeMillis() - file.lastModified(), TimeUnit.MILLISECONDS)

internal fun WorkspaceEntityStorage.serializeTo(stream: OutputStream) {
  val serializer = EntityStorageSerializerImpl(SimpleEntityTypesResolver, VirtualFileUrlManagerImpl())
  serializer.serializeCache(stream, this.makeSureItsStore())
}

internal fun WorkspaceEntityStorageBuilderImpl.serializeDiff(stream: OutputStream) {
  val serializer = EntityStorageSerializerImpl(SimpleEntityTypesResolver, VirtualFileUrlManagerImpl())
  serializeDiff(serializer, stream)
}

private fun WorkspaceEntityStorageBuilderImpl.serializeDiff(serializer: EntityStorageSerializerImpl, stream: OutputStream) {
  serializer.serializeDiffLog(stream, this.changeLog)
}

private fun serializeContentToFolder(contentFolder: Path,
                                     left: WorkspaceEntityStorage?,
                                     right: WorkspaceEntityStorage?,
                                     resulting: WorkspaceEntityStorage): File? {

  if (right is WorkspaceEntityStorageBuilder) {
    serializeContent(contentFolder.resolve("Right_Diff_Log")) { serializer, stream ->
      right as WorkspaceEntityStorageBuilderImpl
      right.serializeDiff(serializer, stream)
    }
  }

  left?.let { serializeEntityStorage(contentFolder.resolve("Left_Store"), it) }
  right?.let { serializeEntityStorage(contentFolder.resolve("Right_Store"), it) }
  serializeEntityStorage(contentFolder.resolve("Res_Store"), resulting)
  serializeContent(contentFolder.resolve("ClassToIntConverter")) { serializer, stream -> serializer.serializeClassToIntConverter(stream) }

  return if (!executingOnTC()) {
    val zipFile = contentFolder.parent.resolve(contentFolder.name + ".zip").toFile()
    Compressor.Zip(zipFile).use { it.addDirectory(contentFolder.toFile()) }
    FileUtil.delete(contentFolder)
    zipFile
  }
  else null
}

internal fun reportConsistencyIssue(message: String,
                                    e: Throwable,
                                    sourceFilter: ((EntitySource) -> Boolean)?,
                                    left: WorkspaceEntityStorage?,
                                    right: WorkspaceEntityStorage?,
                                    resulting: WorkspaceEntityStorage) {
  val entitySourceFilter = if (sourceFilter != null) {
    val allEntitySources = (left as? AbstractEntityStorage)?.indexes?.entitySourceIndex?.entries()?.toHashSet() ?: hashSetOf()
    allEntitySources.addAll((right as? AbstractEntityStorage)?.indexes?.entitySourceIndex?.entries() ?: emptySet())
    allEntitySources.sortedBy { it.toString() }.fold("") { acc, source -> acc + if (sourceFilter(source)) "1" else "0" }
  }
  else null

  var finalMessage = "$message\n\nEntity source filter: $entitySourceFilter"
  finalMessage += "\n\nVersion: ${EntityStorageSerializerImpl.SERIALIZER_VERSION}"

  val zipFile = if (ConsistencyCheckingMode.current != ConsistencyCheckingMode.DISABLED) {
    val dumpDirectory = getStoreDumpDirectory()
    finalMessage += "\nSaving store content at: $dumpDirectory"
    serializeContentToFolder(dumpDirectory, left, right, resulting)
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
