// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId
import com.intellij.platform.workspace.storage.annotations.Parent


// ------------------------------ Persistent Id ---------------

data class NameId(private val name: String) : SymbolicEntityId<NamedEntity> {
  override val presentableName: String
    get() = name

  override fun toString(): String = name
}

data class AnotherNameId(private val name: String) : SymbolicEntityId<NamedEntity> {
  override val presentableName: String
    get() = name

  override fun toString(): String = name
}

data class ComposedId(val name: String, val link: NameId) : SymbolicEntityId<ComposedIdSoftRefEntity> {
  override val presentableName: String
    get() = "$name - ${link.presentableName}"
}

// ------------------------------ Entity With Persistent Id ------------------

interface NamedEntity : WorkspaceEntityWithSymbolicId {
  val myName: String
  val additionalProperty: String?

  val children: List<NamedChildEntity>

  override val symbolicId: NameId
    get() = NameId(myName)

}


//val NamedEntity.children: List<NamedChildEntity>
//    get() = TODO()
//  get() = referrers(NamedChildEntity::parent)

// ------------------------------ Child of entity with persistent id ------------------


interface NamedChildEntity : WorkspaceEntity {
  val childProperty: String
  @Parent
  val parentEntity: NamedEntity

}


// ------------------------------ Entity with soft link --------------------

interface WithSoftLinkEntity : WorkspaceEntity {
  val link: NameId

}

interface ComposedLinkEntity : WorkspaceEntity {
  val link: ComposedId

}

// ------------------------- Entity with SymbolicId and the list of soft links ------------------


interface WithListSoftLinksEntity : WorkspaceEntityWithSymbolicId {
  val myName: String
  val links: List<NameId>
  override val symbolicId: AnotherNameId get() = AnotherNameId(myName)

}


// --------------------------- Entity with composed persistent id via soft reference ------------------


interface ComposedIdSoftRefEntity : WorkspaceEntityWithSymbolicId {
  val myName: String
  val link: NameId
  override val symbolicId: ComposedId get() = ComposedId(myName, link)

}

// ------------------------------ Persistent Id ---------------

data class ParentNameId(private val name: String) : SymbolicEntityId<ParentEntityWithSymbolicId> {
  override val presentableName: String
    get() = name

  override fun toString(): String = name
}

data class ChildNameIdWithParentId(private val name: String, private val parentId: ParentNameId) : SymbolicEntityId<ChildEntityWithSymbolicId> {
  override val presentableName: String
    get() = "$name (${parentId.presentableName})"

  override fun toString(): String = "$name (${parentId.presentableName})"
}

// ------------------------- Entity with SymbolicId which uses parent's SymbolicId ------------------

interface ParentEntityWithSymbolicId : WorkspaceEntityWithSymbolicId {
  val myName: String
  val children: List<ChildEntityWithSymbolicId>

  override val symbolicId: ParentNameId 
    get() = ParentNameId(myName)
}

interface ChildEntityWithSymbolicId : WorkspaceEntityWithSymbolicId {
  val myName: String
  @Parent
  val parent: ParentEntityWithSymbolicId

  override val symbolicId: ChildNameIdWithParentId
    get() = ChildNameIdWithParentId(myName, parent.symbolicId)
}
