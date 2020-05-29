// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage.entities

import com.intellij.workspace.api.PersistentEntityId
import com.intellij.workspace.api.TypedEntityStorage
import com.intellij.workspace.api.TypedEntityWithPersistentId
import com.intellij.workspace.api.pstorage.*

internal data class LinkedListEntityId(val name: String) : PersistentEntityId<LinkedListEntity>() {
  override val parentId: PersistentEntityId<*>?
    get() = null
  override val presentableName: String
    get() = name
}

@Suppress("unused")
internal class LinkedListEntityData : PEntityData.WithCalculatablePersistentId<LinkedListEntity>() {
  lateinit var name: String
  lateinit var next: LinkedListEntityId

  override fun createEntity(snapshot: TypedEntityStorage): LinkedListEntity {
    return LinkedListEntity(name, next).also { addMetaData(it, snapshot) }
  }

  override fun persistentId(): LinkedListEntityId = LinkedListEntityId(name)
}

internal class LinkedListEntity(
  val name: String,
  val next: LinkedListEntityId
) : TypedEntityWithPersistentId, PTypedEntity() {
  override fun persistentId(): LinkedListEntityId = LinkedListEntityId(
    name)

}

internal class ModifiableLinkedListEntity : PModifiableTypedEntity<LinkedListEntity>() {
  var name: String by EntityDataDelegation()
  var next: LinkedListEntityId by EntityDataDelegation()
}

internal fun PEntityStorageBuilder.addLinkedListEntity(name: String, next: LinkedListEntityId) =
  addEntity(ModifiableLinkedListEntity::class.java, PSampleEntitySource("test")) {
    this.name = name
    this.next = next
  }