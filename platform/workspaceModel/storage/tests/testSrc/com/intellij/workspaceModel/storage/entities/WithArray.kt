// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.entities

import com.intellij.workspaceModel.storage.WorkspaceEntityStorage
import com.intellij.workspaceModel.storage.impl.EntityDataDelegation
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData

@Suppress("unused")
internal class WithArrayEntityData : WorkspaceEntityData<WithArrayEntity>() {
  lateinit var stringArrayProperty: Array<String>
  lateinit var info: Array<Info>

  override fun createEntity(snapshot: WorkspaceEntityStorage): WithArrayEntity {
    return WithArrayEntity(stringArrayProperty, info).also { addMetaData(it, snapshot) }
  }
}

internal class WithArrayEntity(
  val stringArrayProperty: Array<String>,
  val info: Array<Info>,
  ) : WorkspaceEntityBase()

internal class ModifiableWithArrayEntity : ModifiableWorkspaceEntityBase<WithArrayEntity>() {
  var stringArrayProperty: Array<String> by EntityDataDelegation()
  var info: Array<Info> by EntityDataDelegation()
}

data class Info(val info: String)
