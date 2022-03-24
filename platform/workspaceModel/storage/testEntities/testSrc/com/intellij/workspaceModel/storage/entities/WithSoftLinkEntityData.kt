// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("unused")

package com.intellij.workspaceModel.storage

import com.intellij.workspaceModel.storage.impl.*
import com.intellij.workspaceModel.storage.impl.indices.WorkspaceMutableIndex
import com.intellij.workspaceModel.storage.impl.references.ManyToOne
import com.intellij.workspaceModel.storage.impl.references.MutableManyToOne

// ------------------------------ Persistent Id ---------------

data class NameId(private val name: String) : PersistentEntityId<NamedEntity>() {
  override val presentableName: String
    get() = name

  override fun toString(): String = name
}

data class AnotherNameId(private val name: String) : PersistentEntityId<NamedEntity>() {
  override val presentableName: String
    get() = name

  override fun toString(): String = name
}

data class ComposedId(val name: String, val link: NameId) : PersistentEntityId<ComposedIdSoftRefEntity>() {
  override val presentableName: String
    get() = "$name - ${link.presentableName}"
}

// ------------------------------ Entity With Persistent Id ------------------

class NamedEntityData : WorkspaceEntityData.WithCalculablePersistentId<NamedEntity>() {
  lateinit var name: String
  var additionalProperty: String? = null

  override fun createEntity(snapshot: WorkspaceEntityStorage): NamedEntity {
    return NamedEntity(name, additionalProperty).also { addMetaData(it, snapshot) }
  }

  override fun persistentId(): PersistentEntityId<*> = NameId(name)

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (this === other) return true
    if (other !is NamedEntityData) return false

    if (name != other.name) return false
    if (additionalProperty != other.additionalProperty) return false

    return true
  }
}

class NamedEntity(val name: String, val additionalProperty: String?) : WorkspaceEntityBase(), WorkspaceEntityWithPersistentId {
  override fun persistentId() = NameId(name)
}

class ModifiableNamedEntity : ModifiableWorkspaceEntityBase<NamedEntity>() {
  var name: String by EntityDataDelegation()
  var additionalProperty: String? by EntityDataDelegation()
}

fun WorkspaceEntityStorageBuilder.addNamedEntity(name: String, additionalProperty: String? = null, source: EntitySource = MySource) =
  addEntity(ModifiableNamedEntity::class.java, source) {
    this.name = name
    this.additionalProperty = additionalProperty
  }


val NamedEntity.children: Sequence<NamedChildEntity>
  get() = referrers(NamedChildEntity::parent)

// ------------------------------ Child of entity with persistent id ------------------

class NamedChildEntityData : WorkspaceEntityData<NamedChildEntity>() {
  lateinit var childProperty: String
  override fun createEntity(snapshot: WorkspaceEntityStorage): NamedChildEntity {
    return NamedChildEntity(childProperty).also { addMetaData(it, snapshot) }
  }
}

class NamedChildEntity(
  val childProperty: String
) : WorkspaceEntityBase() {
  val parent: NamedEntity by ManyToOne.NotNull(NamedEntity::class.java)
}

class ModifiableNamedChildEntity : ModifiableWorkspaceEntityBase<NamedChildEntity>() {
  var childProperty: String by EntityDataDelegation()
  var parent: NamedEntity by MutableManyToOne.NotNull(NamedChildEntity::class.java, NamedEntity::class.java)
}


fun WorkspaceEntityStorageBuilder.addNamedChildEntity(parentEntity: NamedEntity,
                                                      childProperty: String = "child",
                                                      source: EntitySource = MySource) =
  addEntity(ModifiableNamedChildEntity::class.java, source) {
    this.parent = parentEntity
    this.childProperty = childProperty
  }

// ------------------------------ Entity with soft link --------------------

class WithSoftLinkEntityData : WorkspaceEntityData<WithSoftLinkEntity>(), SoftLinkable {

  lateinit var link: PersistentEntityId<*>

  override fun createEntity(snapshot: WorkspaceEntityStorage): WithSoftLinkEntity {
    return WithSoftLinkEntity(link).also { addMetaData(it, snapshot) }
  }

  override fun getLinks(): Set<PersistentEntityId<*>> = setOf(link)
  override fun index(index: WorkspaceMutableIndex<PersistentEntityId<*>>) {
    index.index(this, link)
  }

  override fun updateLinksIndex(prev: Set<PersistentEntityId<*>>, index: WorkspaceMutableIndex<PersistentEntityId<*>>) {
    val previous = prev.singleOrNull()
    if (previous != null) {
      if (previous != link) {
        index.remove(this, previous)
        index.index(this, link)
      }
    }
    else {
      index.index(this, link)
    }
  }

  override fun updateLink(oldLink: PersistentEntityId<*>,
                          newLink: PersistentEntityId<*>): Boolean {
    this.link = newLink
    return true
  }
}

class WithSoftLinkEntity(val link: PersistentEntityId<*>) : WorkspaceEntityBase()

