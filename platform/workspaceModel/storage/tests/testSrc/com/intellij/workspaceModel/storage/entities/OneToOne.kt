// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.entities

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
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
