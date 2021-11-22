// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.entities

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.MySource
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.impl.EntityDataDelegation
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import com.intellij.workspaceModel.storage.impl.references.MutableOneToAbstractMany
import com.intellij.workspaceModel.storage.impl.references.OneToAbstractMany

// ---------------------------

abstract class BaseEntity : WorkspaceEntityBase()

abstract class ModifiableBaseEntity<T : BaseEntity> : ModifiableWorkspaceEntityBase<T>()

// ---------------------------

abstract class CompositeBaseEntity : BaseEntity() {
  val children: Sequence<BaseEntity> by OneToAbstractMany(BaseEntity::class.java)
}

abstract class ModifiableCompositeBaseEntity<T : CompositeBaseEntity>(clazz: Class<T>) : ModifiableWorkspaceEntityBase<T>() {
  var children: Sequence<BaseEntity> by MutableOneToAbstractMany(clazz, BaseEntity::class.java)
}

// ---------------------------

class MiddleEntityData : WorkspaceEntityData<MiddleEntity>() {

  lateinit var property: String

  override fun createEntity(snapshot: WorkspaceEntityStorage): MiddleEntity {
    return MiddleEntity(property).also { addMetaData(it, snapshot) }
  }
}

class MiddleEntity(val property: String) : BaseEntity()

class ModifiableMiddleEntity : ModifiableBaseEntity<MiddleEntity>() {
  var property: String by EntityDataDelegation()
}

fun WorkspaceEntityStorageBuilder.addMiddleEntity(property: String = "prop", source: EntitySource = MySource): MiddleEntity {
  return addEntity(ModifiableMiddleEntity::class.java, source) {
    this.property = property
  }
}

// ---------------------------

class LeftEntityData : WorkspaceEntityData<LeftEntity>() {
  override fun createEntity(snapshot: WorkspaceEntityStorage): LeftEntity {
    return LeftEntity().also { addMetaData(it, snapshot) }
  }
}

class LeftEntity : CompositeBaseEntity()

class ModifiableLeftEntity : ModifiableCompositeBaseEntity<LeftEntity>(LeftEntity::class.java)

fun WorkspaceEntityStorageBuilder.addLeftEntity(children: Sequence<BaseEntity>, source: EntitySource = MySource): LeftEntity {
  return addEntity(ModifiableLeftEntity::class.java, source) {
    this.children = children
  }
}

// ---------------------------

class RightEntityData : WorkspaceEntityData<RightEntity>() {
  override fun createEntity(snapshot: WorkspaceEntityStorage): RightEntity {
    return RightEntity().also { addMetaData(it, snapshot) }
  }
}

class RightEntity : CompositeBaseEntity()

class ModifiableRightEntity : ModifiableCompositeBaseEntity<RightEntity>(RightEntity::class.java)

fun WorkspaceEntityStorageBuilder.addRightEntity(children: Sequence<BaseEntity>, source: EntitySource = MySource): RightEntity {
  return addEntity(ModifiableRightEntity::class.java, source) {
    this.children = children
  }
}