class ModifiableWithSoftLinkEntity : ModifiableWorkspaceEntityBase<WithSoftLinkEntity>() {
  var link: PersistentEntityId<*> by EntityDataDelegation()
}

fun WorkspaceEntityStorageBuilder.addWithSoftLinkEntity(link: PersistentEntityId<*>, source: EntitySource = MySource) =
  addEntity(ModifiableWithSoftLinkEntity::class.java, source) {
    this.link = link
  }

// ------------------------- Entity with persistentId and the list of soft links ------------------

class WithListSoftLinksEntityData : SoftLinkable, WorkspaceEntityData.WithCalculablePersistentId<WithListSoftLinksEntity>() {

  lateinit var name: String
  lateinit var links: MutableList<NameId>

  override fun getLinks(): Set<PersistentEntityId<*>> = links.toSet()

  override fun index(index: WorkspaceMutableIndex<PersistentEntityId<*>>) {
    for (link in links) {
      index.index(this, link)
    }
  }

  override fun updateLinksIndex(prev: Set<PersistentEntityId<*>>, index: WorkspaceMutableIndex<PersistentEntityId<*>>) {
    val mutablePreviousSet = HashSet(prev)

    for (dependency in links) {
      val removed = mutablePreviousSet.remove(dependency)
      if (!removed) {
        index.index(this, dependency)
      }
    }
    for (removed in mutablePreviousSet) {
      index.remove(this, removed)
    }
  }

  override fun updateLink(oldLink: PersistentEntityId<*>,
                          newLink: PersistentEntityId<*>): Boolean {
    links.remove(oldLink)
    links.add(newLink as NameId)
    return true
  }

  override fun createEntity(snapshot: WorkspaceEntityStorage): WithListSoftLinksEntity {
    return WithListSoftLinksEntity(name, links).also { addMetaData(it, snapshot) }
  }

  override fun persistentId() = AnotherNameId(name)
}

class WithListSoftLinksEntity(val name: String, val links: List<NameId>) : WorkspaceEntityBase(), WorkspaceEntityWithPersistentId {
  override fun persistentId(): AnotherNameId = AnotherNameId(name)
}

class ModifiableWithListSoftLinksEntity : ModifiableWorkspaceEntityBase<WithListSoftLinksEntity>() {
  var name: String by EntityDataDelegation()
  var links: List<NameId> by EntityDataDelegation()
}

fun WorkspaceEntityStorageBuilder.addWithListSoftLinksEntity(name: String,
                                                             links: List<NameId>,
                                                             source: EntitySource = MySource) =
  addEntity(ModifiableWithListSoftLinksEntity::class.java, source) {
    this.name = name
    this.links = links
  }

// --------------------------- Entity with composed persistent id via soft reference ------------------

class ComposedIdSoftRefEntityData : WorkspaceEntityData.WithCalculablePersistentId<ComposedIdSoftRefEntity>(), SoftLinkable {
  lateinit var name: String
  lateinit var link: NameId

  override fun createEntity(snapshot: WorkspaceEntityStorage): ComposedIdSoftRefEntity {
    return ComposedIdSoftRefEntity(name, link).also { addMetaData(it, snapshot) }
  }

  override fun getLinks(): Set<PersistentEntityId<*>> = setOf(link)

  override fun index(index: WorkspaceMutableIndex<PersistentEntityId<*>>) {
    index.index(this, link)
  }

  override fun updateLinksIndex(prev: Set<PersistentEntityId<*>>, index: WorkspaceMutableIndex<PersistentEntityId<*>>) {
    val previous = prev.singleOrNull()
    if (previous != null) {
      if (previous != link) {
        index.remove(this, previous)
        index.index(this, link)
      }
    }
    else {
      index.index(this, link)
    }
  }

  override fun updateLink(oldLink: PersistentEntityId<*>,
                          newLink: PersistentEntityId<*>): Boolean {
    if (oldLink != link) return false
    this.link = newLink as NameId
    return true
  }

  override fun persistentId() = ComposedId(name, link)

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (this === other) return true
    if (other !is ComposedIdSoftRefEntityData) return false

    if (name != other.name) return false
    if (link != other.link) return false

    return true
  }
}

class ComposedIdSoftRefEntity(val name: String, val link: NameId) : WorkspaceEntityBase(), WorkspaceEntityWithPersistentId {
  override fun persistentId(): ComposedId = ComposedId(name, link)
}

class ModifiableComposedIdSoftRefEntity : ModifiableWorkspaceEntityBase<ComposedIdSoftRefEntity>() {
  var name: String by EntityDataDelegation()
  var link: NameId by EntityDataDelegation()
}

fun WorkspaceEntityStorageBuilder.addComposedIdSoftRefEntity(name: String,
                                                             link: NameId,
                                                             source: EntitySource = MySource): ComposedIdSoftRefEntity {
  return addEntity(ModifiableComposedIdSoftRefEntity::class.java, source) {
    this.name = name
    this.link = link
  }
}
