// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage

import com.intellij.testFramework.UsefulTestCase.assertEmpty
import com.intellij.workspaceModel.storage.entities.ModifiableSampleEntity
import com.intellij.workspaceModel.storage.entities.SampleEntity
import com.intellij.workspaceModel.storage.entities.SampleEntitySource
import com.intellij.workspaceModel.storage.entities.addSampleEntity
import com.intellij.workspaceModel.storage.entities.addSourceEntity
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityStorageBuilderImpl
import com.intellij.workspaceModel.storage.impl.external.ExternalEntityMappingImpl
import org.junit.Assert.*
import org.junit.Test

class ExternalEntityMappingTest {
  companion object {
    private const val INDEX_ID = "ExternalEntityIndexTest"
    private const val ANOTHER_INDEX_ID = "AnotherExternalEntityIndexTest"
  }

  @Test
  fun `base mapping test`() {
    val builder = WorkspaceEntityStorageBuilderImpl.create()

    val mapping = builder.getMutableExternalMapping<Int>(INDEX_ID)
    val entity = builder.addSourceEntity("hello", SampleEntitySource("source"))
    mapping.addMapping(entity, 1)
    mapping.addMapping(entity, 2)
    assertEquals(2, mapping.getDataByEntity(entity))
    val countBeforeRemove = builder.modificationCount
    mapping.removeMapping(entity)
    assertTrue(builder.modificationCount > countBeforeRemove)
    assertNull(mapping.getDataByEntity(entity))
    val countBeforeAdd = builder.modificationCount
    mapping.addMapping(entity, 3)
    assertTrue(builder.modificationCount > countBeforeAdd)
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
    assertNull(mapping.getDataByEntity(entity))
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

  @Test
  fun `remove mapping if entity is removed`() {
    val initialBuilder = WorkspaceEntityStorageBuilder.create()
    val entity1 = initialBuilder.addSampleEntity("1")
    initialBuilder.getMutableExternalMapping<Int>(INDEX_ID).addMapping(entity1, 1)
    val entity2 = initialBuilder.addSampleEntity("2")
    initialBuilder.getMutableExternalMapping<Int>(INDEX_ID).addMapping(entity2, 2)
    val storage = initialBuilder.toStorage()
    assertEquals(1, storage.getExternalMapping<Int>(INDEX_ID).getDataByEntity(entity1))
    assertEquals(2, storage.getExternalMapping<Int>(INDEX_ID).getDataByEntity(entity2))

    val builder = WorkspaceEntityStorageBuilder.from(storage)
    val entity3 = builder.addSampleEntity("3")
    builder.getMutableExternalMapping<Int>(INDEX_ID).addMapping(entity3, 3)
    builder.removeEntity(entity1)
    val diff = WorkspaceEntityStorageDiffBuilder.create(storage)
    diff.removeEntity(entity2)
    builder.addDiff(diff)
    builder.removeEntity(entity3)

    fun checkStorage(storage: WorkspaceEntityStorage) {
      val mapping = storage.getExternalMapping<Int>(INDEX_ID)
      assertNull(mapping.getDataByEntity(entity1))
      assertNull(mapping.getDataByEntity(entity2))
      assertNull(mapping.getDataByEntity(entity3))
      assertEmpty(mapping.getEntities(1))
      assertEmpty(mapping.getEntities(2))
      assertEmpty(mapping.getEntities(3))
    }
    checkStorage(builder)
    checkStorage(builder.toStorage())
  }

  @Test
  fun `keep mapping if entity is modified`() {
    val initialBuilder = WorkspaceEntityStorageBuilder.create()
    val entity1 = initialBuilder.addSampleEntity("1")
    initialBuilder.getMutableExternalMapping<Int>(INDEX_ID).addMapping(entity1, 1)
    val entity2 = initialBuilder.addSampleEntity("2")
    initialBuilder.getMutableExternalMapping<Int>(INDEX_ID).addMapping(entity2, 2)
    val storage = initialBuilder.toStorage()
    assertEquals(1, storage.getExternalMapping<Int>(INDEX_ID).getDataByEntity(entity1))
    assertEquals(2, storage.getExternalMapping<Int>(INDEX_ID).getDataByEntity(entity2))

    val builder = WorkspaceEntityStorageBuilder.from(storage)
    val entity3 = builder.addSampleEntity("3")
    builder.getMutableExternalMapping<Int>(INDEX_ID).addMapping(entity3, 3)
    val entity1a = builder.modifyEntity(ModifiableSampleEntity::class.java, entity1) {
      stringProperty = "1a"
    }
    val diff = WorkspaceEntityStorageDiffBuilder.create(storage)
    val entity2a = diff.modifyEntity(ModifiableSampleEntity::class.java, entity2) {
      stringProperty = "2a"
    }
    builder.addDiff(diff)
    val entity3a = builder.modifyEntity(ModifiableSampleEntity::class.java, entity3) {
      stringProperty = "3a"
    }

    fun checkStorage(storage: WorkspaceEntityStorage) {
      val mapping = storage.getExternalMapping<Int>(INDEX_ID)
      assertEquals(1, mapping.getDataByEntity(entity1a))
      assertEquals(2, mapping.getDataByEntity(entity2a))
      assertEquals(3, mapping.getDataByEntity(entity3a))
      assertEquals("1a", (mapping.getEntities(1).single() as SampleEntity).stringProperty)
      assertEquals("2a", (mapping.getEntities(2).single() as SampleEntity).stringProperty)
      assertEquals("3a", (mapping.getEntities(3).single() as SampleEntity).stringProperty)
    }

    checkStorage(builder)
    checkStorage(builder.toStorage())
  }

  @Test
  fun `update mapping when id changes on adding via diff`() {
    val builder = WorkspaceEntityStorageBuilder.create()
    val diff = WorkspaceEntityStorageDiffBuilder.create(builder.toStorage())
    val entity1 = builder.addSampleEntity("1")
    builder.getMutableExternalMapping<Int>(INDEX_ID).addMapping(entity1, 1)
    val entity2 = diff.addSampleEntity("2")
    diff.getMutableExternalMapping<Int>(INDEX_ID).addMapping(entity2, 2)
    builder.addDiff(diff)
    val builderMapping = builder.getExternalMapping<Int>(INDEX_ID)
    val entities = builder.entities(SampleEntity::class.java).sortedBy { it.stringProperty }.toList()
    assertEquals(1, builderMapping.getDataByEntity(entities[0]))
    assertEquals(2, builderMapping.getDataByEntity(entities[1]))

    val storage = builder.toStorage()
    val storageMapping = storage.getExternalMapping<Int>(INDEX_ID)
    val entitiesFromStorage = storage.entities(SampleEntity::class.java).sortedBy { it.stringProperty }.toList()
    assertEquals(1, builderMapping.getDataByEntity(entitiesFromStorage[0]))
    assertEquals(2, builderMapping.getDataByEntity(entitiesFromStorage[1]))
  }

  @Test
  fun `merge mapping added after builder was created`() {
    val initialBuilder = WorkspaceEntityStorageBuilder.create()
    initialBuilder.addSampleEntity("foo")
    val initialStorage = initialBuilder.toStorage()
    val diff1 = WorkspaceEntityStorageDiffBuilder.create(initialStorage)
    diff1.addSampleEntity("bar")

    val diff2 = WorkspaceEntityStorageDiffBuilder.create(initialStorage)
    diff2.getMutableExternalMapping<Int>(INDEX_ID).addMapping(initialStorage.singleSampleEntity(), 1)
    val updatedBuilder = WorkspaceEntityStorageBuilder.from(initialStorage)
    updatedBuilder.addDiff(diff2)
    val updatedStorage = updatedBuilder.toStorage()

    val newBuilder = WorkspaceEntityStorageBuilder.from(updatedStorage)
    newBuilder.addDiff(diff1)
    val newStorage = newBuilder.toStorage()
    val entities = newStorage.entities(SampleEntity::class.java).sortedByDescending { it.stringProperty }.toList()
    assertEquals(2, entities.size)
    val (foo, bar) = entities
    assertEquals("foo", foo.stringProperty)
    assertEquals("bar", bar.stringProperty)
    assertEquals(1, newStorage.getExternalMapping<Int>(INDEX_ID).getDataByEntity(foo))
  }

  @Test
  fun `replace by source add new mapping`() {
    val initialBuilder = WorkspaceEntityStorageBuilder.create()
    initialBuilder.addSampleEntity("foo")

    val replacement = WorkspaceEntityStorageBuilder.from(initialBuilder)
    val entity = initialBuilder.singleSampleEntity()
    replacement.getMutableExternalMapping<Int>(INDEX_ID).addMapping(entity, 1)
    initialBuilder.replaceBySource({ it is SampleEntitySource }, replacement)
    assertEquals(1, initialBuilder.getExternalMapping<Int>(INDEX_ID).getDataByEntity(entity))
  }

  @Test
  fun `replace by source add new mapping with new entity`() {
    val initialBuilder = WorkspaceEntityStorageBuilder.create()
    val fooEntity = initialBuilder.addSampleEntity("foo")

    val replacement = WorkspaceEntityStorageBuilder.from(initialBuilder)
    val barEntity = replacement.addSampleEntity("bar")
    var externalMapping = replacement.getMutableExternalMapping<Int>(INDEX_ID)
    externalMapping.addMapping(fooEntity, 1)
    externalMapping = replacement.getMutableExternalMapping<Int>(ANOTHER_INDEX_ID)
    externalMapping.addMapping(barEntity, 2)
    initialBuilder.replaceBySource({ it is SampleEntitySource }, replacement)

    assertEquals(1, initialBuilder.getExternalMapping<Int>(INDEX_ID).getDataByEntity(fooEntity))
    assertEquals(2, initialBuilder.getExternalMapping<Int>(ANOTHER_INDEX_ID).getDataByEntity(barEntity))
    assertNull(initialBuilder.getExternalMapping<Int>(INDEX_ID).getDataByEntity(barEntity))
  }

  @Test
  fun `replace by source update mapping for old entity`() {
    val initialBuilder = WorkspaceEntityStorageBuilder.create()
    val fooEntity = initialBuilder.addSampleEntity("foo")
    var externalMapping = initialBuilder.getMutableExternalMapping<Int>(INDEX_ID)
    externalMapping.addMapping(fooEntity, 1)

    val replacement = WorkspaceEntityStorageBuilder.from(initialBuilder)
    externalMapping = replacement.getMutableExternalMapping(INDEX_ID)
    externalMapping.addMapping(fooEntity, 2)
    initialBuilder.replaceBySource({ it is SampleEntitySource }, replacement)

    assertEquals(2, initialBuilder.getExternalMapping<Int>(INDEX_ID).getDataByEntity(fooEntity))
  }

  @Test
  fun `replace by source update mapping for new entity`() {
    val initialBuilder = WorkspaceEntityStorageBuilder.create()
    val fooEntity = initialBuilder.addSampleEntity("foo")
    var externalMapping = initialBuilder.getMutableExternalMapping<Int>(INDEX_ID)
    externalMapping.addMapping(fooEntity, 1)

    val replacement = WorkspaceEntityStorageBuilder.from(initialBuilder)
    val barEntity = replacement.addSampleEntity("bar")
    externalMapping = replacement.getMutableExternalMapping(INDEX_ID)
    externalMapping.addMapping(barEntity, 2)
    initialBuilder.replaceBySource({ it is SampleEntitySource }, replacement)

    assertEquals(1, initialBuilder.getExternalMapping<Int>(INDEX_ID).getDataByEntity(fooEntity))
    assertEquals(2, initialBuilder.getExternalMapping<Int>(INDEX_ID).getDataByEntity(barEntity))
  }

  @Test
  fun `replace by source remove from mapping`() {
    val initialBuilder = WorkspaceEntityStorageBuilder.create()
    val fooEntity = initialBuilder.addSampleEntity("foo")
    var externalMapping = initialBuilder.getMutableExternalMapping<Int>(INDEX_ID)
    externalMapping.addMapping(fooEntity, 1)

    val replacement = WorkspaceEntityStorageBuilder.from(initialBuilder)
    val barEntity = replacement.addSampleEntity("bar")
    externalMapping = replacement.getMutableExternalMapping(INDEX_ID)
    externalMapping.addMapping(barEntity, 2)
    externalMapping.removeMapping(barEntity)
    externalMapping.removeMapping(fooEntity)
    initialBuilder.replaceBySource({ it is SampleEntitySource }, replacement)

    assertEquals(1, initialBuilder.getExternalMapping<Int>(INDEX_ID).getDataByEntity(fooEntity))
    assertNull(initialBuilder.getExternalMapping<Int>(INDEX_ID).getDataByEntity(barEntity))
  }

  @Test
  fun `replace by source cleanup mapping by entity remove`() {
    val initialBuilder = WorkspaceEntityStorageBuilder.create()
    val fooEntity = initialBuilder.addSampleEntity("foo")
    val externalMapping = initialBuilder.getMutableExternalMapping<Int>(INDEX_ID)
    externalMapping.addMapping(fooEntity, 1)

    val replacement = WorkspaceEntityStorageBuilder.from(initialBuilder)
    replacement.removeEntity(fooEntity)
    assertNull(replacement.getMutableExternalMapping<Int>(INDEX_ID).getDataByEntity(fooEntity))
    initialBuilder.replaceBySource({ it is SampleEntitySource }, replacement)

    assertNull(initialBuilder.getExternalMapping<Int>(INDEX_ID).getDataByEntity(fooEntity))
  }

  @Test
  fun `replace by source replace one mapping to another`() {
    val initialBuilder = WorkspaceEntityStorageBuilder.create()
    val fooEntity = initialBuilder.addSampleEntity("foo")
    var externalMapping = initialBuilder.getMutableExternalMapping<Int>(INDEX_ID)
    externalMapping.addMapping(fooEntity, 1)

    val replacement = WorkspaceEntityStorageBuilder.create()
    var barEntity = replacement.addSampleEntity("bar")
    externalMapping = replacement.getMutableExternalMapping<Int>(ANOTHER_INDEX_ID)
    externalMapping.addMapping(barEntity, 2)
    initialBuilder.replaceBySource({ it is SampleEntitySource }, replacement)

    barEntity = initialBuilder.entities(SampleEntity::class.java).first { it.stringProperty == "bar" }
    assertEquals(2, initialBuilder.getExternalMapping<Int>(ANOTHER_INDEX_ID).getDataByEntity(barEntity))
    val mapping = initialBuilder.getExternalMapping<String>(INDEX_ID) as ExternalEntityMappingImpl
    assertEquals(0, mapping.size())
  }

  @Test
  fun `replace by source replace mappings`() {
    val initialBuilder = WorkspaceEntityStorageBuilder.create()
    val fooEntity = initialBuilder.addSampleEntity("foo")
    var externalMapping = initialBuilder.getMutableExternalMapping<Int>(INDEX_ID)
    externalMapping.addMapping(fooEntity, 1)

    val replacement = WorkspaceEntityStorageBuilder.create()
    var barEntity = replacement.addSampleEntity("bar")
    externalMapping = replacement.getMutableExternalMapping<Int>(INDEX_ID)
    externalMapping.addMapping(barEntity, 2)
    initialBuilder.replaceBySource({ it is SampleEntitySource }, replacement)

    barEntity = initialBuilder.entities(SampleEntity::class.java).first { it.stringProperty == "bar" }

    val mapping = initialBuilder.getExternalMapping<Int>(INDEX_ID) as ExternalEntityMappingImpl
    assertEquals(1, mapping.size())
    assertEquals(2, mapping.getDataByEntity(barEntity))
  }

  @Test
  fun `replace by source update mapping content and type`() {
    val initialBuilder = WorkspaceEntityStorageBuilder.create()
    var fooEntity = initialBuilder.addSampleEntity("foo")
    val externalMapping = initialBuilder.getMutableExternalMapping<Int>(INDEX_ID)
    externalMapping.addMapping(fooEntity, 1)

    val replacement = WorkspaceEntityStorageBuilder.create()
    val secondFooEntity = replacement.addSampleEntity("foo")
    val newExternalMapping = replacement.getMutableExternalMapping<String>(INDEX_ID)
    newExternalMapping.addMapping(secondFooEntity, "test")
    initialBuilder.replaceBySource({ it is SampleEntitySource }, replacement)

    fooEntity = initialBuilder.entities(SampleEntity::class.java).first { it.stringProperty == "foo" }
    val mapping = initialBuilder.getExternalMapping<String>(INDEX_ID) as ExternalEntityMappingImpl
    assertEquals(1, mapping.size())
    assertEquals("test", mapping.getDataByEntity(fooEntity))
  }

  @Test
  fun `replace by source empty mapping`() {
    val initialBuilder = WorkspaceEntityStorageBuilder.create()
    val fooEntity = initialBuilder.addSampleEntity("foo")
    val externalMapping = initialBuilder.getMutableExternalMapping<Int>(INDEX_ID)
    externalMapping.addMapping(fooEntity, 1)

    val replacement = WorkspaceEntityStorageBuilder.create()
    initialBuilder.replaceBySource({ it is SampleEntitySource }, replacement)

    val mapping = initialBuilder.getExternalMapping<String>(INDEX_ID) as ExternalEntityMappingImpl
    assertEquals(0, mapping.size())
  }

  @Test
  fun `replace by source update id in the mapping`() {
    val initialBuilder = WorkspaceEntityStorageBuilder.create()
    val fooEntity = initialBuilder.addSampleEntity("foo")
    initialBuilder.addSampleEntity("baz")
    val barEntity = initialBuilder.addSampleEntity("bar")

    val replacement = WorkspaceEntityStorageBuilder.create()
    val externalMapping = replacement.getMutableExternalMapping<Int>(INDEX_ID)
    val fooEntity1 = replacement.addSampleEntity("foo")
    val barEntity1 = replacement.addSampleEntity("bar")
    externalMapping.addMapping(fooEntity1, 1)
    externalMapping.addMapping(barEntity1, 2)
    initialBuilder.replaceBySource({ it is SampleEntitySource }, replacement)

    assertNotEquals(barEntity.id, barEntity1.id)
    val mapping = initialBuilder.getExternalMapping<String>(INDEX_ID) as ExternalEntityMappingImpl
    assertEquals(2, mapping.size())
    assertEquals(1, mapping.getDataByEntity(fooEntity))
    assertEquals(2, mapping.getDataByEntity(barEntity))
  }
}