// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage.indices

import com.intellij.util.containers.BidirectionalMultiMap
import com.intellij.workspace.api.TypedEntity
import com.intellij.workspace.api.VirtualFileUrl
import com.intellij.workspace.api.pstorage.PId
import com.intellij.workspace.api.pstorage.PModifiableTypedEntity
import com.intellij.workspace.api.pstorage.PTypedEntity
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties

open class VirtualFileIndex private constructor(
  protected val index: BidirectionalMultiMap<VirtualFileUrl, Int>
) {
  constructor() : this(BidirectionalMultiMap<VirtualFileUrl, Int>())

  internal fun getVirtualFiles(id: PId<out TypedEntity>): Set<VirtualFileUrl>? =
    index.getKeys(id.arrayId)

  internal fun copyIndex(): BidirectionalMultiMap<VirtualFileUrl, Int> {
    val copy = BidirectionalMultiMap<VirtualFileUrl, Int>()
    index.keys.forEach { key -> index.getValues(key).forEach { value -> copy.put(key, value) } }
    return copy
  }

  class MutableVirtualFileIndex private constructor(
    index: BidirectionalMultiMap<VirtualFileUrl, Int>
  ) : VirtualFileIndex(index) {
    constructor() : this(BidirectionalMultiMap<VirtualFileUrl, Int>())

    internal fun index(id: PId<out TypedEntity>, virtualFileUrls: List<VirtualFileUrl>? = null) {
      index.removeValue(id.arrayId)
      if (virtualFileUrls == null) return
      virtualFileUrls.forEach { index.put(it, id.arrayId) }
    }

    fun toImmutable(): VirtualFileIndex = VirtualFileIndex(copyIndex())

    companion object {
      fun from(other: VirtualFileIndex): MutableVirtualFileIndex = MutableVirtualFileIndex(other.copyIndex())
    }
  }
}


//---------------------------------------------------------------------
class VirtualFileUrlProperty<T : PModifiableTypedEntity<out PTypedEntity>> : ReadWriteProperty<T, VirtualFileUrl> {
  override fun getValue(thisRef: T, property: KProperty<*>): VirtualFileUrl {
    return ((thisRef.original::class.memberProperties.first { it.name == property.name }) as KProperty1<Any, *>).get(thisRef.original) as VirtualFileUrl
  }

  override fun setValue(thisRef: T, property: KProperty<*>, value: VirtualFileUrl) {
    if (!thisRef.modifiable.get()) {
      throw IllegalStateException("Modifications are allowed inside 'addEntity' and 'modifyEntity' methods only!")
    }
    ((thisRef.original::class.memberProperties.first { it.name == property.name }) as KMutableProperty<*>).setter.call(thisRef.original, value)
    thisRef.diff.virtualFileIndex.index(thisRef.id, listOf(value))
  }
}

//---------------------------------------------------------------------
class VirtualFileUrlNullableProperty<T : PModifiableTypedEntity<out PTypedEntity>> : ReadWriteProperty<T, VirtualFileUrl?> {
  override fun getValue(thisRef: T, property: KProperty<*>): VirtualFileUrl? {
    return ((thisRef.original::class.memberProperties.first { it.name == property.name }) as KProperty1<Any, *>).get(thisRef.original) as VirtualFileUrl?
  }

  override fun setValue(thisRef: T, property: KProperty<*>, value: VirtualFileUrl?) {
    if (!thisRef.modifiable.get()) {
      throw IllegalStateException("Modifications are allowed inside 'addEntity' and 'modifyEntity' methods only!")
    }
    ((thisRef.original::class.memberProperties.first { it.name == property.name }) as KMutableProperty<*>).setter.call(thisRef.original, value)
    thisRef.diff.virtualFileIndex.index(thisRef.id, value?.let{ listOf(value) })
  }
}

//---------------------------------------------------------------------
class VirtualFileUrlListProperty<T : PModifiableTypedEntity<out PTypedEntity>> : ReadWriteProperty<T, List<VirtualFileUrl>> {
  override fun getValue(thisRef: T, property: KProperty<*>): List<VirtualFileUrl> {
    return ((thisRef.original::class.memberProperties.first { it.name == property.name }) as KProperty1<Any, *>).get(thisRef.original) as List<VirtualFileUrl>
  }

  override fun setValue(thisRef: T, property: KProperty<*>, value: List<VirtualFileUrl>) {
    if (!thisRef.modifiable.get()) {
      throw IllegalStateException("Modifications are allowed inside 'addEntity' and 'modifyEntity' methods only!")
    }
    ((thisRef.original::class.memberProperties.first { it.name == property.name }) as KMutableProperty<*>).setter.call(thisRef.original, value)
    thisRef.diff.virtualFileIndex.index(thisRef.id, value)
  }
}