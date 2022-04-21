// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.entities.test

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.entities.test.api.*
import com.intellij.workspaceModel.storage.impl.url.VirtualFileUrlManagerImpl
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager

fun MutableEntityStorage.addParentEntity(parentProperty: String = "parent", source: EntitySource = MySource): XParentEntity {
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


fun MutableEntityStorage.addChildEntity(parentEntity: XParentEntity,
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

fun MutableEntityStorage.addChildWithOptionalParentEntity(parentEntity: XParentEntity?,
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

fun MutableEntityStorage.addChildChildEntity(parent1: XParentEntity, parent2: XChildEntity): XChildChildEntity {
  val child = XChildChildEntity {
    this.parent1 = parent1
    this.parent2 = parent2
    this.entitySource = MySource
  }
  this.addEntity(child)
  return child
}

fun MutableEntityStorage.addChildSampleEntity(
  stringProperty: String,
  parent: SampleEntity?,
  source: EntitySource = SampleEntitySource("test")
): ChildSampleEntity {
  val entity = ChildSampleEntity {
    this.data = stringProperty
    this.parentEntity = parent
    this.entitySource = source
  }
  this.addEntity(entity)
  return entity
}

fun MutableEntityStorage.addSampleEntity(stringProperty: String,
                                         source: EntitySource = SampleEntitySource("test"),
                                         booleanProperty: Boolean = false,
                                         stringListProperty: MutableList<String> = ArrayList(),
                                         stringSetProperty: MutableSet<String> = LinkedHashSet(),
                                         virtualFileManager: VirtualFileUrlManager = VirtualFileUrlManagerImpl(),
                                         fileProperty: VirtualFileUrl = virtualFileManager.fromUrl("file:///tmp"),
                                         info: String = ""
): SampleEntity {
  val entity = SampleEntity {
    this.booleanProperty = booleanProperty
    this.stringProperty = stringProperty
    this.stringListProperty = stringListProperty
    this.fileProperty = fileProperty
    this.entitySource = source
    this.children = emptyList()
  }
  this.addEntity(entity)
  return entity
}

fun MutableEntityStorage.addSourceEntity(data: String,
                                         source: EntitySource): SourceEntity {
  val entity = SourceEntity {
    this.children = emptyList()
    this.data = data
    this.entitySource = source
  }
  this.addEntity(entity)
  return entity
}

fun MutableEntityStorage.addPersistentIdEntity(
  data: String,
  source: EntitySource = SampleEntitySource("test")
): PersistentIdEntity {
  val entity = PersistentIdEntity {
    this.data = data
    this.entitySource = source
  }
  this.addEntity(entity)
  return entity
}
