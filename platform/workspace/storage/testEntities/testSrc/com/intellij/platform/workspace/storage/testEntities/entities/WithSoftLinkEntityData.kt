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

// -------------------------------------------- Parent-Child-Dependent SymbolicIds --------------------------------------------
data class PCDId1(private val name: String) : SymbolicEntityId<PcdParent1Entity> {
  override val presentableName: String
    get() = "PCD1 / $name"
}

data class PCDId2(private val version: Int) : SymbolicEntityId<PcdParent2Entity> {
  override val presentableName: String
    get() = "PCD2 / $version"
}

data class PCDIdChild(private val data: Boolean, private val id1: PCDId1, private val id2: PCDId2) : SymbolicEntityId<PcdChildEntity> {
  override val presentableName: String
    get() = "PCD3 / $data / ${id1.presentableName} / ${id2.presentableName}"
}

// -------------------------------------------- CMPLX Entites --------------------------------------------
interface PcdParent1Entity : WorkspaceEntityWithSymbolicId {
  val name: String
  val version: Int
  val child: PcdChildEntity?
  override val symbolicId: PCDId1
    get() = PCDId1(name)
}

interface PcdParent2Entity : WorkspaceEntityWithSymbolicId {
  val name: String
  val version: Int
  val children: List<PcdChildEntity>
  override val symbolicId: PCDId2
    get() = PCDId2(version)
}

interface PcdChildEntity : WorkspaceEntityWithSymbolicId {
  val data: Boolean

  @Parent
  val parent1: PcdParent1Entity

  @Parent
  val parent2: PcdParent2Entity
  override val symbolicId: PCDIdChild
    get() = PCDIdChild(data, parent1.symbolicId, parent2.symbolicId)
}