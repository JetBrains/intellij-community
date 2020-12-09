// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl.indices

import com.intellij.openapi.diagnostic.logger
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryRoot
import com.intellij.workspaceModel.storage.impl.*
import com.intellij.workspaceModel.storage.impl.AbstractEntityStorage
import com.intellij.workspaceModel.storage.impl.EntityId
import com.intellij.workspaceModel.storage.url.MutableVirtualFileUrlIndex
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlIndex
import org.jetbrains.annotations.TestOnly
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties

internal typealias EntityId2Vfu = HashMap<EntityId, MutableMap<String, MutableSet<VirtualFileUrl>>>
internal typealias Vfu2EntityId = HashMap<VirtualFileUrl, MutableMap<String, EntityId>>

open class VirtualFileIndex internal constructor(
  internal open val entityId2VirtualFileUrl: EntityId2Vfu,
  internal open val vfu2EntityId: Vfu2EntityId
): VirtualFileUrlIndex {
  private lateinit var entityStorage: AbstractEntityStorage
  constructor() : this(EntityId2Vfu(), Vfu2EntityId())

  internal fun getVirtualFiles(id: EntityId): Set<VirtualFileUrl> =
    entityId2VirtualFileUrl[id]?.values?.flatten()?.toSet() ?: emptySet()

  internal fun getVirtualFileUrlInfoByEntityId(id: EntityId): Map<String, MutableSet<VirtualFileUrl>> {
    val property2VfuMap = entityId2VirtualFileUrl[id] ?: return emptyMap()
    val copiedVfuMap = HashMap<String, MutableSet<VirtualFileUrl>>()
    property2VfuMap.forEach { copiedVfuMap[it.key] = HashSet(it.value) }
    return copiedVfuMap
  }

  override fun findEntitiesByUrl(fileUrl: VirtualFileUrl): Sequence<Pair<WorkspaceEntity, String>> =
    vfu2EntityId[fileUrl]?.asSequence()?.mapNotNull {
      val entityData = entityStorage.entityDataById(it.value) ?: return@mapNotNull null
      entityData.createEntity(entityStorage) to it.key
    } ?: emptySequence()

  internal fun setTypedEntityStorage(storage: AbstractEntityStorage) {
    entityStorage = storage
  }

  class MutableVirtualFileIndex private constructor(
    // Do not write to [entityId2VirtualFileUrl]  and [vfu2EntityId] directly! Create a dedicated method for that
    // and call [startWrite] before write.
    override var entityId2VirtualFileUrl: EntityId2Vfu,
    override var vfu2EntityId: Vfu2EntityId
  ) : VirtualFileIndex(entityId2VirtualFileUrl, vfu2EntityId), MutableVirtualFileUrlIndex {

    private var freezed = true

    @Synchronized
    override fun index(entity: WorkspaceEntity, propertyName: String, virtualFileUrl: VirtualFileUrl?) {
      index((entity as WorkspaceEntityBase).id, propertyName, virtualFileUrl)
    }

    @Synchronized
    internal fun index(id: EntityId, propertyName: String, virtualFileUrls: Set<VirtualFileUrl>) {
      startWrite()
      val newVirtualFileUrls = HashSet(virtualFileUrls)
      val existingVfuSet = entityId2VirtualFileUrl[id]?.get(propertyName)
      existingVfuSet?.removeIf { vfu ->
        val elementRemoved = newVirtualFileUrls.remove(vfu)
        if (!elementRemoved) removeFromVfu2EntityIdMap(id, propertyName, vfu)
        return@removeIf !elementRemoved
      }
      newVirtualFileUrls.forEach { indexVirtualFileUrl(id, propertyName, it) }

      existingVfuSet?.let { if (it.isEmpty()) entityId2VirtualFileUrl[id]?.remove(propertyName) }
      entityId2VirtualFileUrl[id]?.let { if (it.isEmpty()) entityId2VirtualFileUrl.remove(id) }
    }

    @Synchronized
    internal fun index(id: EntityId, propertyName: String, virtualFileUrl: VirtualFileUrl? = null) {
      startWrite()
      removeByPropertyFromIndexes(id, propertyName)
      if (virtualFileUrl == null) return
      indexVirtualFileUrl(id, propertyName, virtualFileUrl)
    }

    @Synchronized
    internal fun removeRecordsByEntityId(id: EntityId) {
      startWrite()
      entityId2VirtualFileUrl.remove(id)?.forEach { (property, vfuSet) ->
        vfuSet.forEach{ vfu -> removeFromVfu2EntityIdMap(id, property, vfu) }
      }
    }

    @TestOnly
    internal fun clear() {
      startWrite()
      entityId2VirtualFileUrl.clear()
      vfu2EntityId.clear()
    }

    @TestOnly
    internal fun copyFrom(another: VirtualFileIndex) {
      startWrite()
      entityId2VirtualFileUrl.putAll(another.entityId2VirtualFileUrl)
      vfu2EntityId.putAll(another.vfu2EntityId)
    }

    private fun startWrite() {
      if (!freezed) return
      freezed = false
      entityId2VirtualFileUrl = copyEntityMap(entityId2VirtualFileUrl)
      vfu2EntityId = copyVfuMap(vfu2EntityId)
    }

    fun toImmutable(): VirtualFileIndex {
      freezed = true
      return VirtualFileIndex(entityId2VirtualFileUrl, vfu2EntityId)
    }

    private fun indexVirtualFileUrl(id: EntityId, propertyName: String, virtualFileUrl: VirtualFileUrl) {
      val property2Vfu = entityId2VirtualFileUrl.getOrDefault(id, HashMap())
      val vfuSet = property2Vfu.getOrDefault(propertyName, HashSet())
      vfuSet.add(virtualFileUrl)
      property2Vfu[propertyName] = vfuSet
      entityId2VirtualFileUrl[id] = property2Vfu

      val property2EntityId = vfu2EntityId.getOrDefault(virtualFileUrl, HashMap())
      property2EntityId[getCompositeKey(id,propertyName)] = id
      vfu2EntityId[virtualFileUrl] = property2EntityId
    }

    private fun removeByPropertyFromIndexes(id: EntityId, propertyName: String) {
      val property2vfu = entityId2VirtualFileUrl[id] ?: return
      val vfuSet = property2vfu.remove(propertyName) ?: return
      if (property2vfu.isEmpty()) entityId2VirtualFileUrl.remove(id)

      vfuSet.forEach { removeFromVfu2EntityIdMap(id, propertyName, it) }
    }

    private fun removeFromVfu2EntityIdMap(id: EntityId, propertyName: String, vfu: VirtualFileUrl) {
      val property2EntityId = vfu2EntityId[vfu]
      if (property2EntityId == null) {
        LOG.error("The record for $id <=> ${vfu} should be available in both maps")
        return
      }
      property2EntityId.remove(getCompositeKey(id,propertyName))
      if (property2EntityId.isEmpty()) vfu2EntityId.remove(vfu)
    }

    private fun copyEntityMap(originMap: EntityId2Vfu): EntityId2Vfu{
      val copiedMap = EntityId2Vfu()
      originMap.forEach{ (entityId, vfuMap) ->
        val copiedVfuMap = HashMap<String, MutableSet<VirtualFileUrl>>()
        vfuMap.forEach { copiedVfuMap[it.key] = HashSet(it.value) }
        copiedMap[entityId] = copiedVfuMap
      }
      return copiedMap
    }

    private fun copyVfuMap(originMap: Vfu2EntityId): Vfu2EntityId{
      val copiedMap = Vfu2EntityId()
      originMap.forEach{ (key, value) -> copiedMap[key] = HashMap(value) }
      return copiedMap
    }

    private fun getCompositeKey(entityId: EntityId, propertyName: String) = "${entityId}_$propertyName"

    companion object {
      private val LOG = logger<MutableVirtualFileIndex>()
      const val VIRTUAL_FILE_INDEX_ENTITY_SOURCE_PROPERTY = "entitySource"
      fun from(other: VirtualFileIndex): MutableVirtualFileIndex = MutableVirtualFileIndex(other.entityId2VirtualFileUrl, other.vfu2EntityId)
    }
  }
}

