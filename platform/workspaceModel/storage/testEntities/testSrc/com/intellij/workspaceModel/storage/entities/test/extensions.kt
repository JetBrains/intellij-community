// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.entities.test

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.entities.test.api.*
import com.intellij.workspaceModel.storage.impl.url.VirtualFileUrlManagerImpl
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager

fun MutableEntityStorage.addParentEntity(parentProperty: String = "parent", source: EntitySource = MySource): XParentEntity {
  val parent = XParentEntity(parentProperty, source) {
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
  val child = XChildEntity(childProperty, source) {
    this.parentEntity = parentEntity
    this.dataClass = dataClass
    this.childChild = emptyList()
  }
  this.addEntity(child)
  return child
}

fun MutableEntityStorage.addChildWithOptionalParentEntity(parentEntity: XParentEntity?,
                                                          childProperty: String = "child",
                                                          source: EntitySource = MySource): XChildWithOptionalParentEntity {
  val child = XChildWithOptionalParentEntity(childProperty, source) {
    this.optionalParent = parentEntity
  }
  this.addEntity(child)
  return child
}

fun MutableEntityStorage.addChildChildEntity(parent1: XParentEntity, parent2: XChildEntity): XChildChildEntity {
  val child = XChildChildEntity(MySource) {
    this.parent1 = parent1
    this.parent2 = parent2
  }
  this.addEntity(child)
  return child
}

fun MutableEntityStorage.addChildSampleEntity(
  stringProperty: String,
  parent: SampleEntity?,
  source: EntitySource = SampleEntitySource("test")
): ChildSampleEntity {
  val entity = ChildSampleEntity(stringProperty, source) {
    this.parentEntity = parent
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
                                         info: String = "",
                                         stringMapProperty: MutableMap<String, String> = HashMap(),
): SampleEntity {
  val entity = SampleEntity(booleanProperty, stringProperty, stringListProperty, stringMapProperty, fileProperty, source) {
    this.children = emptyList()
  }
  this.addEntity(entity)
  return entity
}

fun MutableEntityStorage.addSourceEntity(data: String,
                                         source: EntitySource): SourceEntity {
  val entity = SourceEntity(data, source) {
    this.children = emptyList()
  }
  this.addEntity(entity)
  return entity
}

fun MutableEntityStorage.addSymbolicIdEntity(
  data: String,
  source: EntitySource = SampleEntitySource("test")
): SymbolicIdEntity {
  val entity = SymbolicIdEntity(data, source)
  this.addEntity(entity)
  return entity
}
