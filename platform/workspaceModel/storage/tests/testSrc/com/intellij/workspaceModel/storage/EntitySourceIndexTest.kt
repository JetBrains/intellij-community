// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage

import com.intellij.workspaceModel.storage.entities.ModifiableChildSourceEntity
import com.intellij.workspaceModel.storage.entities.SampleEntitySource
import com.intellij.workspaceModel.storage.entities.addSourceEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EntitySourceIndexTest {
  @Test
  fun `base index test`() {
    val oldSource = SampleEntitySource("oldSource")
    val newSource = SampleEntitySource("newSource")
    val builder = createEmptyBuilder()
    val entity = builder.addSourceEntity("hello", oldSource)
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
    val oldSource = SampleEntitySource("oldSource")
    val newSource = SampleEntitySource("newSource")
    val builder = createEmptyBuilder()
    val firstEntity = builder.addSourceEntity("one", oldSource)
    assertEquals(firstEntity.id, builder.indexes.entitySourceIndex.getIdsByEntry(oldSource)?.get(0))

    val diff = createBuilderFrom(builder.toStorage())
    assertEquals(firstEntity.id, diff.indexes.entitySourceIndex.getIdsByEntry(oldSource)?.get(0))
    assertNull(diff.indexes.entitySourceIndex.getIdsByEntry(newSource))

    val secondEntity = diff.addSourceEntity("two", newSource)
    assertEquals(secondEntity.id, diff.indexes.entitySourceIndex.getIdsByEntry(newSource)?.get(0))
    assertEquals(firstEntity.id, diff.indexes.entitySourceIndex.getIdsByEntry(oldSource)?.get(0))
    assertNull(builder.indexes.entitySourceIndex.getIdsByEntry(newSource))

    builder.addDiff(diff)
    assertEquals(secondEntity.id, builder.indexes.entitySourceIndex.getIdsByEntry(newSource)?.get(0))
    assertEquals(firstEntity.id, builder.indexes.entitySourceIndex.getIdsByEntry(oldSource)?.get(0))
  }

  @Test
  fun `remove from diff test`() {
    val oldSource = SampleEntitySource("oldSource")
    val builder = createEmptyBuilder()
    val firstEntity = builder.addSourceEntity("one", oldSource)
    assertEquals(firstEntity.id, builder.indexes.entitySourceIndex.getIdsByEntry(oldSource)?.get(0))

    val diff = createBuilderFrom(builder.toStorage())
    assertEquals(firstEntity.id, diff.indexes.entitySourceIndex.getIdsByEntry(oldSource)?.get(0))

    diff.removeEntity(firstEntity)
    assertEquals(firstEntity.id, builder.indexes.entitySourceIndex.getIdsByEntry(oldSource)?.get(0))
    assertNull(diff.indexes.entitySourceIndex.getIdsByEntry(oldSource))

    builder.addDiff(diff)
    assertNull(builder.indexes.entitySourceIndex.getIdsByEntry(oldSource))
  }

  @Test
  fun `change source in diff test`() {
    val oldSource = SampleEntitySource("oldSource")
    val newSource = SampleEntitySource("newSource")
    val builder = createEmptyBuilder()
    val firstEntity = builder.addSourceEntity("one", oldSource)
    assertEquals(firstEntity.id, builder.indexes.entitySourceIndex.getIdsByEntry(oldSource)?.get(0))

    val diff = createBuilderFrom(builder.toStorage())
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
    val entitySource = SampleEntitySource("oldSource")
    val builder = createEmptyBuilder()
    val firstEntity = builder.addSourceEntity("one", entitySource)
    builder.addEntity(ModifiableChildSourceEntity::class.java, entitySource) {
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