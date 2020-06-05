// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage

import com.intellij.workspaceModel.storage.impl.WorkspaceEntityStorageBuilderImpl
import com.intellij.workspaceModel.storage.entities.SampleEntitySource
import com.intellij.workspaceModel.storage.entities.addSourceEntity
import org.junit.Assert.*
import org.junit.Test

class ExternalEntityMappingTest {
  companion object {
    private const val INDEX_ID = "ExternalEntityIndexTest"
  }

  @Test
  fun `base mapping test`() {
    val builder = WorkspaceEntityStorageBuilderImpl.create()

    val mapping = builder.getMutableExternalMapping<Int>(INDEX_ID)
    val entity = builder.addSourceEntity("hello", SampleEntitySource("source"))
    mapping.addMapping(entity, 1)
    mapping.addMapping(entity, 2)
    assertEquals(2, mapping.getDataByEntity(entity))
    mapping.removeMapping(entity)
    assertNull(mapping.getDataByEntity(entity))
    mapping.addMapping(entity, 3)
    assertEquals(3, mapping.getDataByEntity(entity))

    val storage = builder.toStorage()
    val newMapping = storage.getExternalMapping<Int>(INDEX_ID)
    assertEquals(3, newMapping.getDataByEntity(entity))
    assertEquals(entity, newMapping.getEntities(3).single())
  }

  @Test
  fun `update in diff test`() {
    val builder = WorkspaceEntityStorageBuilderImpl.create()

    val mapping = builder.getMutableExternalMapping<Int>(INDEX_ID)
    val entity = builder.addSourceEntity("hello", SampleEntitySource("source"))
    mapping.addMapping(entity, 1)
    assertEquals(1, mapping.getDataByEntity(entity))

    val diff = WorkspaceEntityStorageBuilderImpl.from(builder.toStorage())
    val diffMapping = diff.getMutableExternalMapping<Int>(INDEX_ID)
    assertNotEquals(mapping, diffMapping)
    assertEquals(1, diffMapping.getDataByEntity(entity))
    diffMapping.addMapping(entity, 2)
    assertEquals(1, mapping.getDataByEntity(entity))
    assertEquals(2, diffMapping.getDataByEntity(entity))

    builder.addDiff(diff)
    assertEquals(2, mapping.getDataByEntity(entity))

    val storage = builder.toStorage()
    val newMapping = storage.getExternalMapping<Int>(INDEX_ID)
    assertNotEquals(mapping, newMapping)
    assertEquals(2, newMapping.getDataByEntity(entity))
  }

  @Test
  fun `remove from diff test`() {
    val builder = WorkspaceEntityStorageBuilderImpl.create()

    val mapping = builder.getMutableExternalMapping<Int>(INDEX_ID)
    val entity = builder.addSourceEntity("hello", SampleEntitySource("source"))
    mapping.addMapping(entity, 1)
    assertEquals(1, mapping.getDataByEntity(entity))

    val diff = WorkspaceEntityStorageBuilderImpl.from(builder.toStorage())
    val diffMapping = diff.getMutableExternalMapping<Int>(INDEX_ID)
    assertNotEquals(mapping, diffMapping)
    assertEquals(1, diffMapping.getDataByEntity(entity))
    diffMapping.removeMapping(entity)
    assertEquals(1, mapping.getDataByEntity(entity))
    assertNull(diffMapping.getDataByEntity(entity))

    builder.addDiff(diff)
    assertNull(mapping.getDataByEntity(entity))

    val storage = builder.toStorage()
    val newMapping = storage.getExternalMapping<Int>(INDEX_ID)
    assertNotNull(newMapping)
    assertNotEquals(mapping, newMapping)
    assertNull(newMapping.getDataByEntity(entity))
  }

  @Test
  fun `add to diff test`() {
    val builder = WorkspaceEntityStorageBuilderImpl.create()

    val mapping = builder.getMutableExternalMapping<Int>(INDEX_ID)
    val entity = builder.addSourceEntity("hello", SampleEntitySource("source"))
    mapping.addMapping(entity, 1)
    assertEquals(1, mapping.getDataByEntity(entity))

    val diff = WorkspaceEntityStorageBuilderImpl.from(builder.toStorage())
    val newEntity = builder.addSourceEntity("world", SampleEntitySource("source"))
    val diffMapping = diff.getMutableExternalMapping<Int>(INDEX_ID)
    assertNotEquals(mapping, diffMapping)
    assertEquals(1, diffMapping.getDataByEntity(entity))
    diffMapping.addMapping(newEntity, 2)
    assertEquals(1, mapping.getDataByEntity(entity))
    assertEquals(1, diffMapping.getDataByEntity(entity))
    assertEquals(2, diffMapping.getDataByEntity(newEntity))

    builder.addDiff(diff)
    assertEquals(1, mapping.getDataByEntity(entity))
    assertEquals(2, mapping.getDataByEntity(newEntity))

    val storage = builder.toStorage()
    val newMapping = storage.getExternalMapping<Int>(INDEX_ID)
    assertNotEquals(mapping, newMapping)
    assertEquals(1, newMapping.getDataByEntity(entity))
    assertEquals(2, newMapping.getDataByEntity(newEntity))
    assertEquals(entity, newMapping.getEntities(1).single())
    assertEquals(newEntity, newMapping.getEntities(2).single())
  }

  @Test
  fun `remove mapping from diff test`() {
    val builder = WorkspaceEntityStorageBuilderImpl.create()

    val mapping = builder.getMutableExternalMapping<Int>(INDEX_ID)
    val entity = builder.addSourceEntity("hello", SampleEntitySource("source"))
    mapping.addMapping(entity, 1)
    assertEquals(1, mapping.getDataByEntity(entity))

    val diff = WorkspaceEntityStorageBuilderImpl.from(builder.toStorage())
    diff.removeExternalMapping(INDEX_ID)
    assertNull(diff.getExternalMapping<Int>(INDEX_ID).getDataByEntity(entity))
    assertEquals(1, mapping.getDataByEntity(entity))

    builder.addDiff(diff)
    assertEquals(1, mapping.getDataByEntity(entity))
    assertNull(builder.getExternalMapping<Int>(INDEX_ID).getDataByEntity(entity))

    val storage = builder.toStorage()
    assertNull(storage.getExternalMapping<Int>(INDEX_ID).getDataByEntity(entity))
  }

  @Test
  fun `add mapping to diff test`() {
    val builder = WorkspaceEntityStorageBuilderImpl.create()

    val entity = builder.addSourceEntity("hello", SampleEntitySource("source"))
    assertNull(builder.getExternalMapping<Int>(INDEX_ID).getDataByEntity(entity))

    val diff = WorkspaceEntityStorageBuilderImpl.from(builder.toStorage())
    val diffMapping = diff.getMutableExternalMapping<Int>(INDEX_ID)
    diffMapping.addMapping(entity, 1)
    assertNull(builder.getExternalMapping<Int>(INDEX_ID).getDataByEntity(entity))
    assertEquals(1, diffMapping.getDataByEntity(entity))

    builder.addDiff(diff)
    assertEquals(1, diffMapping.getDataByEntity(entity))
    val mapping = builder.getExternalMapping<Int>(INDEX_ID)
    assertNotEquals(diffMapping, mapping)
    assertEquals(1, mapping.getDataByEntity(entity))

    val storage = builder.toStorage()
    val newMapping = storage.getExternalMapping<Int>(INDEX_ID)
    assertNotEquals(mapping, newMapping)
    assertEquals(1, newMapping.getDataByEntity(entity))
  }
}