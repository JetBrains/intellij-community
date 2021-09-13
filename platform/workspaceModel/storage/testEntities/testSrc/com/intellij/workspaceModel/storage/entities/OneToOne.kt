// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage

import com.intellij.workspaceModel.storage.impl.EntityDataDelegation
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import com.intellij.workspaceModel.storage.impl.references.MutableOneToOneChild
import com.intellij.workspaceModel.storage.impl.references.MutableOneToOneParent
import com.intellij.workspaceModel.storage.impl.references.OneToOneChild
import com.intellij.workspaceModel.storage.impl.references.OneToOneParent

//region ------------------- Parent Entity --------------------------------

@Suppress("unused")
class OoParentEntityData : WorkspaceEntityData<OoParentEntity>() {

  lateinit var parentProperty: String

  override fun createEntity(snapshot: WorkspaceEntityStorage): OoParentEntity {
    return OoParentEntity(parentProperty).also { addMetaData(it, snapshot) }
  }
}

@Suppress("unused")
class OoParentEntity(val parentProperty: String) : WorkspaceEntityBase() {

  val child: OoChildEntity? by OneToOneParent.Nullable<OoParentEntity, OoChildEntity>(OoChildEntity::class.java, false)
}

class ModifiableOoParentEntity : ModifiableWorkspaceEntityBase<OoParentEntity>() {
  var parentProperty: String by EntityDataDelegation()
  var child: OoChildEntity? by MutableOneToOneParent.Nullable(OoParentEntity::class.java, OoChildEntity::class.java, false)
}

fun WorkspaceEntityStorageBuilder.addOoParentEntity(parentProperty: String = "parent",
                                                             source: EntitySource = MySource): OoParentEntity {
  return addEntity(ModifiableOoParentEntity::class.java, source) {
    this.parentProperty = parentProperty
  }
}

//endregion

//region ---------------- Child entity ----------------------

@Suppress("unused")
class OoChildEntityData : WorkspaceEntityData<OoChildEntity>() {
  lateinit var childProperty: String
  override fun createEntity(snapshot: WorkspaceEntityStorage): OoChildEntity {
    return OoChildEntity(childProperty).also { addMetaData(it, snapshot) }
  }
}

@Suppress("unused")
class OoChildEntity(val childProperty: String) : WorkspaceEntityBase() {
  val parent: OoParentEntity by OneToOneChild.NotNull(OoParentEntity::class.java)
}

class ModifiableOoChildEntity : ModifiableWorkspaceEntityBase<OoChildEntity>() {
  var childProperty: String by EntityDataDelegation()
  var parent: OoParentEntity by MutableOneToOneChild.NotNull(OoChildEntity::class.java, OoParentEntity::class.java)
}


fun WorkspaceEntityStorageBuilder.addOoChildEntity(OoParentEntity: OoParentEntity,
                                                   childProperty: String = "child",
                                                   source: EntitySource = MySource) =
  addEntity(ModifiableOoChildEntity::class.java, source) {
    this.parent = OoParentEntity
    this.childProperty = childProperty
  }

//endregion

//region ----------------- Child entity with a nullable parent -----------------------------
@Suppress("unused")
class OoChildWithNullableParentEntityData : WorkspaceEntityData<OoChildWithNullableParentEntity>() {
  override fun createEntity(snapshot: WorkspaceEntityStorage): OoChildWithNullableParentEntity {
    return OoChildWithNullableParentEntity().also { addMetaData(it, snapshot) }
  }
}

class OoChildWithNullableParentEntity : WorkspaceEntityBase() {
  val parent: OoParentEntity? by OneToOneChild.Nullable(OoParentEntity::class.java)
}

class ModifiableOoChildWithNullableParentEntity : ModifiableWorkspaceEntityBase<OoChildWithNullableParentEntity>() {
  var parent: OoParentEntity? by MutableOneToOneChild.Nullable(OoChildWithNullableParentEntity::class.java, OoParentEntity::class.java)
}

fun WorkspaceEntityStorageBuilder.addOoChildWithNullableParentEntity(OoParentEntity: OoParentEntity,
                                                                     source: EntitySource = MySource) =
  addEntity(ModifiableOoChildWithNullableParentEntity::class.java, source) {
    this.parent = OoParentEntity
  }

//endregion

//region ------------------- Parent Entity with PersistentId --------------------------------

