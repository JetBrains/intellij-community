// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage.indices

import com.intellij.util.containers.BidirectionalMultiMap
import com.intellij.workspace.api.VirtualFileUrl
import com.intellij.workspace.api.pstorage.PId
import com.intellij.workspace.api.pstorage.PModifiableTypedEntity
import com.intellij.workspace.api.pstorage.PTypedEntity
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties

open class VirtualFileIndex private constructor(
  internal open val index: BidirectionalMultiMap<VirtualFileUrl, PId>
) {
  constructor() : this(BidirectionalMultiMap<VirtualFileUrl, PId>())

  internal fun getVirtualFiles(id: PId): Set<VirtualFileUrl>? =
    index.getKeys(id)

  class MutableVirtualFileIndex private constructor(
    override var index: BidirectionalMultiMap<VirtualFileUrl, PId>
  ) : VirtualFileIndex(index) {

    private var freezed = true

    internal fun index(id: PId, virtualFileUrls: List<VirtualFileUrl>? = null) {
      startWrite()
      index.removeValue(id)
      if (virtualFileUrls == null) return
      virtualFileUrls.forEach { index.put(it, id) }
    }

    private fun startWrite() {
      if (!freezed) return
      freezed = false
      index = copyIndex()
    }

    private fun copyIndex(): BidirectionalMultiMap<VirtualFileUrl, PId> {
      val copy = BidirectionalMultiMap<VirtualFileUrl, PId>()
      index.keys.forEach { key -> index.getValues(key).forEach { value -> copy.put(key, value) } }
      return copy
    }

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
class VirtualFileUrlProperty<T : PModifiableTypedEntity<out PTypedEntity>> : ReadWriteProperty<T, VirtualFileUrl> {
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
    thisRef.diff.indexes.virtualFileIndex.index(thisRef.id, listOf(value))
  }
}

//---------------------------------------------------------------------
class VirtualFileUrlNullableProperty<T : PModifiableTypedEntity<out PTypedEntity>> : ReadWriteProperty<T, VirtualFileUrl?> {
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
    thisRef.diff.indexes.virtualFileIndex.index(thisRef.id, value?.let { listOf(value) })
  }
}

//---------------------------------------------------------------------
class VirtualFileUrlListProperty<T : PModifiableTypedEntity<out PTypedEntity>> : ReadWriteProperty<T, List<VirtualFileUrl>> {
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
    thisRef.diff.indexes.virtualFileIndex.index(thisRef.id, value)
  }
}