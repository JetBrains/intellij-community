// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.entities

import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.impl.EntityDataDelegation
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import com.intellij.workspaceModel.storage.impl.references.MutableOneToOneChild
import com.intellij.workspaceModel.storage.impl.references.MutableOneToOneParent
import com.intellij.workspaceModel.storage.impl.references.OneToOneChild
import com.intellij.workspaceModel.storage.impl.references.OneToOneParent

// ------------------- Parent Entity --------------------------------

internal class OoParentEntityData : WorkspaceEntityData<OoParentEntity>() {

  lateinit var parentProperty: String

  override fun createEntity(snapshot: WorkspaceEntityStorage): OoParentEntity {
    return OoParentEntity(parentProperty).also { addMetaData(it, snapshot) }
  }
}

internal class OoParentEntity(val parentProperty: String) : WorkspaceEntityBase() {

  val child: OoChildEntity? by OneToOneParent.Nullable<OoParentEntity, OoChildEntity>(OoChildEntity::class.java, false)
}

internal class ModifiableOoParentEntity : ModifiableWorkspaceEntityBase<OoParentEntity>() {
  var parentProperty: String by EntityDataDelegation()
  var child: OoChildEntity? by MutableOneToOneParent.Nullable(OoParentEntity::class.java, OoChildEntity::class.java, false)
}

internal fun WorkspaceEntityStorageBuilder.addOoParentEntity(parentProperty: String = "parent", source: EntitySource = MySource): OoParentEntity {
  return addEntity(ModifiableOoParentEntity::class.java, source) {
    this.parentProperty = parentProperty
  }
}

// ---------------- Child entity ----------------------

internal class OoChildEntityData : WorkspaceEntityData<OoChildEntity>() {
  lateinit var childProperty: String
  override fun createEntity(snapshot: WorkspaceEntityStorage): OoChildEntity {
    return OoChildEntity(childProperty).also { addMetaData(it, snapshot) }
  }
}

internal class OoChildEntity(val childProperty: String) : WorkspaceEntityBase() {
  val parent: OoParentEntity by OneToOneChild.NotNull(OoParentEntity::class.java, true)
}

internal class ModifiableOoChildEntity : ModifiableWorkspaceEntityBase<OoChildEntity>() {
  var childProperty: String by EntityDataDelegation()
  var parent: OoParentEntity by MutableOneToOneChild.NotNull(OoChildEntity::class.java, OoParentEntity::class.java, true)
}


internal fun WorkspaceEntityStorageBuilder.addOoChildEntity(OoParentEntity: OoParentEntity,
                                                            childProperty: String = "child",
                                                            source: EntitySource = MySource) =
  addEntity(ModifiableOoChildEntity::class.java, source) {
    this.parent = OoParentEntity
    this.childProperty = childProperty
  }

// ------------------- Parent Entity with PersistentId --------------------------------

internal data class OoParentEntityId(val name: String) : PersistentEntityId<OoParentWithPidEntity>() {
  override val parentId: PersistentEntityId<*>?
    get() = null
  override val presentableName: String
    get() = name
}

internal class OoParentWithPidEntityData : WorkspaceEntityData.WithCalculablePersistentId<OoParentWithPidEntity>() {

  lateinit var parentProperty: String

  override fun createEntity(snapshot: WorkspaceEntityStorage): OoParentWithPidEntity {
    return OoParentWithPidEntity(parentProperty).also { addMetaData(it, snapshot) }
  }

  override fun persistentId(): OoParentEntityId = OoParentEntityId(parentProperty)
}

internal class OoParentWithPidEntity(val parentProperty: String) : WorkspaceEntityWithPersistentId, WorkspaceEntityBase() {
  override fun persistentId(): OoParentEntityId = OoParentEntityId(parentProperty)
  val child: OoChildForParentWithPidEntity? by OneToOneParent.Nullable<OoParentWithPidEntity, OoChildForParentWithPidEntity>(
    OoChildForParentWithPidEntity::class.java, false)
}

internal class ModifiableOoParentWithPidEntity : ModifiableWorkspaceEntityBase<OoParentWithPidEntity>() {
  var parentProperty: String by EntityDataDelegation()
  var child: OoChildForParentWithPidEntity? by MutableOneToOneParent.Nullable(OoParentWithPidEntity::class.java,
                                                                              OoChildForParentWithPidEntity::class.java, false)
}

internal fun WorkspaceEntityStorageBuilder.addOoParentWithPidEntity(parentProperty: String = "parent",
                                                                    source: EntitySource = MySource): OoParentWithPidEntity {
  return addEntity(ModifiableOoParentWithPidEntity::class.java, source) {
    this.parentProperty = parentProperty
  }
}

// ---------------- Child entity for parent with PersistentId ----------------------

internal class OoChildForParentWithPidEntityData : WorkspaceEntityData<OoChildForParentWithPidEntity>() {
  lateinit var childProperty: String
  override fun createEntity(snapshot: WorkspaceEntityStorage): OoChildForParentWithPidEntity {
    return OoChildForParentWithPidEntity(childProperty).also { addMetaData(it, snapshot) }
  }
}

internal class OoChildForParentWithPidEntity(val childProperty: String) : WorkspaceEntityBase() {
  val parent: OoParentWithPidEntity by OneToOneChild.NotNull(OoParentWithPidEntity::class.java, true)
}

internal class ModifiableOoChildForParentWithPidEntity : ModifiableWorkspaceEntityBase<OoChildForParentWithPidEntity>() {
  var childProperty: String by EntityDataDelegation()
  var parent: OoParentWithPidEntity by MutableOneToOneChild.NotNull(OoChildForParentWithPidEntity::class.java,
                                                                    OoParentWithPidEntity::class.java, true)
}


internal fun WorkspaceEntityStorageBuilder.addOoChildForParentWithPidEntity(parentEntity: OoParentWithPidEntity,
                                                                            childProperty: String = "child",
                                                                            source: EntitySource = MySource) =
  addEntity(ModifiableOoChildForParentWithPidEntity::class.java, source) {
    this.parent = parentEntity
    this.childProperty = childProperty
  }