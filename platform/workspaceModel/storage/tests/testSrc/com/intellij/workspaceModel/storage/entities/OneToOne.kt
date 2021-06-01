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
  val childOne: OoChildForParentWithPidEntity? by OneToOneParent.Nullable<OoParentWithPidEntity, OoChildForParentWithPidEntity>(
    OoChildForParentWithPidEntity::class.java, false)
  val childTwo: OoChildForParentWithPidNotNullRefEntity by OneToOneParent.NotNull<OoParentWithPidEntity, OoChildForParentWithPidNotNullRefEntity>(
    OoChildForParentWithPidNotNullRefEntity::class.java, false)
  val childThree: OoChildAlsoWithPidEntity? by OneToOneParent.Nullable<OoParentWithPidEntity, OoChildAlsoWithPidEntity>(
    OoChildAlsoWithPidEntity::class.java, false)
}

internal class ModifiableOoParentWithPidEntity : ModifiableWorkspaceEntityBase<OoParentWithPidEntity>() {
  var parentProperty: String by EntityDataDelegation()
  var childOne: OoChildForParentWithPidEntity? by MutableOneToOneParent.Nullable(OoParentWithPidEntity::class.java,
                                                                                 OoChildForParentWithPidEntity::class.java, false)
  var childTwo: OoChildForParentWithPidNotNullRefEntity by MutableOneToOneParent.NotNull(OoParentWithPidEntity::class.java,
                                                                                         OoChildForParentWithPidNotNullRefEntity::class.java,
                                                                                         false)
  var childThree: OoChildAlsoWithPidEntity by MutableOneToOneParent.NotNull(OoParentWithPidEntity::class.java,
                                                                              OoChildAlsoWithPidEntity::class.java,
                                                                              false)
}

internal fun WorkspaceEntityStorageBuilder.addOoParentWithPidEntity(parentProperty: String = "parent",
                                                                    source: EntitySource = MySource): OoParentWithPidEntity {
  return addEntity(ModifiableOoParentWithPidEntity::class.java, source) {
    this.parentProperty = parentProperty
  }
}

// ---------------- Child entity for parent with PersistentId for Nullable ref ----------------------

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

// ---------------- Child entity for parent with PersistentId for Not Null ref ----------------------

internal class OoChildForParentWithPidNotNullRefEntityData : WorkspaceEntityData<OoChildForParentWithPidNotNullRefEntity>() {
  lateinit var childProperty: String
  override fun createEntity(snapshot: WorkspaceEntityStorage): OoChildForParentWithPidNotNullRefEntity {
    return OoChildForParentWithPidNotNullRefEntity(childProperty).also { addMetaData(it, snapshot) }
  }
}

internal class OoChildForParentWithPidNotNullRefEntity(val childProperty: String) : WorkspaceEntityBase() {
  val parent: OoParentWithPidEntity by OneToOneChild.NotNull(OoParentWithPidEntity::class.java, false)
}

internal class ModifiableOoChildForParentWithPidNotNullRefEntity : ModifiableWorkspaceEntityBase<OoChildForParentWithPidNotNullRefEntity>() {
  var childProperty: String by EntityDataDelegation()
  var parent: OoParentWithPidEntity by MutableOneToOneChild.NotNull(OoChildForParentWithPidNotNullRefEntity::class.java,
                                                                    OoParentWithPidEntity::class.java, false)
}


internal fun WorkspaceEntityStorageBuilder.addOoChildForParentWithPidNotNullRefEntity(parentEntity: OoParentWithPidEntity,
                                                                            childProperty: String = "child",
                                                                            source: EntitySource = MySource) =
  addEntity(ModifiableOoChildForParentWithPidNotNullRefEntity::class.java, source) {
    this.parent = parentEntity
    this.childProperty = childProperty
  }

// ---------------- Child with PersistentId for parent with PersistentId ----------------------

internal class OoChildAlsoWithPidEntityData : WorkspaceEntityData.WithCalculablePersistentId<OoChildAlsoWithPidEntity>() {
  lateinit var childProperty: String

  override fun createEntity(snapshot: WorkspaceEntityStorage): OoChildAlsoWithPidEntity {
    return OoChildAlsoWithPidEntity(childProperty).also { addMetaData(it, snapshot) }
  }

  override fun persistentId(): OoChildEntityId = OoChildEntityId(childProperty)
}

internal class OoChildAlsoWithPidEntity(val childProperty: String) : WorkspaceEntityWithPersistentId, WorkspaceEntityBase() {
  val parent: OoParentWithPidEntity by OneToOneChild.NotNull(OoParentWithPidEntity::class.java, false)

  override fun persistentId(): OoChildEntityId = OoChildEntityId(childProperty)
}

internal class ModifiableOoChildAlsoWithPidEntity : ModifiableWorkspaceEntityBase<OoChildAlsoWithPidEntity>() {
  var childProperty: String by EntityDataDelegation()
  var parent: OoParentWithPidEntity by MutableOneToOneChild.NotNull(OoChildAlsoWithPidEntity::class.java,
                                                                    OoParentWithPidEntity::class.java, false)
}

internal fun WorkspaceEntityStorageBuilder.addOoChildAlsoWithPidEntity(parentEntity: OoParentWithPidEntity,
                                                                   childProperty: String = "child",
                                                                   source: EntitySource = MySource) =
  addEntity(ModifiableOoChildAlsoWithPidEntity::class.java, source) {
    this.parent = parentEntity
    this.childProperty = childProperty
  }

// ------------------- Parent Entity without PersistentId for Nullable ref --------------------------------