data class OoParentEntityId(val name: String) : PersistentEntityId<OoParentWithPidEntity>() {
  override val parentId: PersistentEntityId<*>?
    get() = null
  override val presentableName: String
    get() = name
}

@Suppress("unused")
class OoParentWithPidEntityData : WorkspaceEntityData.WithCalculablePersistentId<OoParentWithPidEntity>() {

  lateinit var parentProperty: String

  override fun createEntity(snapshot: WorkspaceEntityStorage): OoParentWithPidEntity {
    return OoParentWithPidEntity(parentProperty).also { addMetaData(it, snapshot) }
  }

  override fun persistentId(): OoParentEntityId = OoParentEntityId(parentProperty)
}

class OoParentWithPidEntity(val parentProperty: String) : WorkspaceEntityWithPersistentId, WorkspaceEntityBase() {
  override fun persistentId(): OoParentEntityId = OoParentEntityId(parentProperty)
  val childOne: OoChildForParentWithPidEntity? by OneToOneParent.Nullable<OoParentWithPidEntity, OoChildForParentWithPidEntity>(
    OoChildForParentWithPidEntity::class.java, false)
  val childThree: OoChildAlsoWithPidEntity? by OneToOneParent.Nullable<OoParentWithPidEntity, OoChildAlsoWithPidEntity>(
    OoChildAlsoWithPidEntity::class.java, false)
}

class ModifiableOoParentWithPidEntity : ModifiableWorkspaceEntityBase<OoParentWithPidEntity>() {
  var parentProperty: String by EntityDataDelegation()
  var childOne: OoChildForParentWithPidEntity? by MutableOneToOneParent.Nullable(OoParentWithPidEntity::class.java,
                                                                                 OoChildForParentWithPidEntity::class.java, false)
  var childThree: OoChildAlsoWithPidEntity? by MutableOneToOneParent.Nullable(OoParentWithPidEntity::class.java,
                                                                              OoChildAlsoWithPidEntity::class.java, false)
}

fun WorkspaceEntityStorageBuilder.addOoParentWithPidEntity(parentProperty: String = "parent",
                                                                    source: EntitySource = MySource): OoParentWithPidEntity {
  return addEntity(ModifiableOoParentWithPidEntity::class.java, source) {
    this.parentProperty = parentProperty
  }
}

//endregion

// ---------------- Child entity for parent with PersistentId for Nullable ref ----------------------

class OoChildForParentWithPidEntityData : WorkspaceEntityData<OoChildForParentWithPidEntity>() {
  lateinit var childProperty: String
  override fun createEntity(snapshot: WorkspaceEntityStorage): OoChildForParentWithPidEntity {
    return OoChildForParentWithPidEntity(childProperty).also { addMetaData(it, snapshot) }
  }
}

class OoChildForParentWithPidEntity(val childProperty: String) : WorkspaceEntityBase() {
  val parent: OoParentWithPidEntity by OneToOneChild.NotNull(OoParentWithPidEntity::class.java)
}

class ModifiableOoChildForParentWithPidEntity : ModifiableWorkspaceEntityBase<OoChildForParentWithPidEntity>() {
  var childProperty: String by EntityDataDelegation()
  var parent: OoParentWithPidEntity by MutableOneToOneChild.NotNull(OoChildForParentWithPidEntity::class.java,
                                                                    OoParentWithPidEntity::class.java)
}


fun WorkspaceEntityStorageBuilder.addOoChildForParentWithPidEntity(parentEntity: OoParentWithPidEntity,
                                                                   childProperty: String = "child",
                                                                   source: EntitySource = MySource) =
  addEntity(ModifiableOoChildForParentWithPidEntity::class.java, source) {
    this.parent = parentEntity
    this.childProperty = childProperty
  }

// ---------------- Child with PersistentId for parent with PersistentId ----------------------

class OoChildAlsoWithPidEntityData : WorkspaceEntityData.WithCalculablePersistentId<OoChildAlsoWithPidEntity>() {
  lateinit var childProperty: String

  override fun createEntity(snapshot: WorkspaceEntityStorage): OoChildAlsoWithPidEntity {
    return OoChildAlsoWithPidEntity(childProperty).also { addMetaData(it, snapshot) }
  }

  override fun persistentId(): OoChildEntityId = OoChildEntityId(childProperty)
}

