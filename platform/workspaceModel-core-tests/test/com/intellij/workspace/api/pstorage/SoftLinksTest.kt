//.indexes Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage

import com.intellij.testFramework.UsefulTestCase.assertOneElement
import com.intellij.workspace.api.EntitySource
import com.intellij.workspace.api.PersistentEntityId
import com.intellij.workspace.api.TypedEntityStorage
import com.intellij.workspace.api.TypedEntityWithPersistentId
import junit.framework.Assert.assertNotNull
import org.junit.Test

internal data class NameId(private val name: String) : PersistentEntityId<NamedEntity>() {
  override val parentId: PersistentEntityId<*>?
    get() = null
  override val presentableName: String
    get() = name

  override fun toString(): String = name
}

@Suppress("unused")
internal class NamedEntityData : PEntityData.WithCalculatablePersistentId<NamedEntity>() {
  lateinit var name: String
  override fun createEntity(snapshot: TypedEntityStorage): NamedEntity {
    return NamedEntity(name).also { addMetaData(it, snapshot) }
  }

  override fun persistentId(): PersistentEntityId<*> = NameId(name)
}

internal class NamedEntity(
  val name: String
) : PTypedEntity(), TypedEntityWithPersistentId {
  override fun persistentId(): PersistentEntityId<*> = NameId(name)
}

internal class ModifiableNamedEntity : PModifiableTypedEntity<NamedEntity>() {
  var name: String by EntityDataDelegation()
}

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

internal class WithSoftLinkEntity(
  val link: NameId
) : PTypedEntity()

internal class ModifiableWithSoftLinkEntity : PModifiableTypedEntity<WithSoftLinkEntity>() {
  var link: NameId by EntityDataDelegation()
}

internal object MySource : EntitySource

class SoftLinksTest {
  @Test
  fun `test add diff with soft links`() {
    val id = "MyId"
    val newId = "MyNewId"

    // Setup builder for test
    val builder = PEntityStorageBuilder.create()
    builder.addEntity(ModifiableWithSoftLinkEntity::class.java, MySource) {
      this.link = NameId(id)
    }
    builder.addEntity(ModifiableNamedEntity::class.java, MySource) {
      name = id
    }

    // Change persistent id in a different builder
    val newBuilder = PEntityStorageBuilder.from(builder.toStorage())
    val entity = newBuilder.resolve(NameId(id))!!
    newBuilder.modifyEntity(ModifiableNamedEntity::class.java, entity) {
      this.name = newId
    }

    // Apply changes
    builder.addDiff(newBuilder)

    // Check
    assertNotNull(builder.resolve(NameId(newId)))
    assertOneElement(builder.indexes.softLinks.get(NameId(newId)))
  }

  @Test
  fun `test add diff with soft links and back`() {
    val id = "MyId"
    val newId = "MyNewId"

    // Setup builder for test
    val builder = PEntityStorageBuilder.create()
    builder.addEntity(ModifiableWithSoftLinkEntity::class.java, MySource) {
      this.link = NameId(id)
    }
    builder.addEntity(ModifiableNamedEntity::class.java, MySource) {
      name = id
    }

    // Change persistent id in a different builder
    val newBuilder = PEntityStorageBuilder.from(builder.toStorage())
    val entity = newBuilder.resolve(NameId(id))!!
    newBuilder.modifyEntity(ModifiableNamedEntity::class.java, entity) {
      this.name = newId
    }

    // Apply changes
    builder.addDiff(newBuilder)

    // Check
    assertNotNull(builder.resolve(NameId(newId)))
    assertOneElement(builder.indexes.softLinks.get(NameId(newId)))

    // Change persistent id to the initial value
    val anotherNewBuilder = PEntityStorageBuilder.from(builder.toStorage())
    val anotherEntity = anotherNewBuilder.resolve(NameId(newId))!!
    anotherNewBuilder.modifyEntity(ModifiableNamedEntity::class.java, anotherEntity) {
      this.name = id
    }

    // Apply changes
    builder.addDiff(anotherNewBuilder)

    // Check
    assertNotNull(builder.resolve(NameId(id)))
    assertOneElement(builder.indexes.softLinks.get(NameId(id)))
  }
}