// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId


data class LinkedListEntityId(val name: String) : SymbolicEntityId<LinkedListEntity> {
  override val presentableName: String
    get() = name
}

interface LinkedListEntity : WorkspaceEntityWithSymbolicId {
  val myName: String
  val next: LinkedListEntityId

  override val symbolicId: LinkedListEntityId
    get() = LinkedListEntityId(myName)

}
