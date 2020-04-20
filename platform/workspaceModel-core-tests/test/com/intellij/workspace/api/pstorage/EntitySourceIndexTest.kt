// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage

import com.intellij.workspace.api.EntitySource
import com.intellij.workspace.api.TypedEntityStorage
import com.intellij.workspace.api.TypedEntityStorageBuilder
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

class EntitySourceIndexTest {
  @Test
  fun `base index test`() {
    val oldSource = PSampleEntitySource("oldSource")
    val newSource = PSampleEntitySource("newSource")
    val builder = PEntityStorageBuilder.create()
    val entity = builder.addPSourceEntity("hello", oldSource)
    assertEquals(entity.id, builder.entitySourceIndex.getIdsByEntitySource(oldSource)?.get(0))

    builder.changeSource(entity, newSource)
    assertNull(builder.entitySourceIndex.getIdsByEntitySource(oldSource))
    assertEquals(entity.id, builder.entitySourceIndex.getIdsByEntitySource(newSource)?.get(0))

    builder.removeEntity(entity)
    assertNull(builder.entitySourceIndex.getIdsByEntitySource(oldSource))
    assertNull(builder.entitySourceIndex.getIdsByEntitySource(newSource))
  }

  @Test
  fun `base index with diff test`() {
    val oldSource = PSampleEntitySource("oldSource")
    val newSource = PSampleEntitySource("newSource")
    val builder = PEntityStorageBuilder.create()
    val firstEntity = builder.addPSourceEntity("one", oldSource)
    assertEquals(firstEntity.id, builder.entitySourceIndex.getIdsByEntitySource(oldSource)?.get(0))

    val diff = PEntityStorageBuilder.from(builder.toStorage())
    assertEquals(firstEntity.id, diff.entitySourceIndex.getIdsByEntitySource(oldSource)?.get(0))
    assertNull(diff.entitySourceIndex.getIdsByEntitySource(newSource))

    val secondEntity = diff.addPSourceEntity("two", newSource)
    assertEquals(secondEntity.id, diff.entitySourceIndex.getIdsByEntitySource(newSource)?.get(0))
    assertEquals(firstEntity.id, diff.entitySourceIndex.getIdsByEntitySource(oldSource)?.get(0))
    assertNull(builder.entitySourceIndex.getIdsByEntitySource(newSource))

    builder.addDiff(diff)
    assertEquals(secondEntity.id, builder.entitySourceIndex.getIdsByEntitySource(newSource)?.get(0))
    assertEquals(firstEntity.id, builder.entitySourceIndex.getIdsByEntitySource(oldSource)?.get(0))
  }

  @Test
  fun `remove from diff test`() {
    val oldSource = PSampleEntitySource("oldSource")
    val builder = PEntityStorageBuilder.create()
    val firstEntity = builder.addPSourceEntity("one", oldSource)
    assertEquals(firstEntity.id, builder.entitySourceIndex.getIdsByEntitySource(oldSource)?.get(0))

    val diff = PEntityStorageBuilder.from(builder.toStorage())
    assertEquals(firstEntity.id, diff.entitySourceIndex.getIdsByEntitySource(oldSource)?.get(0))

    diff.removeEntity(firstEntity)
    assertEquals(firstEntity.id, builder.entitySourceIndex.getIdsByEntitySource(oldSource)?.get(0))
    assertNull(diff.entitySourceIndex.getIdsByEntitySource(oldSource))

    builder.addDiff(diff)
    assertNull(builder.entitySourceIndex.getIdsByEntitySource(oldSource))
  }

  @Test
  fun `change source in diff test`() {
    val oldSource = PSampleEntitySource("oldSource")
    val newSource = PSampleEntitySource("newSource")
    val builder = PEntityStorageBuilder.create()
    val firstEntity = builder.addPSourceEntity("one", oldSource)
    assertEquals(firstEntity.id, builder.entitySourceIndex.getIdsByEntitySource(oldSource)?.get(0))

    val diff = PEntityStorageBuilder.from(builder.toStorage())
    assertEquals(firstEntity.id, diff.entitySourceIndex.getIdsByEntitySource(oldSource)?.get(0))
    assertNull(diff.entitySourceIndex.getIdsByEntitySource(newSource))

    diff.changeSource(firstEntity, newSource)
    assertEquals(firstEntity.id, builder.entitySourceIndex.getIdsByEntitySource(oldSource)?.get(0))
    assertEquals(firstEntity.id, diff.entitySourceIndex.getIdsByEntitySource(newSource)?.get(0))
    assertNull(builder.entitySourceIndex.getIdsByEntitySource(newSource))

    builder.addDiff(diff)
    assertEquals(firstEntity.id, builder.entitySourceIndex.getIdsByEntitySource(newSource)?.get(0))
    assertNull(builder.entitySourceIndex.getIdsByEntitySource(oldSource))
  }
}