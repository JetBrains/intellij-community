// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage

import org.junit.Assert.*
import org.junit.Test


class ExternalEntityIndexTest {
  companion object {
    private const val INDEX_ID = "ExternalEntityIndexTest"
  }

  @Test
  fun `base index test`() {
    val builder = PEntityStorageBuilder.create()

    val index = builder.getOrCreateExternalIndex<Int>(INDEX_ID)
    val entity = builder.addPSourceEntity("hello", PSampleEntitySource("source"))
    index.index(entity, 1)
    index.index(entity, 2)
    assertEquals(2, index.getDataByEntity(entity))
    index.remove(entity)
    assertNull(index.getDataByEntity(entity))
    index.index(entity, 3)
    assertEquals(3, index.getDataByEntity(entity))

    val storage = builder.toStorage()
    val newIndex = storage.getExternalIndex<Int>(INDEX_ID)
    assertNotNull(newIndex)
    assertEquals(3, newIndex!!.getDataByEntity(entity))
    assertEquals(entity, newIndex.getEntities(3)?.get(0))
  }

  @Test
  fun `update in diff test`() {
    val builder = PEntityStorageBuilder.create()

    val index = builder.getOrCreateExternalIndex<Int>(INDEX_ID)
    val entity = builder.addPSourceEntity("hello", PSampleEntitySource("source"))
    index.index(entity, 1)
    assertEquals(1, index.getDataByEntity(entity))

    val diff = PEntityStorageBuilder.from(builder.toStorage())
    val externalIndex = diff.getExternalIndex<Int>(INDEX_ID)
    assertNotNull(externalIndex)
    assertNotEquals(index, externalIndex)
    assertEquals(1, externalIndex!!.getDataByEntity(entity))
    externalIndex.index(entity, 2)
    assertEquals(1, index.getDataByEntity(entity))
    assertEquals(2, externalIndex.getDataByEntity(entity))

    builder.addDiff(diff)
    assertEquals(2, index.getDataByEntity(entity))

    val storage = builder.toStorage()
    val newIndex = storage.getExternalIndex<Int>(INDEX_ID)
    assertNotNull(newIndex)
    assertNotEquals(index, newIndex)
    assertEquals(2, newIndex!!.getDataByEntity(entity))
  }

  @Test
  fun `remove from diff test`() {
    val builder = PEntityStorageBuilder.create()

    val index = builder.getOrCreateExternalIndex<Int>(INDEX_ID)
    val entity = builder.addPSourceEntity("hello", PSampleEntitySource("source"))
    index.index(entity, 1)
    assertEquals(1, index.getDataByEntity(entity))

    val diff = PEntityStorageBuilder.from(builder.toStorage())
    val externalIndex = diff.getExternalIndex<Int>(INDEX_ID)
    assertNotNull(externalIndex)
    assertNotEquals(index, externalIndex)
    assertEquals(1, externalIndex!!.getDataByEntity(entity))
    externalIndex.remove(entity)
    assertEquals(1, index.getDataByEntity(entity))
    assertNull(externalIndex.getDataByEntity(entity))

    builder.addDiff(diff)
    assertNull(index.getDataByEntity(entity))

    val storage = builder.toStorage()
    val newIndex = storage.getExternalIndex<Int>(INDEX_ID)
    assertNotNull(newIndex)
    assertNotEquals(index, newIndex)
    assertNull(newIndex!!.getDataByEntity(entity))
  }

  @Test
  fun `add to diff test`() {
    val builder = PEntityStorageBuilder.create()

    val index = builder.getOrCreateExternalIndex<Int>(INDEX_ID)
    val entity = builder.addPSourceEntity("hello", PSampleEntitySource("source"))
    index.index(entity, 1)
    assertEquals(1, index.getDataByEntity(entity))

    val diff = PEntityStorageBuilder.from(builder.toStorage())
    val newEntity = builder.addPSourceEntity("world", PSampleEntitySource("source"))
    val externalIndex = diff.getExternalIndex<Int>(INDEX_ID)
    assertNotNull(externalIndex)
    assertNotEquals(index, externalIndex)
    assertEquals(1, externalIndex!!.getDataByEntity(entity))
    externalIndex.index(newEntity, 2)
    assertEquals(1, index.getDataByEntity(entity))
    assertEquals(1, externalIndex.getDataByEntity(entity))
    assertEquals(2, externalIndex.getDataByEntity(newEntity))

    builder.addDiff(diff)
    assertEquals(1, index.getDataByEntity(entity))
    assertEquals(2, index.getDataByEntity(newEntity))

    val storage = builder.toStorage()
    val newIndex = storage.getExternalIndex<Int>(INDEX_ID)
    assertNotNull(newIndex)
    assertNotEquals(index, newIndex)
    assertEquals(1, newIndex!!.getDataByEntity(entity))
    assertEquals(2, newIndex.getDataByEntity(newEntity))
    assertEquals(entity, newIndex.getEntities(1)?.get(0))
    assertEquals(newEntity, newIndex.getEntities(2)?.get(0))
  }

  @Test
  fun `remove index from diff test`() {
    val builder = PEntityStorageBuilder.create()

    val index = builder.getOrCreateExternalIndex<Int>(INDEX_ID)
    val entity = builder.addPSourceEntity("hello", PSampleEntitySource("source"))
    index.index(entity, 1)
    assertEquals(1, index.getDataByEntity(entity))

    val diff = PEntityStorageBuilder.from(builder.toStorage())
    diff.removeExternalIndex(INDEX_ID)
    assertNull(diff.getExternalIndex<Int>(INDEX_ID))
    assertEquals(1, index.getDataByEntity(entity))

    builder.addDiff(diff)
    assertEquals(1, index.getDataByEntity(entity))
    assertNull(builder.getExternalIndex<Int>(INDEX_ID))

    val storage = builder.toStorage()
    assertNull(storage.getExternalIndex<Int>(INDEX_ID))
  }

  @Test
  fun `add index to diff test`() {
    val builder = PEntityStorageBuilder.create()

    val entity = builder.addPSourceEntity("hello", PSampleEntitySource("source"))
    assertNull(builder.getExternalIndex<Int>(INDEX_ID))

    val diff = PEntityStorageBuilder.from(builder.toStorage())
    val index = diff.getOrCreateExternalIndex<Int>(INDEX_ID)
    index.index(entity, 1)
    assertNull(builder.getExternalIndex<Int>(INDEX_ID))
    assertEquals(1, index.getDataByEntity(entity))

    builder.addDiff(diff)
    assertEquals(1, index.getDataByEntity(entity))
    val entityIndex = builder.getExternalIndex<Int>(INDEX_ID)
    assertNotNull(entityIndex)
    assertNotEquals(index, entityIndex)
    assertEquals(1, entityIndex!!.getDataByEntity(entity))

    val storage = builder.toStorage()
    val newIndex = storage.getExternalIndex<Int>(INDEX_ID)
    assertNotNull(newIndex)
    assertNotEquals(entityIndex, newIndex)
    assertEquals(1, newIndex!!.getDataByEntity(entity))
  }
}