// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl

import com.intellij.serialization.ObjectSerializer
import com.intellij.serialization.ReadConfiguration
import com.intellij.serialization.WriteConfiguration
import com.intellij.util.containers.BidirectionalMultiMap
import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.impl.indices.*
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import java.io.InputStream
import java.io.OutputStream

class IonSerializer(@Suppress("UNUSED_PARAMETER") virtualFileManager: VirtualFileUrlManager) : EntityStorageSerializer {
  override val serializerDataFormatVersion: String = "v1"

  override fun serializeCache(stream: OutputStream, storage: WorkspaceEntityStorage): SerializationResult {
    storage as WorkspaceEntityStorageImpl
    val configuration = WriteConfiguration(allowAnySubTypes = true)
    val ion = ObjectSerializer.instance

    ion.write(storage.entitiesByType, stream, configuration)
    ion.write(storage.refs, stream, configuration)

    ion.write(storage.indexes.softLinks, stream, configuration)

    ion.write(storage.indexes.virtualFileIndex.entityId2VirtualFileUrl, stream, configuration)
    ion.write(storage.indexes.virtualFileIndex.vfu2EntityId, stream, configuration)
    ion.write(storage.indexes.virtualFileIndex.entityId2JarDir, stream, configuration)

    ion.write(storage.indexes.entitySourceIndex, stream, configuration)
    ion.write(storage.indexes.persistentIdIndex, stream, configuration)

    return SerializationResult.Success
  }

  @Suppress("UNCHECKED_CAST")
  override fun deserializeCache(stream: InputStream): WorkspaceEntityStorageBuilder {
    val configuration = ReadConfiguration(allowAnySubTypes = true)
    val ion = ObjectSerializer.instance

    // Read entity data and references
    val entitiesBarrel = ion.read(ImmutableEntitiesBarrel::class.java, stream, configuration)
    val refsTable = ion.read(RefsTable::class.java, stream, configuration)

    val softLinks = ion.read(MultimapStorageIndex::class.java, stream, configuration)

    val entityId2VirtualFileUrlInfo = ion.read(Object2ObjectOpenHashMap::class.java, stream, configuration) as EntityId2Vfu
    val vfu2VirtualFileUrlInfo = ion.read(Object2ObjectOpenHashMap::class.java, stream, configuration) as Vfu2EntityId
    val entityId2JarDir = ion.read(BidirectionalMultiMap::class.java, stream, configuration) as EntityId2JarDir
    val virtualFileIndex = VirtualFileIndex(entityId2VirtualFileUrlInfo, vfu2VirtualFileUrlInfo, entityId2JarDir)

    val entitySourceIndex = ion.read(EntityStorageInternalIndex::class.java, stream, configuration) as EntityStorageInternalIndex<EntitySource>
    val persistentIdIndex = ion.read(PersistentIdInternalIndex::class.java, stream, configuration)
    val storageIndexes = StorageIndexes(softLinks, virtualFileIndex, entitySourceIndex, persistentIdIndex)

    val storage = WorkspaceEntityStorageImpl(entitiesBarrel, refsTable, storageIndexes)
    val builder = WorkspaceEntityStorageBuilderImpl.from(storage)

    builder.entitiesByType.entityFamilies.forEach { family ->
      family?.entities?.asSequence()?.filterNotNull()?.forEach { entityData -> builder.createAddEvent(entityData) }
    }

    return builder
  }
}
