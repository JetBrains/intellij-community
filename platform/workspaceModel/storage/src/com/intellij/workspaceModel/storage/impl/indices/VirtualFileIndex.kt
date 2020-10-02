// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl.indices

import com.intellij.workspaceModel.storage.VirtualFileUrl
import com.intellij.workspaceModel.storage.VirtualFileUrlIndex
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryRoot
import com.intellij.workspaceModel.storage.impl.AbstractEntityStorage
import com.intellij.workspaceModel.storage.impl.EntityId
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import org.jetbrains.annotations.TestOnly
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties

open class VirtualFileIndex internal constructor(
  internal open val entityId2VirtualFileUrlInfo: HashMap<EntityId, MutableSet<VirtualFileUrlInfo>>,
  internal open val vfu2VirtualFileUrlInfo: HashMap<VirtualFileUrl, MutableSet<VirtualFileUrlInfo>>
): VirtualFileUrlIndex {
  private lateinit var entityStorage: AbstractEntityStorage
  constructor() : this(HashMap<EntityId, MutableSet<VirtualFileUrlInfo>>(), HashMap<VirtualFileUrl, MutableSet<VirtualFileUrlInfo>>())

  internal fun getVirtualFiles(id: EntityId): Set<VirtualFileUrl> =
    entityId2VirtualFileUrlInfo[id]?.asSequence()?.map { it.vfu }?.toSet() ?: emptySet()

  internal fun getVirtualFileUrlInfoByEntityId(id: EntityId): Sequence<VirtualFileUrlInfo> =
    entityId2VirtualFileUrlInfo[id]?.asSequence() ?: emptySequence()

  override fun findEntitiesByUrl(fileUrl: VirtualFileUrl): Sequence<Pair<WorkspaceEntity, String>> =
    vfu2VirtualFileUrlInfo[fileUrl]?.asSequence()?.mapNotNull {
      val entityData = entityStorage.entityDataById(it.entityId) ?: return@mapNotNull null
      entityData.createEntity(entityStorage) to it.propertyName
    } ?: emptySequence()

  internal fun setTypedEntityStorage(storage: AbstractEntityStorage) {
    entityStorage = storage
  }

  class MutableVirtualFileIndex private constructor(
    // Do not write to [entityId2VirtualFileUrlInfo]  and [vfu2VirtualFileUrlInfo] directly! Create a dedicated method for that
    // and call [startWrite] before write.
    override var entityId2VirtualFileUrlInfo: HashMap<EntityId, MutableSet<VirtualFileUrlInfo>>,
    override var vfu2VirtualFileUrlInfo: HashMap<VirtualFileUrl, MutableSet<VirtualFileUrlInfo>>
  ) : VirtualFileIndex(entityId2VirtualFileUrlInfo, vfu2VirtualFileUrlInfo) {

    private var freezed = true

    internal fun index(id: EntityId, propertyName: String? = null, virtualFileUrls: List<VirtualFileUrl>? = null) {
      startWrite()
      if (propertyName == null) {
        val removedVfuInfos = entityId2VirtualFileUrlInfo.remove(id) ?: return
        removedVfuInfos.forEach {
          val vfuInfos = vfu2VirtualFileUrlInfo[it.vfu] ?: error("The record for $id <=> ${it.vfu} should be available in both maps")
          vfuInfos.remove(it)
          if (vfuInfos.isEmpty()) vfu2VirtualFileUrlInfo.remove(it.vfu)
        }
        return
      }
      removeFromIndexes(id, propertyName)
      if (virtualFileUrls == null) return
      virtualFileUrls.forEach { indexVirtualFileUrl(id, propertyName, it) }
    }

    @TestOnly
    internal fun clear() {
      startWrite()
      entityId2VirtualFileUrlInfo.clear()
      vfu2VirtualFileUrlInfo.clear()
    }

    @TestOnly
    internal fun copyFrom(another: VirtualFileIndex) {
      startWrite()
      entityId2VirtualFileUrlInfo.putAll(another.entityId2VirtualFileUrlInfo)
      vfu2VirtualFileUrlInfo.putAll(another.vfu2VirtualFileUrlInfo)
    }

    private fun startWrite() {
      if (!freezed) return
      freezed = false
      entityId2VirtualFileUrlInfo = copyMap(entityId2VirtualFileUrlInfo)
      vfu2VirtualFileUrlInfo = copyMap(vfu2VirtualFileUrlInfo)
    }

    fun toImmutable(): VirtualFileIndex {
      freezed = true
      return VirtualFileIndex(entityId2VirtualFileUrlInfo, vfu2VirtualFileUrlInfo)
    }

    private fun indexVirtualFileUrl(id: EntityId, propertyName: String, virtualFileUrl: VirtualFileUrl) {
      val entityProperty = VirtualFileUrlInfo(virtualFileUrl, id, propertyName)
      val firstVfuInfos = entityId2VirtualFileUrlInfo.getOrDefault(id, HashSet())
      firstVfuInfos.add(entityProperty)
      entityId2VirtualFileUrlInfo[id] = firstVfuInfos

      val secondVfuInfos = vfu2VirtualFileUrlInfo.getOrDefault(virtualFileUrl, HashSet())
      secondVfuInfos.add(entityProperty)
      vfu2VirtualFileUrlInfo[virtualFileUrl] = secondVfuInfos
    }

    private fun removeFromIndexes(id: EntityId, propertyName: String) {
      val vfuInfos = entityId2VirtualFileUrlInfo[id] ?: return
      val filteredVfuInfos = vfuInfos.filter { it.propertyName == propertyName }
      vfuInfos.removeAll(filteredVfuInfos)
      if (vfuInfos.isEmpty()) entityId2VirtualFileUrlInfo.remove(id)

      filteredVfuInfos.forEach { vfuInfo ->
        val vfuInfos = vfu2VirtualFileUrlInfo[vfuInfo.vfu] ?: error("The record for $id <=> ${vfuInfo.vfu} should be available in both maps")
        val filteredRecords = vfuInfos.find { it.propertyName == propertyName && it.entityId == id }
        vfuInfos.remove(filteredRecords)
        if (vfuInfos.isEmpty()) vfu2VirtualFileUrlInfo.remove(vfuInfo.vfu)
      }
    }

    private fun <T> copyMap(originMap: HashMap<T, MutableSet<VirtualFileUrlInfo>>): HashMap<T, MutableSet<VirtualFileUrlInfo>>{
      val copiedMap = HashMap<T, MutableSet<VirtualFileUrlInfo>>()
      originMap.forEach{ (key, value) -> copiedMap[key] = HashSet(value) }
      return copiedMap
    }

    companion object {
      fun from(other: VirtualFileIndex): MutableVirtualFileIndex = MutableVirtualFileIndex(other.entityId2VirtualFileUrlInfo,
                                                                                           other.vfu2VirtualFileUrlInfo)
    }
  }

  internal data class VirtualFileUrlInfo(val vfu: VirtualFileUrl, val entityId: EntityId, val propertyName: String)
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
    thisRef.diff.indexes.virtualFileIndex.index(thisRef.id, property.name, listOf(value))
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
    thisRef.diff.indexes.virtualFileIndex.index(thisRef.id, property.name, value?.let { listOf(value) })
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
    thisRef.diff.indexes.virtualFileIndex.index(thisRef.id, property.name, value)
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
    thisRef.diff.indexes.virtualFileIndex.index(thisRef.id, property.name, value.map { it.url })
  }
}
