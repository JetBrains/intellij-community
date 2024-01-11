// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests

import com.intellij.platform.workspace.storage.impl.ClassToIntConverter
import com.intellij.platform.workspace.storage.impl.assertConsistency
import com.intellij.platform.workspace.storage.impl.createEntityId
import com.intellij.platform.workspace.storage.testEntities.entities.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class EntitySourceIndexTest {
  @Test
  fun `base index test`() {
    val oldSource = SampleEntitySource("oldSource")
    val newSource = SampleEntitySource("newSource")
    val builder = createEmptyBuilder()
    val entity = builder addEntity SourceEntity("hello", oldSource)
    assertEquals((entity as SourceEntityImpl.Builder).id, builder.indexes.entitySourceIndex.getIdsByEntry(oldSource)?.single())

    builder.modifyEntity(entity) {
      this.entitySource = newSource
    }
    Assertions.assertNull(builder.indexes.entitySourceIndex.getIdsByEntry(oldSource))
    assertEquals(entity.id, builder.indexes.entitySourceIndex.getIdsByEntry(newSource)?.single())

    builder.removeEntity(entity)
    Assertions.assertNull(builder.indexes.entitySourceIndex.getIdsByEntry(oldSource))
    Assertions.assertNull(builder.indexes.entitySourceIndex.getIdsByEntry(newSource))
  }

  @Test
  fun `base index with diff test`() {
    val oldSource = SampleEntitySource("oldSource")
    val newSource = SampleEntitySource("newSource")
    val builder = createEmptyBuilder()
    val firstEntity = builder addEntity SourceEntity("one", oldSource)
    assertEquals((firstEntity as SourceEntityImpl.Builder).id,
                            builder.indexes.entitySourceIndex.getIdsByEntry(oldSource)?.single())

    val diff = createBuilderFrom(builder.toSnapshot())
    assertEquals(firstEntity.id, diff.indexes.entitySourceIndex.getIdsByEntry(oldSource)?.single())
    Assertions.assertNull(diff.indexes.entitySourceIndex.getIdsByEntry(newSource))

    val secondEntity = diff addEntity SourceEntity("two", newSource)
    assertEquals((secondEntity as SourceEntityImpl.Builder).id,
                            diff.indexes.entitySourceIndex.getIdsByEntry(newSource)?.single())
    assertEquals(firstEntity.id, diff.indexes.entitySourceIndex.getIdsByEntry(oldSource)?.single())
    Assertions.assertNull(builder.indexes.entitySourceIndex.getIdsByEntry(newSource))

    builder.applyChangesFrom(diff)
    assertEquals(secondEntity.id, builder.indexes.entitySourceIndex.getIdsByEntry(newSource)?.single())
    assertEquals(firstEntity.id, builder.indexes.entitySourceIndex.getIdsByEntry(oldSource)?.single())
  }

  @Test
  fun `remove from diff test`() {
    val oldSource = SampleEntitySource("oldSource")
    val builder = createEmptyBuilder()
    val firstEntity = builder addEntity SourceEntity("one", oldSource)
    assertEquals((firstEntity as SourceEntityImpl.Builder).id,
                            builder.indexes.entitySourceIndex.getIdsByEntry(oldSource)?.single())

    val diff = createBuilderFrom(builder.toSnapshot())
    assertEquals(firstEntity.id, diff.indexes.entitySourceIndex.getIdsByEntry(oldSource)?.single())

    diff.removeEntity(firstEntity.from(diff))
    assertEquals(firstEntity.id, builder.indexes.entitySourceIndex.getIdsByEntry(oldSource)?.single())
    Assertions.assertNull(diff.indexes.entitySourceIndex.getIdsByEntry(oldSource))

    builder.applyChangesFrom(diff)
    Assertions.assertNull(builder.indexes.entitySourceIndex.getIdsByEntry(oldSource))
  }

  @Test
  fun `change source in diff test`() {
    val oldSource = SampleEntitySource("oldSource")
    val newSource = SampleEntitySource("newSource")
    val builder = createEmptyBuilder()
    val firstEntity = builder addEntity SourceEntity("one", oldSource)
    assertEquals((firstEntity as SourceEntityImpl.Builder).id,
                            builder.indexes.entitySourceIndex.getIdsByEntry(oldSource)?.single())

    val diff = createBuilderFrom(builder.toSnapshot())
    assertEquals(firstEntity.id, diff.indexes.entitySourceIndex.getIdsByEntry(oldSource)?.single())
    Assertions.assertNull(diff.indexes.entitySourceIndex.getIdsByEntry(newSource))

    diff.modifyEntity(firstEntity.from(diff)) {
      this.entitySource = newSource
    }
    assertEquals(firstEntity.id, builder.indexes.entitySourceIndex.getIdsByEntry(oldSource)?.single())
    assertEquals(firstEntity.id, diff.indexes.entitySourceIndex.getIdsByEntry(newSource)?.single())
    Assertions.assertNull(builder.indexes.entitySourceIndex.getIdsByEntry(newSource))

    builder.applyChangesFrom(diff)
    assertEquals(firstEntity.id, builder.indexes.entitySourceIndex.getIdsByEntry(newSource)?.single())
    Assertions.assertNull(builder.indexes.entitySourceIndex.getIdsByEntry(oldSource))
  }

  @Test
  fun `remove entity with child`() {
    val entitySource = SampleEntitySource("oldSource")
    val builder = createEmptyBuilder()
    val firstEntity = builder addEntity SourceEntity("one", entitySource)
    val entity = ChildSourceEntity("firstChild", entitySource) {
      this.parentEntity = firstEntity
    }
    builder.addEntity(entity)

    var entities = builder.indexes.entitySourceIndex.getIdsByEntry(entitySource)
    assertEquals(2, entities?.size)

    builder.removeEntity(firstEntity)

    entities = builder.indexes.entitySourceIndex.getIdsByEntry(entitySource)
    Assertions.assertNull(entities)
  }

  @Test
  fun `test incorrect index`() {
    val oldSource = SampleEntitySource("oldSource")
    val builder = createEmptyBuilder()
    builder addEntity SourceEntity("hello", oldSource)

    builder.indexes.entitySourceIndex.index(createEntityId(1, ClassToIntConverter.getInstance().getInt(SourceEntity::class.java)), oldSource)

    assertThrows<AssertionError> {
      builder.assertConsistency()
    }
  }

  @Test
  fun `add and change source`() {
    val oldSource = SampleEntitySource("oldSource")
    val builder = createEmptyBuilder()
    val entity = builder addEntity SourceEntity("one", oldSource)
    builder.modifyEntity(entity) {
      this.entitySource = SampleEntitySource("newSource")
    }
    builder.assertConsistency()
  }
}