// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage

import com.intellij.workspaceModel.storage.impl.EntityDataDelegation
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData

data class LinkedListEntityId(val name: String) : PersistentEntityId<LinkedListEntity>() {
  override val parentId: PersistentEntityId<*>?
    get() = null
  override val presentableName: String
    get() = name
}

@Suppress("unused")
class LinkedListEntityData : WorkspaceEntityData.WithCalculablePersistentId<LinkedListEntity>() {
  lateinit var name: String
  lateinit var next: LinkedListEntityId

  override fun createEntity(snapshot: WorkspaceEntityStorage): LinkedListEntity {
    return LinkedListEntity(name, next).also { addMetaData(it, snapshot) }
  }

  override fun persistentId(): LinkedListEntityId = LinkedListEntityId(name)
}

class LinkedListEntity(
  val name: String,
  val next: LinkedListEntityId
) : WorkspaceEntityWithPersistentId, WorkspaceEntityBase() {
  override fun persistentId(): LinkedListEntityId = LinkedListEntityId(name)
}

class ModifiableLinkedListEntity : ModifiableWorkspaceEntityBase<LinkedListEntity>() {
  var name: String by EntityDataDelegation()
  var next: LinkedListEntityId by EntityDataDelegation()
}

fun WorkspaceEntityStorageBuilder.addLinkedListEntity(name: String, next: LinkedListEntityId) =
  addEntity(ModifiableLinkedListEntity::class.java, SampleEntitySource("test")) {
    this.name = name
    this.next = next
  }