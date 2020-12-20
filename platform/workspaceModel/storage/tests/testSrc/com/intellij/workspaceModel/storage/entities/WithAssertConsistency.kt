// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.entities

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.impl.*

// ------------------- Entity with consistency assertion --------------------------------

@Suppress("unused")
internal class AssertConsistencyEntityData : WorkspaceEntityData<AssertConsistencyEntity>(), WithAssertableConsistency {

  var passCheck: Boolean = false

  override fun createEntity(snapshot: WorkspaceEntityStorage): AssertConsistencyEntity {
    return AssertConsistencyEntity(passCheck).also { addMetaData(it, snapshot) }
  }

  override fun assertConsistency(storage: WorkspaceEntityStorage) {
    assert(passCheck)
  }
}

internal class AssertConsistencyEntity(val passCheck: Boolean) : WorkspaceEntityBase()

internal class ModifiableAssertConsistencyEntity : ModifiableWorkspaceEntityBase<AssertConsistencyEntity>() {
  var passCheck: Boolean by EntityDataDelegation()
}

internal fun WorkspaceEntityStorageBuilder.addAssertConsistencyEntity(passCheck: Boolean, source: EntitySource = MySource) =
  addEntity(ModifiableAssertConsistencyEntity::class.java, source) {
    this.passCheck = passCheck
  }
