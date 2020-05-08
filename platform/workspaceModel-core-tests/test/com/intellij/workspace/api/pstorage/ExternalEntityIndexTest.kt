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
    assertEquals(2, index.getDataById(entity))
    index.remove(entity)
    assertNull(index.getDataById(entity))
    index.update(entity, 3)
    assertEquals(3, index.getDataById(entity))

    val storage = builder.toStorage()
    val newIndex = storage.getExternalIndex<Int>(INDEX_ID)
    assertNotNull(newIndex)
    assertEquals(3, newIndex!!.getDataById(entity))
    assertEquals(entity, newIndex.getIds(3)?.get(0))
  }

  @Test
  fun `update in diff test`() {
    val builder = PEntityStorageBuilder.create()

    val index = builder.getOrCreateExternalIndex<Int>(INDEX_ID)
    val entity = builder.addPSourceEntity("hello", PSampleEntitySource("source"))
    index.index(entity, 1)
    assertEquals(1, index.getDataById(entity))

    val diff = PEntityStorageBuilder.from(builder.toStorage())
    val externalIndex = diff.getExternalIndex<Int>(INDEX_ID)
    assertNotNull(externalIndex)
    assertNotEquals(index, externalIndex)
    assertEquals(1, externalIndex!!.getDataById(entity))
    externalIndex.update(entity, 2)
    assertEquals(1, index.getDataById(entity))
    assertEquals(2, externalIndex.getDataById(entity))

    builder.addDiff(diff)
    assertEquals(2, index.getDataById(entity))

    val storage = builder.toStorage()
    val newIndex = storage.getExternalIndex<Int>(INDEX_ID)
    assertNotNull(newIndex)
    assertNotEquals(index, newIndex)
    assertEquals(2, newIndex!!.getDataById(entity))
  }

  @Test
  fun `remove from diff test`() {
    val builder = PEntityStorageBuilder.create()

    val index = builder.getOrCreateExternalIndex<Int>(INDEX_ID)
    val entity = builder.addPSourceEntity("hello", PSampleEntitySource("source"))
    index.index(entity, 1)
    assertEquals(1, index.getDataById(entity))

    val diff = PEntityStorageBuilder.from(builder.toStorage())
    val externalIndex = diff.getExternalIndex<Int>(INDEX_ID)
    assertNotNull(externalIndex)
    assertNotEquals(index, externalIndex)
    assertEquals(1, externalIndex!!.getDataById(entity))
    externalIndex.remove(entity)
    assertEquals(1, index.getDataById(entity))
    assertNull(externalIndex.getDataById(entity))

    builder.addDiff(diff)
    assertNull(index.getDataById(entity))

    val storage = builder.toStorage()
    val newIndex = storage.getExternalIndex<Int>(INDEX_ID)
    assertNotNull(newIndex)
    assertNotEquals(index, newIndex)
    assertNull(newIndex!!.getDataById(entity))
  }

  @Test
  fun `add to diff test`() {
    val builder = PEntityStorageBuilder.create()

    val index = builder.getOrCreateExternalIndex<Int>(INDEX_ID)
    val entity = builder.addPSourceEntity("hello", PSampleEntitySource("source"))
    index.index(entity, 1)
    assertEquals(1, index.getDataById(entity))

    val diff = PEntityStorageBuilder.from(builder.toStorage())
    val newEntity = builder.addPSourceEntity("world", PSampleEntitySource("source"))
    val externalIndex = diff.getExternalIndex<Int>(INDEX_ID)
    assertNotNull(externalIndex)
    assertNotEquals(index, externalIndex)
    assertEquals(1, externalIndex!!.getDataById(entity))
    externalIndex.update(newEntity, 2)
    assertEquals(1, index.getDataById(entity))
    assertEquals(1, externalIndex.getDataById(entity))
    assertEquals(2, externalIndex.getDataById(newEntity))

    builder.addDiff(diff)
    assertEquals(1, index.getDataById(entity))
    assertEquals(2, index.getDataById(newEntity))

    val storage = builder.toStorage()
    val newIndex = storage.getExternalIndex<Int>(INDEX_ID)
    assertNotNull(newIndex)
    assertNotEquals(index, newIndex)
    assertEquals(1, newIndex!!.getDataById(entity))
    assertEquals(2, newIndex.getDataById(newEntity))
    assertEquals(entity, newIndex.getIds(1)?.get(0))
    assertEquals(newEntity, newIndex.getIds(2)?.get(0))
  }

  @Test
  fun `remove index from diff test`() {
    val builder = PEntityStorageBuilder.create()

    val index = builder.getOrCreateExternalIndex<Int>(INDEX_ID)
    val entity = builder.addPSourceEntity("hello", PSampleEntitySource("source"))
    index.index(entity, 1)
    assertEquals(1, index.getDataById(entity))

    val diff = PEntityStorageBuilder.from(builder.toStorage())
    diff.removeExternalIndex<Int>(INDEX_ID)
    assertNull(diff.getExternalIndex<Int>(INDEX_ID))
    assertEquals(1, index.getDataById(entity))

    builder.addDiff(diff)
    assertEquals(1, index.getDataById(entity))
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
    assertEquals(1, index.getDataById(entity))

    builder.addDiff(diff)
    assertEquals(1, index.getDataById(entity))
    val entityIndex = builder.getExternalIndex<Int>(INDEX_ID)
    assertNotNull(entityIndex)
    assertNotEquals(index, entityIndex)
    assertEquals(1, entityIndex!!.getDataById(entity))

    val storage = builder.toStorage()
    val newIndex = storage.getExternalIndex<Int>(INDEX_ID)
    assertNotNull(newIndex)
    assertNotEquals(entityIndex, newIndex)
    assertEquals(1, newIndex!!.getDataById(entity))
  }
}