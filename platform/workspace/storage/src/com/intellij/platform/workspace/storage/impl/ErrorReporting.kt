// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.workspace.storage.*
import com.intellij.util.io.Compressor
import com.intellij.platform.workspace.storage.impl.serialization.EntityStorageSerializerImpl
import com.intellij.platform.workspace.storage.impl.serialization.getCacheMetadata
import com.intellij.platform.workspace.storage.impl.serialization.registration.registerEntitiesClasses
import com.intellij.platform.workspace.storage.impl.url.VirtualFileUrlManagerImpl
import org.jetbrains.annotations.ApiStatus
import java.io.File
import java.lang.UnsupportedOperationException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.io.path.createFile
import kotlin.io.path.readBytes

@ApiStatus.Internal
public fun reportErrorAndAttachStorage(message: String, storage: EntityStorage) {
  reportConsistencyIssue(message = message,
                         e = IllegalStateException(),
                         sourceFilter = null,
                         left = null,
                         right = null,
                         resulting = storage)
}

internal fun serializeContent(file: Path, howToSerialize: (EntityStorageSerializerImpl, Path) -> Unit) {
  val serializer = EntityStorageSerializerImpl(SimpleEntityTypesResolver, VirtualFileUrlManagerImpl())
  howToSerialize(serializer, file)
}

