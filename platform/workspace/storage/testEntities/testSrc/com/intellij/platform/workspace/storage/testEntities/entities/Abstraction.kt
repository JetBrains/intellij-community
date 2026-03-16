// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId
import com.intellij.platform.workspace.storage.annotations.Abstract
import com.intellij.platform.workspace.storage.annotations.Parent


interface HeadAbstractionEntity : WorkspaceEntityWithSymbolicId {
  val data: String
  val child: CompositeBaseEntity?

  override val symbolicId: HeadAbstractionSymbolicId
    get() = HeadAbstractionSymbolicId(data)
}

data class HeadAbstractionSymbolicId(override val presentableName: String) : SymbolicEntityId<HeadAbstractionEntity>


@Abstract
interface BaseEntity : WorkspaceEntity {
  @Parent
  val parentEntity: CompositeBaseEntity?

}

@Abstract
interface CompositeBaseEntity : BaseEntity {
  val children: List<BaseEntity>

  @Parent
  val parent: HeadAbstractionEntity?

}

interface MiddleEntity : BaseEntity {
  val property: String

}

// ---------------------------

interface LeftEntity : CompositeBaseEntity {

}

// ---------------------------

interface RightEntity : CompositeBaseEntity {

}
