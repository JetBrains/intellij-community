// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage

import com.intellij.workspaceModel.storage.entities.test.addSourceEntity
import com.intellij.workspaceModel.storage.entities.test.api.*
import com.intellij.workspaceModel.storage.impl.ClassToIntConverter
import com.intellij.workspaceModel.storage.impl.assertConsistency
import com.intellij.workspaceModel.storage.impl.createEntityId
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
    assertEquals((entity as SourceEntityImpl.Builder).id, builder.indexes.entitySourceIndex.getIdsByEntry(oldSource)?.single())

    builder.modifyEntity(entity) {
      this.entitySource = newSource
    }
    assertNull(builder.indexes.entitySourceIndex.getIdsByEntry(oldSource))
    assertEquals(entity.id, builder.indexes.entitySourceIndex.getIdsByEntry(newSource)?.single())

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
    assertEquals((firstEntity as SourceEntityImpl.Builder).id, builder.indexes.entitySourceIndex.getIdsByEntry(oldSource)?.single())

    val diff = createBuilderFrom(builder.toSnapshot())
    assertEquals(firstEntity.id, diff.indexes.entitySourceIndex.getIdsByEntry(oldSource)?.single())
    assertNull(diff.indexes.entitySourceIndex.getIdsByEntry(newSource))

    val secondEntity = diff.addSourceEntity("two", newSource)
    assertEquals((secondEntity as SourceEntityImpl.Builder).id, diff.indexes.entitySourceIndex.getIdsByEntry(newSource)?.single())
    assertEquals(firstEntity.id, diff.indexes.entitySourceIndex.getIdsByEntry(oldSource)?.single())
    assertNull(builder.indexes.entitySourceIndex.getIdsByEntry(newSource))

    builder.addDiff(diff)
    assertEquals(secondEntity.id, builder.indexes.entitySourceIndex.getIdsByEntry(newSource)?.single())
    assertEquals(firstEntity.id, builder.indexes.entitySourceIndex.getIdsByEntry(oldSource)?.single())
  }

  @Test
  fun `remove from diff test`() {
    val oldSource = SampleEntitySource("oldSource")
    val builder = createEmptyBuilder()
    val firstEntity = builder.addSourceEntity("one", oldSource)
    assertEquals((firstEntity as SourceEntityImpl.Builder).id, builder.indexes.entitySourceIndex.getIdsByEntry(oldSource)?.single())

    val diff = createBuilderFrom(builder.toSnapshot())
    assertEquals(firstEntity.id, diff.indexes.entitySourceIndex.getIdsByEntry(oldSource)?.single())

    diff.removeEntity(firstEntity.from(diff))
    assertEquals(firstEntity.id, builder.indexes.entitySourceIndex.getIdsByEntry(oldSource)?.single())
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
    assertEquals((firstEntity as SourceEntityImpl.Builder).id, builder.indexes.entitySourceIndex.getIdsByEntry(oldSource)?.single())

    val diff = createBuilderFrom(builder.toSnapshot())
    assertEquals(firstEntity.id, diff.indexes.entitySourceIndex.getIdsByEntry(oldSource)?.single())
    assertNull(diff.indexes.entitySourceIndex.getIdsByEntry(newSource))

    diff.modifyEntity(firstEntity.from(diff)) {
      this.entitySource = newSource
    }
    assertEquals(firstEntity.id, builder.indexes.entitySourceIndex.getIdsByEntry(oldSource)?.single())
    assertEquals(firstEntity.id, diff.indexes.entitySourceIndex.getIdsByEntry(newSource)?.single())
    assertNull(builder.indexes.entitySourceIndex.getIdsByEntry(newSource))

    builder.addDiff(diff)
    assertEquals(firstEntity.id, builder.indexes.entitySourceIndex.getIdsByEntry(newSource)?.single())
    assertNull(builder.indexes.entitySourceIndex.getIdsByEntry(oldSource))
  }

  @Test
  fun `remove entity with child`() {
    val entitySource = SampleEntitySource("oldSource")
    val builder = createEmptyBuilder()
    val firstEntity = builder.addSourceEntity("one", entitySource)
    val entity = ChildSourceEntity("firstChild", entitySource) {
      this.parentEntity = firstEntity
    }
    builder.addEntity(entity)

    var entities = builder.indexes.entitySourceIndex.getIdsByEntry(entitySource)
    assertEquals(2, entities?.size)

    builder.removeEntity(firstEntity)

    entities = builder.indexes.entitySourceIndex.getIdsByEntry(entitySource)
    assertNull(entities)
  }

  @Test(expected = AssertionError::class)
  fun `test incorrect index`() {
    val oldSource = SampleEntitySource("oldSource")
    val builder = createEmptyBuilder()
    builder.addSourceEntity("hello", oldSource)

    builder.indexes.entitySourceIndex.index(createEntityId(1, ClassToIntConverter.INSTANCE.getInt(SourceEntity::class.java)), oldSource)

    builder.assertConsistency()
  }

  @Test
  fun `add and change source`() {
    val oldSource = SampleEntitySource("oldSource")
    val builder = createEmptyBuilder()
    val entity = builder.addSourceEntity("one", oldSource)
    builder.modifyEntity(entity) {
      this.entitySource = SampleEntitySource("newSource")
    }
    builder.assertConsistency()
  }
}