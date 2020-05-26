// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage

import com.intellij.workspace.api.EntitySource
import com.intellij.workspace.api.TypedEntityStorage
import com.intellij.workspace.api.TypedEntityStorageBuilder
import com.intellij.workspace.api.pstorage.references.ManyToOne
import com.intellij.workspace.api.pstorage.references.MutableManyToOne
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal class PSourceEntityData : PEntityData<PSourceEntity>() {
  lateinit var data: String
  override fun createEntity(snapshot: TypedEntityStorage): PSourceEntity {
    return PSourceEntity(data).also { addMetaData(it, snapshot) }
  }
}

internal class PSourceEntity(val data: String) : PTypedEntity()

internal class ModifiablePSourceEntity : PModifiableTypedEntity<PSourceEntity>() {
  var data: String by EntityDataDelegation()
}

internal fun TypedEntityStorageBuilder.addPSourceEntity(data: String,
                                                        source: EntitySource): PSourceEntity {
  return addEntity(ModifiablePSourceEntity::class.java, source) {
    this.data = data
  }
}

internal class PChildSourceEntityData : PEntityData<PChildSourceEntity>() {
  lateinit var data: String
  override fun createEntity(snapshot: TypedEntityStorage): PChildSourceEntity {
    return PChildSourceEntity(data).also { addMetaData(it, snapshot) }
  }
}

internal class PChildSourceEntity(val data: String) : PTypedEntity() {
  val parent: PSourceEntity by ManyToOne.NotNull(PSourceEntity::class.java)
}

internal class ModifiablePChildSourceEntity : PModifiableTypedEntity<PChildSourceEntity>() {
  var data: String by EntityDataDelegation()
  var parent: PSourceEntity by MutableManyToOne.NotNull(PChildSourceEntity::class.java, PSourceEntity::class.java)
}

class EntitySourceIndexTest {
  @Test
  fun `base index test`() {
    val oldSource = PSampleEntitySource("oldSource")
    val newSource = PSampleEntitySource("newSource")
    val builder = PEntityStorageBuilder.create()
    val entity = builder.addPSourceEntity("hello", oldSource)
    assertEquals(entity.id, builder.indexes.entitySourceIndex.getIdsByEntry(oldSource)?.get(0))

    builder.changeSource(entity, newSource)
    assertNull(builder.indexes.entitySourceIndex.getIdsByEntry(oldSource))
    assertEquals(entity.id, builder.indexes.entitySourceIndex.getIdsByEntry(newSource)?.get(0))

    builder.removeEntity(entity)
    assertNull(builder.indexes.entitySourceIndex.getIdsByEntry(oldSource))
    assertNull(builder.indexes.entitySourceIndex.getIdsByEntry(newSource))
  }

  @Test
  fun `base index with diff test`() {
    val oldSource = PSampleEntitySource("oldSource")
    val newSource = PSampleEntitySource("newSource")
    val builder = PEntityStorageBuilder.create()
    val firstEntity = builder.addPSourceEntity("one", oldSource)
    assertEquals(firstEntity.id, builder.indexes.entitySourceIndex.getIdsByEntry(oldSource)?.get(0))

    val diff = PEntityStorageBuilder.from(builder.toStorage())
    assertEquals(firstEntity.id, diff.indexes.entitySourceIndex.getIdsByEntry(oldSource)?.get(0))
    assertNull(diff.indexes.entitySourceIndex.getIdsByEntry(newSource))

    val secondEntity = diff.addPSourceEntity("two", newSource)
    assertEquals(secondEntity.id, diff.indexes.entitySourceIndex.getIdsByEntry(newSource)?.get(0))
    assertEquals(firstEntity.id, diff.indexes.entitySourceIndex.getIdsByEntry(oldSource)?.get(0))
    assertNull(builder.indexes.entitySourceIndex.getIdsByEntry(newSource))

    builder.addDiff(diff)
    assertEquals(secondEntity.id, builder.indexes.entitySourceIndex.getIdsByEntry(newSource)?.get(0))
    assertEquals(firstEntity.id, builder.indexes.entitySourceIndex.getIdsByEntry(oldSource)?.get(0))
  }

  @Test
  fun `remove from diff test`() {
    val oldSource = PSampleEntitySource("oldSource")
    val builder = PEntityStorageBuilder.create()
    val firstEntity = builder.addPSourceEntity("one", oldSource)
    assertEquals(firstEntity.id, builder.indexes.entitySourceIndex.getIdsByEntry(oldSource)?.get(0))

    val diff = PEntityStorageBuilder.from(builder.toStorage())
    assertEquals(firstEntity.id, diff.indexes.entitySourceIndex.getIdsByEntry(oldSource)?.get(0))

    diff.removeEntity(firstEntity)
    assertEquals(firstEntity.id, builder.indexes.entitySourceIndex.getIdsByEntry(oldSource)?.get(0))
    assertNull(diff.indexes.entitySourceIndex.getIdsByEntry(oldSource))

    builder.addDiff(diff)
    assertNull(builder.indexes.entitySourceIndex.getIdsByEntry(oldSource))
  }

  @Test
  fun `change source in diff test`() {
    val oldSource = PSampleEntitySource("oldSource")
    val newSource = PSampleEntitySource("newSource")
    val builder = PEntityStorageBuilder.create()
    val firstEntity = builder.addPSourceEntity("one", oldSource)
    assertEquals(firstEntity.id, builder.indexes.entitySourceIndex.getIdsByEntry(oldSource)?.get(0))

    val diff = PEntityStorageBuilder.from(builder.toStorage())
    assertEquals(firstEntity.id, diff.indexes.entitySourceIndex.getIdsByEntry(oldSource)?.get(0))
    assertNull(diff.indexes.entitySourceIndex.getIdsByEntry(newSource))

    diff.changeSource(firstEntity, newSource)
    assertEquals(firstEntity.id, builder.indexes.entitySourceIndex.getIdsByEntry(oldSource)?.get(0))
    assertEquals(firstEntity.id, diff.indexes.entitySourceIndex.getIdsByEntry(newSource)?.get(0))
    assertNull(builder.indexes.entitySourceIndex.getIdsByEntry(newSource))

    builder.addDiff(diff)
    assertEquals(firstEntity.id, builder.indexes.entitySourceIndex.getIdsByEntry(newSource)?.get(0))
    assertNull(builder.indexes.entitySourceIndex.getIdsByEntry(oldSource))
  }

  @Test
  fun `remove entity with child`() {
    val entitySource = PSampleEntitySource("oldSource")
    val builder = PEntityStorageBuilder.create()
    val firstEntity = builder.addPSourceEntity("one", entitySource)
    builder.addEntity(ModifiablePChildSourceEntity::class.java, entitySource) {
      this.data = "firstChild"
      this.parent = firstEntity
    }

    var entities = builder.indexes.entitySourceIndex.getIdsByEntry(entitySource)
    assertEquals(2, entities?.size)

    builder.removeEntity(firstEntity)

    entities = builder.indexes.entitySourceIndex.getIdsByEntry(entitySource)
    assertNull(entities)
  }
}