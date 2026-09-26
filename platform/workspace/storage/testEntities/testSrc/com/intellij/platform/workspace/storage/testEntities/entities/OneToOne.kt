// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId
import com.intellij.platform.workspace.storage.annotations.Parent


//region ------------------- Parent Entity --------------------------------

@Suppress("unused")
interface OoParentEntity : WorkspaceEntity {
  val parentProperty: String
  val child: OoChildEntity?

  val anotherChild: OoChildWithNullableParentEntity?

}

//region ---------------- Child entity ----------------------


@Suppress("unused")
interface OoChildEntity : WorkspaceEntity {
  val childProperty: String
  @Parent
  val parentEntity: OoParentEntity

}


//region ----------------- Child entity with a nullable parent -----------------------------
interface OoChildWithNullableParentEntity : WorkspaceEntity {
  @Parent
  val parentEntity: OoParentEntity?

}

//region ------------------- Parent Entity with SymbolicId --------------------------------

data class OoParentEntityId(val name: String) : SymbolicEntityId<OoParentWithPidEntity> {
  override val presentableName: String
    get() = name
}


interface OoParentWithPidEntity : WorkspaceEntityWithSymbolicId {
  val parentProperty: String

  override val symbolicId: OoParentEntityId get() = OoParentEntityId(parentProperty)

  val childOne: OoChildForParentWithPidEntity?
  val childThree: OoChildAlsoWithPidEntity?

}


// ---------------- Child entity for parent with SymbolicId for Nullable ref ----------------------

interface OoChildForParentWithPidEntity : WorkspaceEntity {
  val childProperty: String
  @Parent
  val parentEntity: OoParentWithPidEntity

}

// ---------------- Child with SymbolicId for parent with SymbolicId ----------------------

interface OoChildAlsoWithPidEntity : WorkspaceEntityWithSymbolicId {
  val childProperty: String
  @Parent
  val parentEntity: OoParentWithPidEntity

  override val symbolicId: OoChildEntityId get() = OoChildEntityId(childProperty)

}

// ------------------- Parent Entity without SymbolicId for Nullable ref --------------------------------


interface OoParentWithoutPidEntity : WorkspaceEntity {
  val parentProperty: String
  val childOne: OoChildWithPidEntity?

}


// ---------------- Child entity with SymbolicId for Nullable ref----------------------

data class OoChildEntityId(val name: String) : SymbolicEntityId<OoChildWithPidEntity> {
  override val presentableName: String
    get() = name
}

interface OoChildWithPidEntity : WorkspaceEntityWithSymbolicId {
  val childProperty: String
  @Parent
  val parentEntity: OoParentWithoutPidEntity

  override val symbolicId: OoChildEntityId get() = OoChildEntityId(childProperty)

}
