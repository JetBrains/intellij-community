// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId
import com.intellij.platform.workspace.storage.annotations.Parent

interface PlaceholderEntity : WorkspaceEntity {
  val myId: String
}

interface GrandParentWithId : WorkspaceEntityWithSymbolicId {

  val myId: String

  override val symbolicId: GrandParentId
    get() = GrandParentId(myId)

  data class GrandParentId(val id: String) : SymbolicEntityId<GrandParentWithId> {
    override val presentableName: @NlsSafe String = id
  }
}

interface ParentWithId : WorkspaceEntityWithSymbolicId {

  val myId: String

  @Parent
  val parent: GrandParentWithId

  val children: List<ChildWithId>

  override val symbolicId: ParentId
    get() = ParentId(myId)

  data class ParentId(val id: String) : SymbolicEntityId<ParentWithId> {
    override val presentableName: @NlsSafe String = id
  }
}

val GrandParentWithId.children: List<ParentWithId>
  by WorkspaceEntity.extension()

interface ChildWithId : WorkspaceEntityWithSymbolicId {

  val myId: String

  @Parent
  val parent: ParentWithId

  override val symbolicId: ChildId
    get() = ChildId(myId)

  data class ChildId(val id: String) : SymbolicEntityId<ChildWithId> {
    override val presentableName: @NlsSafe String = id
  }
}

//val ParentWithId.children: List<ChildWithId>
//  by WorkspaceEntity.extension()
