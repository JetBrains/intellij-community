// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("unused")

package com.intellij.workspace.api.pstorage.entities

import com.intellij.workspace.api.EntitySource
import com.intellij.workspace.api.PersistentEntityId
import com.intellij.workspace.api.TypedEntityStorage
import com.intellij.workspace.api.TypedEntityWithPersistentId
import com.intellij.workspace.api.pstorage.*

// ------------------------------ Persistent Id ---------------

internal data class NameId(private val name: String) : PersistentEntityId<NamedEntity>() {
  override val parentId: PersistentEntityId<*>?
    get() = null
  override val presentableName: String
    get() = name

  override fun toString(): String = name
}

// ------------------------------ Entity With Persistent Id ------------------

internal class NamedEntityData : PEntityData.WithCalculatablePersistentId<NamedEntity>() {

  lateinit var name: String
  override fun createEntity(snapshot: TypedEntityStorage): NamedEntity {
    return NamedEntity(name).also { addMetaData(it, snapshot) }
  }

  override fun persistentId(): PersistentEntityId<*> = NameId(name)

}

internal class NamedEntity(val name: String) : PTypedEntity(), TypedEntityWithPersistentId {
  override fun persistentId() = NameId(name)
}

internal class ModifiableNamedEntity : PModifiableTypedEntity<NamedEntity>() {
  var name: String by EntityDataDelegation()
}

internal fun PEntityStorageBuilder.addNamedEntity(name: String, source: EntitySource = MySource) =
  addEntity(ModifiableNamedEntity::class.java, source) {
    this.name = name
  }

// ------------------------------ Entity with soft link --------------------

internal class WithSoftLinkEntityData : PEntityData<WithSoftLinkEntity>(), PSoftLinkable {

  lateinit var link: NameId

  override fun createEntity(snapshot: TypedEntityStorage): WithSoftLinkEntity {
    return WithSoftLinkEntity(link).also { addMetaData(it, snapshot) }
  }

  override fun getLinks(): List<PersistentEntityId<*>> = listOf(link)

  override fun updateLink(oldLink: PersistentEntityId<*>,
                          newLink: PersistentEntityId<*>,
                          affectedIds: MutableList<Pair<PersistentEntityId<*>, PersistentEntityId<*>>>): Boolean {
    this.link = newLink as NameId
    return true
  }
}

internal class WithSoftLinkEntity(val link: NameId) : PTypedEntity()

internal class ModifiableWithSoftLinkEntity : PModifiableTypedEntity<WithSoftLinkEntity>() {
  var link: NameId by EntityDataDelegation()
}

internal fun PEntityStorageBuilder.addWithSoftLinkEntity(link: NameId, source: EntitySource = MySource) =
  addEntity(ModifiableWithSoftLinkEntity::class.java, source) {
    this.link = link
  }