//---------------------------------------------------------------------
class VirtualFileUrlProperty<T : ModifiableWorkspaceEntityBase<out WorkspaceEntityBase>> : ReadWriteProperty<T, VirtualFileUrl> {
  override fun getValue(thisRef: T, property: KProperty<*>): VirtualFileUrl {
    return ((thisRef.original::class.memberProperties.first { it.name == property.name }) as KProperty1<Any, *>)
      .get(thisRef.original) as VirtualFileUrl
  }

  override fun setValue(thisRef: T, property: KProperty<*>, value: VirtualFileUrl) {
    if (!thisRef.modifiable.get()) {
      throw IllegalStateException("Modifications are allowed inside 'addEntity' and 'modifyEntity' methods only!")
    }
    val field = thisRef.original.javaClass.getDeclaredField(property.name)
    field.isAccessible = true
    field.set(thisRef.original, value)
    thisRef.diff.indexes.virtualFileIndex.index(thisRef.id, property.name, value)
  }
}

//---------------------------------------------------------------------
class VirtualFileUrlNullableProperty<T : ModifiableWorkspaceEntityBase<out WorkspaceEntityBase>> : ReadWriteProperty<T, VirtualFileUrl?> {
  override fun getValue(thisRef: T, property: KProperty<*>): VirtualFileUrl? {
    return ((thisRef.original::class.memberProperties.first { it.name == property.name }) as KProperty1<Any, *>)
      .get(thisRef.original) as VirtualFileUrl?
  }

