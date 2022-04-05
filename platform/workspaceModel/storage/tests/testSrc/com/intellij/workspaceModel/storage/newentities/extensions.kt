// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.newentities

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.MySource
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.newentities.api.*

fun WorkspaceEntityStorageBuilder.addParentEntity(parentProperty: String = "parent", source: EntitySource = MySource): XParentEntity {
  val parent = XParentEntity {
    this.parentProperty = parentProperty
    this.entitySource = source
    this.children = emptyList()
    this.optionalChildren = emptyList()
    this.childChild = emptyList()
  }
  this.addEntity(parent)
  return parent
}


fun WorkspaceEntityStorageBuilder.addChildEntity(parentEntity: XParentEntity,
                                                 childProperty: String = "child",
                                                 dataClass: DataClassX? = null,
                                                 source: EntitySource = MySource): XChildEntity {
  val child = XChildEntity {
    this.parentEntity = parentEntity
    this.childProperty = childProperty
    this.dataClass = dataClass
    this.entitySource = source
    this.childChild = emptyList()
  }
  this.addEntity(child)
  return child
}

fun WorkspaceEntityStorageBuilder.addChildWithOptionalParentEntity(parentEntity: XParentEntity?,
                                                                   childProperty: String = "child",
                                                                   source: EntitySource = MySource): XChildWithOptionalParentEntity {
  val child = XChildWithOptionalParentEntity {
    this.childProperty = childProperty
    this.entitySource = source
    this.optionalParent = parentEntity
  }
  this.addEntity(child)
  return child
}

fun WorkspaceEntityStorageBuilder.addChildChildEntity(parent1: XParentEntity, parent2: XChildEntity): XChildChildEntity {
  val child = XChildChildEntity {
    this.parent1 = parent1
    this.parent2 = parent2
    this.entitySource = MySource
  }
  this.addEntity(child)
  return child
}

