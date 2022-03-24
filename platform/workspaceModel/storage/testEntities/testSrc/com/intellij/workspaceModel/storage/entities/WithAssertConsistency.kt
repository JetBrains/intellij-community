// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage

import com.intellij.workspaceModel.storage.impl.*

// ------------------- Entity with consistency assertion --------------------------------

@Suppress("unused")
class AssertConsistencyEntityData : WorkspaceEntityData<AssertConsistencyEntity>(), WithAssertableConsistency {

  var passCheck: Boolean = false

  override fun createEntity(snapshot: WorkspaceEntityStorage): AssertConsistencyEntity {
    return AssertConsistencyEntity(passCheck).also { addMetaData(it, snapshot) }
  }

  override fun assertConsistency(storage: WorkspaceEntityStorage) {
    assert(passCheck)
  }
}

class AssertConsistencyEntity(val passCheck: Boolean) : WorkspaceEntityBase()

class ModifiableAssertConsistencyEntity : ModifiableWorkspaceEntityBase<AssertConsistencyEntity>() {
  var passCheck: Boolean by EntityDataDelegation()
}

fun WorkspaceEntityStorageBuilder.addAssertConsistencyEntity(passCheck: Boolean, source: EntitySource = MySource) =
  addEntity(ModifiableAssertConsistencyEntity::class.java, source) {
    this.passCheck = passCheck
  }
