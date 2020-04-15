// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage

import com.intellij.util.containers.BidirectionalMultiMap
import com.intellij.workspace.api.TypedEntity
import com.intellij.workspace.api.VirtualFileUrl
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

private const val DELIMITER = ":"

open class VirtualFileIndex private constructor(
  protected val index: BidirectionalMultiMap<VirtualFileUrl, String>
) {
  constructor() : this(BidirectionalMultiMap<VirtualFileUrl, String>())

  internal fun getVirtualFileForProperty(id: PId<TypedEntity>, propertyName: String): Set<VirtualFileUrl>? =
    index.getKeys(getIdentifier(id, propertyName))

  internal fun getIdentifier(id: PId<TypedEntity>, propertyName: String) =
    "${id.arrayId}$DELIMITER$propertyName"

  internal fun copyIndex(): BidirectionalMultiMap<VirtualFileUrl, String> {
    val copy = BidirectionalMultiMap<VirtualFileUrl, String>()
    index.keys.forEach { key -> index.getValues(key).forEach { value -> copy.put(key, value) } }
    return copy
  }

  class MutableVirtualFileIndex private constructor(
    index: BidirectionalMultiMap<VirtualFileUrl, String>
  ) : VirtualFileIndex(index) {
    constructor() : this(BidirectionalMultiMap<VirtualFileUrl, String>())

    internal fun index(id: PId<TypedEntity>, propertyName: String, virtualFileUrls: List<VirtualFileUrl>?) {
      val identifier = getIdentifier(id, propertyName)
      index.removeValue(identifier)
      if (virtualFileUrls == null) return
      virtualFileUrls.forEach { index.put(it, identifier) }
    }

    fun toImmutable(): VirtualFileIndex = VirtualFileIndex(copyIndex())

    companion object {
      fun from(other: VirtualFileIndex): MutableVirtualFileIndex = MutableVirtualFileIndex(other.copyIndex())
    }
  }
}


//---------------------------------------------------------------------
class VirtualFileUrlProperty<T : PTypedEntity> : ReadOnlyProperty<T, VirtualFileUrl> {
  override fun getValue(thisRef: T, property: KProperty<*>): VirtualFileUrl {
    val virtualFiles = thisRef.snapshot.virtualFileIndex.getVirtualFileForProperty(thisRef.id, property.name)
    if (virtualFiles == null) error("Property can't be nullable")
    if (virtualFiles.size > 1) error("Property should have only one value")
    return virtualFiles.first()
  }
}

class MutableVirtualFileUrlProperty<T : PModifiableTypedEntity<out PTypedEntity>> : ReadWriteProperty<T, VirtualFileUrl> {
  override fun getValue(thisRef: T, property: KProperty<*>): VirtualFileUrl {
    val virtualFiles = thisRef.diff.virtualFileIndex.getVirtualFileForProperty(thisRef.id, property.name)
    if (virtualFiles == null) error("Property can't be nullable")
    if (virtualFiles.size > 1) error("Property should have only one value")
    return virtualFiles.first()
  }

  override fun setValue(thisRef: T, property: KProperty<*>, value: VirtualFileUrl) {
    if (!thisRef.modifiable.get()) {
      throw IllegalStateException("Modifications are allowed inside 'addEntity' and 'modifyEntity' methods only!")
    }
    return thisRef.diff.virtualFileIndex.index(thisRef.id, property.name, listOf(value))
  }
}

//---------------------------------------------------------------------
class NullableVirtualFileUrlProperty<T : PTypedEntity> : ReadOnlyProperty<T, VirtualFileUrl?> {
  override fun getValue(thisRef: T, property: KProperty<*>): VirtualFileUrl? {
    val virtualFiles = thisRef.snapshot.virtualFileIndex.getVirtualFileForProperty(thisRef.id, property.name)
    if (virtualFiles == null) return null
    if (virtualFiles.size > 1) error("Property should have only one value")
    if (virtualFiles.isEmpty()) return null
    return virtualFiles.first()
  }
}

class MutableNullableVirtualFileUrlProperty<T : PModifiableTypedEntity<out PTypedEntity>> : ReadWriteProperty<T, VirtualFileUrl?> {
  override fun getValue(thisRef: T, property: KProperty<*>): VirtualFileUrl? {
    val virtualFiles = thisRef.diff.virtualFileIndex.getVirtualFileForProperty(thisRef.id, property.name)
    if (virtualFiles == null) return null
    if (virtualFiles.size > 1) error("Property should have only one value")
    if (virtualFiles.isEmpty()) return null
    return virtualFiles.first()
  }

  override fun setValue(thisRef: T, property: KProperty<*>, value: VirtualFileUrl?) {
    if (!thisRef.modifiable.get()) {
      throw IllegalStateException("Modifications are allowed inside 'addEntity' and 'modifyEntity' methods only!")
    }
    return thisRef.diff.virtualFileIndex.index(thisRef.id, property.name, value?.let{ listOf(value) })
  }
}

//---------------------------------------------------------------------
class VirtualFileUrlListProperty<T : PTypedEntity> : ReadOnlyProperty<T, List<VirtualFileUrl>> {
  override fun getValue(thisRef: T, property: KProperty<*>): List<VirtualFileUrl> {
    val virtualFiles = thisRef.snapshot.virtualFileIndex.getVirtualFileForProperty(thisRef.id, property.name)
    if (virtualFiles == null) error("Property can't be nullable")
    return virtualFiles.toList()
  }
}

class MutableVirtualFileUrlListProperty<T : PModifiableTypedEntity<out PTypedEntity>> : ReadWriteProperty<T, List<VirtualFileUrl>> {
  override fun getValue(thisRef: T, property: KProperty<*>): List<VirtualFileUrl> {
    val virtualFiles = thisRef.diff.virtualFileIndex.getVirtualFileForProperty(thisRef.id, property.name)
    if (virtualFiles == null) error("Property can't be nullable")
    return virtualFiles.toList()
  }

  override fun setValue(thisRef: T, property: KProperty<*>, value: List<VirtualFileUrl>) {
    if (!thisRef.modifiable.get()) {
      throw IllegalStateException("Modifications are allowed inside 'addEntity' and 'modifyEntity' methods only!")
    }
    return thisRef.diff.virtualFileIndex.index(thisRef.id, property.name, value)
  }
}