// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Parent


interface XParentEntity : WorkspaceEntity {
  val parentProperty: String

  val children: List<XChildEntity>
  val optionalChildren: List<XChildWithOptionalParentEntity>
  val childChild: List<XChildChildEntity>

}

data class DataClassX(val stringProperty: String, val parent: EntityPointer<XParentEntity>)

interface XChildEntity : WorkspaceEntity {
  val childProperty: String
  val dataClass: DataClassX?

  @Parent
  val parentEntity: XParentEntity

  val childChild: List<XChildChildEntity>

}

interface XChildWithOptionalParentEntity : WorkspaceEntity {
  val childProperty: String
  @Parent
  val optionalParent: XParentEntity?

}

interface XChildChildEntity : WorkspaceEntity {
  @Parent
  val parent1: XParentEntity
  @Parent
  val parent2: XChildEntity

}
