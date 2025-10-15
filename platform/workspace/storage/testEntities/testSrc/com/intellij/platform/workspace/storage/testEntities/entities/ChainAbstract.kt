// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Abstract
import com.intellij.platform.workspace.storage.annotations.Parent


interface ParentChainEntity : WorkspaceEntity {
  val root: CompositeAbstractEntity?

}

@Abstract
interface SimpleAbstractEntity : WorkspaceEntity {
  @Parent
  val parentInList: CompositeAbstractEntity?

}

@Abstract
interface CompositeAbstractEntity : SimpleAbstractEntity {
  val children: List<SimpleAbstractEntity>

  @Parent
  val parentEntity: ParentChainEntity?

}

interface CompositeChildAbstractEntity : CompositeAbstractEntity {

}

interface SimpleChildAbstractEntity : SimpleAbstractEntity {

}
