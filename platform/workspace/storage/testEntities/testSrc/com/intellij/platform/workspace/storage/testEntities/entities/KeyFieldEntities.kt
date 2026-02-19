// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EqualsBy
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Parent


interface KeyParent : WorkspaceEntity {
  @EqualsBy
  val keyField: String
  val notKeyField: String
  val children: List<KeyChild>

}

interface KeyChild : WorkspaceEntity {
  val data: String

  @Parent
  val parentEntity : KeyParent

}