internal class OoParentWithoutPidEntityData : WorkspaceEntityData<OoParentWithoutPidEntity>() {
  lateinit var parentProperty: String

  override fun createEntity(snapshot: WorkspaceEntityStorage): OoParentWithoutPidEntity {
    return OoParentWithoutPidEntity(parentProperty).also { addMetaData(it, snapshot) }
  }
}

internal class OoParentWithoutPidEntity(val parentProperty: String) : WorkspaceEntityBase() {
  val childOne: OoChildWithPidEntity? by OneToOneParent.Nullable<OoParentWithoutPidEntity, OoChildWithPidEntity>(
    OoChildWithPidEntity::class.java, false)
  val childTwo: OoChildWithPidEntity by OneToOneParent.NotNull<OoParentWithoutPidEntity, OoChildWithPidEntity>(
    OoChildWithPidEntity::class.java, false)
}

internal class ModifiableOoParentWithoutPidEntity : ModifiableWorkspaceEntityBase<OoParentWithoutPidEntity>() {
  var parentProperty: String by EntityDataDelegation()
  var childOne: OoChildWithPidEntity? by MutableOneToOneParent.Nullable(OoParentWithoutPidEntity::class.java,
                                                                        OoChildWithPidEntity::class.java, false)
  var childTwo: OoChildWithPidEntity by MutableOneToOneParent.NotNull(OoParentWithoutPidEntity::class.java,
                                                                      OoChildWithPidEntity::class.java, false)
}

internal fun WorkspaceEntityStorageBuilder.addOoParentWithoutPidEntity(parentProperty: String = "parent",
                                                                       source: EntitySource = MySource): OoParentWithoutPidEntity {
  return addEntity(ModifiableOoParentWithoutPidEntity::class.java, source) {
    this.parentProperty = parentProperty
  }
}

// ---------------- Child entity with PersistentId for Nullable ref----------------------

internal data class OoChildEntityId(val name: String) : PersistentEntityId<OoChildWithPidEntity>() {
  override val parentId: PersistentEntityId<*>?
    get() = null
  override val presentableName: String
    get() = name
}

internal class OoChildWithPidEntityData : WorkspaceEntityData.WithCalculablePersistentId<OoChildWithPidEntity>() {
  lateinit var childProperty: String

  override fun createEntity(snapshot: WorkspaceEntityStorage): OoChildWithPidEntity {
    return OoChildWithPidEntity(childProperty).also { addMetaData(it, snapshot) }
  }

  override fun persistentId(): OoChildEntityId = OoChildEntityId(childProperty)
}

internal class OoChildWithPidEntity(val childProperty: String) : WorkspaceEntityWithPersistentId, WorkspaceEntityBase() {
  val parent: OoParentWithoutPidEntity by OneToOneChild.NotNull(OoParentWithoutPidEntity::class.java, true)

  override fun persistentId(): OoChildEntityId = OoChildEntityId(childProperty)
}

internal class ModifiableOoChildWithPidEntity : ModifiableWorkspaceEntityBase<OoChildWithPidEntity>() {
  var childProperty: String by EntityDataDelegation()
  var parent: OoParentWithoutPidEntity by MutableOneToOneChild.NotNull(OoChildWithPidEntity::class.java,
                                                                       OoParentWithoutPidEntity::class.java, true)
}

internal fun WorkspaceEntityStorageBuilder.addOoChildWithPidEntity(parentEntity: OoParentWithoutPidEntity,
                                                                   childProperty: String = "child",
                                                                   source: EntitySource = MySource) =
  addEntity(ModifiableOoChildWithPidEntity::class.java, source) {
    this.parent = parentEntity
    this.childProperty = childProperty
  }

// ---------------- Child entity with PersistentId for Not Null ref----------------------

internal class OoChildWithPidNotNullRefEntityData : WorkspaceEntityData.WithCalculablePersistentId<OoChildWithPidNotNullRefEntity>() {
  lateinit var childProperty: String
  lateinit var childField: String

  override fun createEntity(snapshot: WorkspaceEntityStorage): OoChildWithPidNotNullRefEntity {
    return OoChildWithPidNotNullRefEntity(childProperty, childField).also { addMetaData(it, snapshot) }
  }

  override fun persistentId(): OoChildEntityId = OoChildEntityId(childProperty)
}

internal class OoChildWithPidNotNullRefEntity(val childProperty: String, val childField: String) : WorkspaceEntityWithPersistentId, WorkspaceEntityBase() {
  val parent: OoParentWithoutPidEntity by OneToOneChild.NotNull(OoParentWithoutPidEntity::class.java, false)

  override fun persistentId(): OoChildEntityId = OoChildEntityId(childProperty)
}

internal class ModifiableOoChildWithPidNotNullRefEntity : ModifiableWorkspaceEntityBase<OoChildWithPidNotNullRefEntity>() {
  var childProperty: String by EntityDataDelegation()
  var childField: String by EntityDataDelegation()
  var parent: OoParentWithoutPidEntity by MutableOneToOneChild.NotNull(OoChildWithPidNotNullRefEntity::class.java,
                                                                       OoParentWithoutPidEntity::class.java, false)
}


internal fun WorkspaceEntityStorageBuilder.addOoChildWithPidNotNullRefEntity(parentEntity: OoParentWithoutPidEntity,
                                                                             childProperty: String = "child",
                                                                             childField: String = "child",
                                                                             source: EntitySource = MySource) =
  addEntity(ModifiableOoChildWithPidNotNullRefEntity::class.java, source) {
    this.parent = parentEntity
    this.childProperty = childProperty
    this.childField = childField
  }