  override fun setValue(thisRef: T, property: KProperty<*>, value: VirtualFileUrl?) {
    if (!thisRef.modifiable.get()) {
      throw IllegalStateException("Modifications are allowed inside 'addEntity' and 'modifyEntity' methods only!")
    }
    val field = thisRef.original.javaClass.getDeclaredField(property.name)
    field.isAccessible = true
    field.set(thisRef.original, value)
    thisRef.diff.indexes.virtualFileIndex.index(thisRef.id, property.name, value)
  }
}

//---------------------------------------------------------------------
class VirtualFileUrlListProperty<T : ModifiableWorkspaceEntityBase<out WorkspaceEntityBase>> : ReadWriteProperty<T, List<VirtualFileUrl>> {
  override fun getValue(thisRef: T, property: KProperty<*>): List<VirtualFileUrl> {
    return ((thisRef.original::class.memberProperties.first { it.name == property.name }) as KProperty1<Any, *>)
      .get(thisRef.original) as List<VirtualFileUrl>
  }

  override fun setValue(thisRef: T, property: KProperty<*>, value: List<VirtualFileUrl>) {
    if (!thisRef.modifiable.get()) {
      throw IllegalStateException("Modifications are allowed inside 'addEntity' and 'modifyEntity' methods only!")
    }
    val field = thisRef.original.javaClass.getDeclaredField(property.name)
    field.isAccessible = true
    field.set(thisRef.original, value)
    thisRef.diff.indexes.virtualFileIndex.index(thisRef.id, property.name, value.toHashSet())
  }
}

/**
 * This delegate was created specifically for the handling VirtualFileUrls from LibraryRoot
 */
class VirtualFileUrlLibraryRootProperty<T : ModifiableWorkspaceEntityBase<out WorkspaceEntityBase>> : ReadWriteProperty<T, List<LibraryRoot>> {
  override fun getValue(thisRef: T, property: KProperty<*>): List<LibraryRoot> {
    return ((thisRef.original::class.memberProperties.first { it.name == property.name }) as KProperty1<Any, *>)
      .get(thisRef.original) as List<LibraryRoot>
  }

  override fun setValue(thisRef: T, property: KProperty<*>, value: List<LibraryRoot>) {
    if (!thisRef.modifiable.get()) {
      throw IllegalStateException("Modifications are allowed inside 'addEntity' and 'modifyEntity' methods only!")
    }
    val field = thisRef.original.javaClass.getDeclaredField(property.name)
    field.isAccessible = true
    field.set(thisRef.original, value)
    thisRef.diff.indexes.virtualFileIndex.index(thisRef.id, property.name, value.map { it.url }.toHashSet())
  }
}