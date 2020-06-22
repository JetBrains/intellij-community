// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl.indices

import com.intellij.util.containers.BidirectionalMultiMap
import com.intellij.workspaceModel.storage.VirtualFileUrl
import com.intellij.workspaceModel.storage.impl.EntityId
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.containers.copy
import com.intellij.workspaceModel.storage.impl.containers.putAll
import org.jetbrains.annotations.TestOnly
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties

open class VirtualFileIndex private constructor(
  internal open val index: BidirectionalMultiMap<Pair<VirtualFileUrl, String>, EntityId>
) {
  constructor() : this(BidirectionalMultiMap<Pair<VirtualFileUrl, String>, EntityId>())

  internal fun getVirtualFiles(id: EntityId): Set<VirtualFileUrl>? =
    index.getKeys(id).asSequence().map { it.first }.toSet()

  internal fun getVirtualFilesPerProperty(id: EntityId): Set<Pair<VirtualFileUrl, String>>? =
    index.getKeys(id)

  class MutableVirtualFileIndex private constructor(
    // Do not write to [index] directly! Create a method in this index and call [startWrite] before write.
    override var index: BidirectionalMultiMap<Pair<VirtualFileUrl, String>, EntityId>
  ) : VirtualFileIndex(index) {

    private var freezed = true

    internal fun index(id: EntityId, propertyName: String? = null, virtualFileUrls: List<VirtualFileUrl>? = null) {
      startWrite()
      if (propertyName == null) {
        index.removeValue(id)
        return
      }
      index.getKeys(id).forEach { if (it.second == propertyName) index.remove(it, id) }
      if (virtualFileUrls == null) return
      virtualFileUrls.forEach { index.put(it to propertyName, id) }
    }

    @TestOnly
    internal fun clear() {
      startWrite()
      index.clear()
    }

    @TestOnly
    internal fun copyFrom(another: VirtualFileIndex) {
      startWrite()
      index.putAll(another.index)
    }

    private fun startWrite() {
      if (!freezed) return
      freezed = false
      index = copyIndex()
    }

    private fun copyIndex(): BidirectionalMultiMap<Pair<VirtualFileUrl, String>, EntityId> = index.copy()

    fun toImmutable(): VirtualFileIndex {
      freezed = true
      return VirtualFileIndex(index)
    }

    companion object {
      fun from(other: VirtualFileIndex): MutableVirtualFileIndex = MutableVirtualFileIndex(other.index)
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