class OoChildAlsoWithPidEntity(val childProperty: String) : WorkspaceEntityWithPersistentId, WorkspaceEntityBase() {
  val parent: OoParentWithPidEntity by OneToOneChild.NotNull(OoParentWithPidEntity::class.java)

  override fun persistentId(): OoChildEntityId = OoChildEntityId(childProperty)
}

class ModifiableOoChildAlsoWithPidEntity : ModifiableWorkspaceEntityBase<OoChildAlsoWithPidEntity>() {
  var childProperty: String by EntityDataDelegation()
  var parent: OoParentWithPidEntity by MutableOneToOneChild.NotNull(OoChildAlsoWithPidEntity::class.java,
                                                                    OoParentWithPidEntity::class.java)
}

fun WorkspaceEntityStorageBuilder.addOoChildAlsoWithPidEntity(parentEntity: OoParentWithPidEntity,
                                                              childProperty: String = "child",
                                                              source: EntitySource = MySource) =
  addEntity(ModifiableOoChildAlsoWithPidEntity::class.java, source) {
    this.parent = parentEntity
    this.childProperty = childProperty
  }

// ------------------- Parent Entity without PersistentId for Nullable ref --------------------------------

class OoParentWithoutPidEntityData : WorkspaceEntityData<OoParentWithoutPidEntity>() {
  lateinit var parentProperty: String

  override fun createEntity(snapshot: WorkspaceEntityStorage): OoParentWithoutPidEntity {
    return OoParentWithoutPidEntity(parentProperty).also { addMetaData(it, snapshot) }
  }
}

class OoParentWithoutPidEntity(val parentProperty: String) : WorkspaceEntityBase() {
  val childOne: OoChildWithPidEntity? by OneToOneParent.Nullable<OoParentWithoutPidEntity, OoChildWithPidEntity>(
    OoChildWithPidEntity::class.java, false)
}

class ModifiableOoParentWithoutPidEntity : ModifiableWorkspaceEntityBase<OoParentWithoutPidEntity>() {
  var parentProperty: String by EntityDataDelegation()
  var childOne: OoChildWithPidEntity? by MutableOneToOneParent.Nullable(OoParentWithoutPidEntity::class.java,
                                                                        OoChildWithPidEntity::class.java, false)
}

fun WorkspaceEntityStorageBuilder.addOoParentWithoutPidEntity(parentProperty: String = "parent",
                                                                       source: EntitySource = MySource): OoParentWithoutPidEntity {
  return addEntity(ModifiableOoParentWithoutPidEntity::class.java, source) {
    this.parentProperty = parentProperty
  }
}

// ---------------- Child entity with PersistentId for Nullable ref----------------------

data class OoChildEntityId(val name: String) : PersistentEntityId<OoChildWithPidEntity>() {
  override val parentId: PersistentEntityId<*>?
    get() = null
  override val presentableName: String
    get() = name
}

class OoChildWithPidEntityData : WorkspaceEntityData.WithCalculablePersistentId<OoChildWithPidEntity>() {
  lateinit var childProperty: String

  override fun createEntity(snapshot: WorkspaceEntityStorage): OoChildWithPidEntity {
    return OoChildWithPidEntity(childProperty).also { addMetaData(it, snapshot) }
  }

  override fun persistentId(): OoChildEntityId = OoChildEntityId(childProperty)
}

class OoChildWithPidEntity(val childProperty: String) : WorkspaceEntityWithPersistentId, WorkspaceEntityBase() {
  val parent: OoParentWithoutPidEntity by OneToOneChild.NotNull(OoParentWithoutPidEntity::class.java)

  override fun persistentId(): OoChildEntityId = OoChildEntityId(childProperty)
}

class ModifiableOoChildWithPidEntity : ModifiableWorkspaceEntityBase<OoChildWithPidEntity>() {
  var childProperty: String by EntityDataDelegation()
  var parent: OoParentWithoutPidEntity by MutableOneToOneChild.NotNull(OoChildWithPidEntity::class.java,
                                                                       OoParentWithoutPidEntity::class.java)
}

fun WorkspaceEntityStorageBuilder.addOoChildWithPidEntity(parentEntity: OoParentWithoutPidEntity,
                                                          childProperty: String = "child",
                                                          source: EntitySource = MySource) =
  addEntity(ModifiableOoChildWithPidEntity::class.java, source) {
    this.parent = parentEntity
    this.childProperty = childProperty
  }
