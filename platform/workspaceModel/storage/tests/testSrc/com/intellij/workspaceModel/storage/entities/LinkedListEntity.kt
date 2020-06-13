// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.entities

import com.intellij.workspaceModel.storage.PersistentEntityId
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntityWithPersistentId
import com.intellij.workspaceModel.storage.impl.*

internal data class LinkedListEntityId(val name: String) : PersistentEntityId<LinkedListEntity>() {
  override val parentId: PersistentEntityId<*>?
    get() = null
  override val presentableName: String
    get() = name
}

@Suppress("unused")
internal class LinkedListEntityData : WorkspaceEntityData.WithCalculablePersistentId<LinkedListEntity>() {
  lateinit var name: String
  lateinit var next: LinkedListEntityId

  override fun createEntity(snapshot: WorkspaceEntityStorage): LinkedListEntity {
    return LinkedListEntity(name, next).also { addMetaData(it, snapshot) }
  }

  override fun persistentId(): LinkedListEntityId = LinkedListEntityId(name)
}

internal class LinkedListEntity(
  val name: String,
  val next: LinkedListEntityId
) : WorkspaceEntityWithPersistentId, WorkspaceEntityBase() {
  override fun persistentId(): LinkedListEntityId = LinkedListEntityId(name)
}

internal class ModifiableLinkedListEntity : ModifiableWorkspaceEntityBase<LinkedListEntity>() {
  var name: String by EntityDataDelegation()
  var next: LinkedListEntityId by EntityDataDelegation()
}

internal fun WorkspaceEntityStorageBuilderImpl.addLinkedListEntity(name: String, next: LinkedListEntityId) =
  addEntity(ModifiableLinkedListEntity::class.java, SampleEntitySource("test")) {
    this.name = name
    this.next = next
  }