internal fun serializeEntityStorage(file: Path, storage: EntityStorage) {
  serializeContent(file) { serializer, stream ->
    serializer.serializeCache(stream, storage.toSnapshot())
  }
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

internal fun EntityStorage.serializeTo(file: Path) {
  val serializer = EntityStorageSerializerImpl(SimpleEntityTypesResolver, VirtualFileUrlManagerImpl())
  serializer.serializeCache(file, toSnapshot())
}

internal fun MutableEntityStorageImpl.serializeDiff(file: Path) {
  val serializer = EntityStorageSerializerImpl(SimpleEntityTypesResolver, VirtualFileUrlManagerImpl())
  serializeDiff(serializer, file)
}

private fun MutableEntityStorageImpl.serializeDiff(serializer: EntityStorageSerializerImpl, file: Path) {
  serializer.serializeDiffLog(
    file, changeLog.changeLog.anonymize(),
    indexes.entitySourceIndex.entries(), indexes.symbolicIdIndex.entries()
  )
}

internal fun EntityStorage.anonymize(sourceFilter: ((EntitySource) -> Boolean)?): EntityStorage {
  if (!isWrapped()) return this
  val builder = MutableEntityStorage.from(this.toSnapshot())
  builder.entitiesBySource { true }.flatMap { entry -> entry.value.flatMap { it.value } }.forEach { entity ->
    builder.modifyEntity(WorkspaceEntity.Builder::class.java, entity) {
      this.entitySource = entity.entitySource.anonymize(sourceFilter)
    }
  }
  return builder.toSnapshot()
}

internal fun ChangeLog.anonymize(): ChangeLog {
  if (!isWrapped()) return this
  val result = HashMap(this)
  result.replaceAll { _, value ->
    when (value) {
      is ChangeEntry.AddEntity -> {
        val newEntityData = value.entityData.clone()
        newEntityData.entitySource = newEntityData.entitySource.anonymize(null)
        ChangeEntry.AddEntity(newEntityData, value.clazz)
      }
      is ChangeEntry.ChangeEntitySource -> {
        val newEntityData = value.newData.clone()
        newEntityData.entitySource = newEntityData.entitySource.anonymize(null)
        ChangeEntry.ChangeEntitySource(value.originalSource.anonymize(null), newEntityData)
      }
      is ChangeEntry.RemoveEntity -> value
      is ChangeEntry.ReplaceAndChangeSource -> {
        val newEntityData = value.sourceChange.newData.clone()
        newEntityData.entitySource = newEntityData.entitySource.anonymize(null)
        val sourceChange = ChangeEntry.ChangeEntitySource(value.sourceChange.originalSource.anonymize(null), newEntityData)

        val dataChange = if (value.dataChange.data != null) {
          @Suppress("UNCHECKED_CAST")
          val changedData = value.dataChange.data.newData.clone() as WorkspaceEntityData<WorkspaceEntity>
          changedData.entitySource = changedData.entitySource.anonymize(null)
          @Suppress("UNCHECKED_CAST")
          val changedOldData = value.dataChange.data.oldData.clone() as WorkspaceEntityData<WorkspaceEntity>
          changedOldData.entitySource = changedOldData.entitySource.anonymize(null)
          ChangeEntry.ReplaceEntity(ChangeEntry.ReplaceEntity.Data(changedOldData, changedData), value.dataChange.references?.copy())
        }
        else {
          ChangeEntry.ReplaceEntity(null, value.dataChange.references?.copy())
        }
        ChangeEntry.ReplaceAndChangeSource(dataChange, sourceChange)
      }
      is ChangeEntry.ReplaceEntity -> {
        if (value.data != null) {
          @Suppress("UNCHECKED_CAST")
          val newEntityData = value.data.newData.clone() as WorkspaceEntityData<WorkspaceEntity>
          newEntityData.entitySource = newEntityData.entitySource.anonymize(null)
          @Suppress("UNCHECKED_CAST")
          val oldEntityData = value.data.oldData.clone() as WorkspaceEntityData<WorkspaceEntity>
          oldEntityData.entitySource = oldEntityData.entitySource.anonymize(null)
          ChangeEntry.ReplaceEntity(ChangeEntry.ReplaceEntity.Data(oldEntityData, newEntityData), value.references?.copy())
        }
        else {
          ChangeEntry.ReplaceEntity(null, value.references?.copy())
        }
      }
    }
  }
  return result
}

internal fun EntitySource.anonymize(sourceFilter: ((EntitySource) -> Boolean)?): EntitySource {
  return if (sourceFilter != null) {
    if (sourceFilter(this)) {
      MatchedEntitySource(this.toString())
    }
    else {
      UnmatchedEntitySource(this.toString())
    }
  }
  else {
    AnonymizedEntitySource(this.toString())
  }
}

public data class AnonymizedEntitySource(val originalSourceDump: String) : EntitySource
public data class MatchedEntitySource(val originalSourceDump: String) : EntitySource
public data class UnmatchedEntitySource(val originalSourceDump: String) : EntitySource

private fun serializeContentToFolder(contentFolder: Path,
                                     left: EntityStorage?,
                                     right: EntityStorage?,
                                     resulting: EntityStorage,
                                     sourceFilter: ((EntitySource) -> Boolean)?): Path? {
  if (right is MutableEntityStorage) {
    serializeContent(contentFolder.resolve("Right_Diff_Log")) { serializer, file ->
      right as MutableEntityStorageImpl
      right.serializeDiff(serializer, file)
    }
  }

  left?.anonymize(sourceFilter)?.let { serializeEntityStorage(contentFolder.resolve("Left_Store"), it) }
  right?.anonymize(sourceFilter)?.let { serializeEntityStorage(contentFolder.resolve("Right_Store"), it) }
  serializeEntityStorage(contentFolder.resolve("Res_Store"), resulting.anonymize(sourceFilter))
  serializeContent(contentFolder.resolve("ClassToIntConverter"), EntityStorageSerializerImpl::serializeClassToIntConverter)

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

private fun EntityStorageSerializerImpl.serializeDiffLog(file: Path, log: ChangeLog,
                                                         entitySources: Collection<EntitySource>,
                                                         symbolicIds: Collection<SymbolicEntityId<*>>) {
  val output = createKryoOutput(file)
  try {
    val (kryo, classCache) = createKryo()

    // Save version
    output.writeString(serializerDataFormatVersion)

    val entityDataSequence = log.values.mapNotNull {
      when (it) {
        is ChangeEntry.AddEntity -> it.entityData
        is ChangeEntry.RemoveEntity -> null
        is ChangeEntry.ReplaceEntity -> it.data?.newData
        is ChangeEntry.ChangeEntitySource -> it.newData
        is ChangeEntry.ReplaceAndChangeSource -> it.dataChange.data?.newData
      }
    }.asSequence()

    val entitiesMetadata = getCacheMetadata(entityDataSequence, entitySources, symbolicIds, typesResolver)
    kryo.writeObject(output, entitiesMetadata)

    registerEntitiesClasses(kryo, entitiesMetadata, typesResolver, classCache)

    kryo.writeClassAndObject(output, log)
  }
  finally {
    closeOutput(output)
  }
}

private fun EntityStorageSerializerImpl.serializeClassToIntConverter(file: Path) {
  val converterMap = ClassToIntConverter.getInstance().getMap().toMap()
  val output = createKryoOutput(file)
  try {
    val (kryo, _) = createKryo()

    // Save version
    output.writeString(serializerDataFormatVersion)

    val mapData = converterMap.map { (key, value) -> key.typeInfo to value }

    kryo.writeClassAndObject(output, mapData)
  }
  finally {
    closeOutput(output)
  }
}

internal fun reportConsistencyIssue(message: String,
                                    e: Throwable,
                                    sourceFilter: ((EntitySource) -> Boolean)?,
                                    left: EntityStorage?,
                                    right: EntityStorage?,
                                    resulting: EntityStorage) {
  var finalMessage = "$message\n\n"
  finalMessage += "\nVersion: ${EntityStorageSerializerImpl.STORAGE_SERIALIZATION_VERSION}"

  val zipFile = if (ConsistencyCheckingMode.current != ConsistencyCheckingMode.DISABLED) {
    val dumpDirectory = getStoreDumpDirectory()
    finalMessage += "\nSaving store content at: $dumpDirectory"
    serializeContentToFolder(dumpDirectory, left, right, resulting, sourceFilter)
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
