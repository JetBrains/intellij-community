// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.entities

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.impl.EntityDataDelegation
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import com.intellij.workspaceModel.storage.impl.references.MutableOneToAbstractMany
import com.intellij.workspaceModel.storage.impl.references.OneToAbstractMany

// ---------------------------

internal abstract class BaseEntity : WorkspaceEntityBase()

internal abstract class ModifiableBaseEntity<T : BaseEntity> : ModifiableWorkspaceEntityBase<T>()

// ---------------------------

internal abstract class CompositeBaseEntity : BaseEntity() {
  val children: Sequence<BaseEntity> by OneToAbstractMany(BaseEntity::class.java)
}

internal abstract class ModifiableCompositeBaseEntity<T : CompositeBaseEntity>(clazz: Class<T>) : ModifiableWorkspaceEntityBase<T>() {
  var children: Sequence<BaseEntity> by MutableOneToAbstractMany(clazz, BaseEntity::class.java)
}

// ---------------------------

internal class MiddleEntityData : WorkspaceEntityData<MiddleEntity>() {

  lateinit var property: String

  override fun createEntity(snapshot: WorkspaceEntityStorage): MiddleEntity {
    return MiddleEntity(property).also { addMetaData(it, snapshot) }
  }
}

internal class MiddleEntity(val property: String) : BaseEntity()

internal class ModifiableMiddleEntity : ModifiableBaseEntity<MiddleEntity>() {
  var property: String by EntityDataDelegation()
}

internal fun WorkspaceEntityStorageBuilder.addMiddleEntity(property: String = "prop", source: EntitySource = MySource): MiddleEntity {
  return addEntity(ModifiableMiddleEntity::class.java, source) {
    this.property = property
  }
}

// ---------------------------

internal class LeftEntityData : WorkspaceEntityData<LeftEntity>() {
  override fun createEntity(snapshot: WorkspaceEntityStorage): LeftEntity {
    return LeftEntity().also { addMetaData(it, snapshot) }
  }
}

internal class LeftEntity : CompositeBaseEntity()

internal class ModifiableLeftEntity : ModifiableCompositeBaseEntity<LeftEntity>(LeftEntity::class.java)

internal fun WorkspaceEntityStorageBuilder.addLeftEntity(children: Sequence<BaseEntity>, source: EntitySource = MySource): LeftEntity {
  return addEntity(ModifiableLeftEntity::class.java, source) {
    this.children = children
  }
}

// ---------------------------

internal class RightEntityData : WorkspaceEntityData<RightEntity>() {
  override fun createEntity(snapshot: WorkspaceEntityStorage): RightEntity {
    return RightEntity().also { addMetaData(it, snapshot) }
  }
}

internal class RightEntity : CompositeBaseEntity()

internal class ModifiableRightEntity : ModifiableCompositeBaseEntity<RightEntity>(RightEntity::class.java)

internal fun WorkspaceEntityStorageBuilder.addRightEntity(children: Sequence<BaseEntity>, source: EntitySource = MySource): RightEntity {
  return addEntity(ModifiableRightEntity::class.java, source) {
    this.children = children
  }
}
