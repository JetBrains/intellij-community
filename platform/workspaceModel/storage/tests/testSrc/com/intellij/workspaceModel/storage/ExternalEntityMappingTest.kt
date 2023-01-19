// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage

import com.intellij.testFramework.UsefulTestCase.assertEmpty
import com.intellij.workspaceModel.storage.entities.test.addSampleEntity
import com.intellij.workspaceModel.storage.entities.test.addSourceEntity
import com.intellij.workspaceModel.storage.entities.test.api.SampleEntity
import com.intellij.workspaceModel.storage.entities.test.api.SampleEntitySource
import com.intellij.workspaceModel.storage.entities.test.api.modifyEntity
import com.intellij.workspaceModel.storage.impl.external.ExternalEntityMappingImpl
import com.intellij.workspaceModel.storage.impl.external.MutableExternalEntityMappingImpl
import org.junit.Assert.*
import org.junit.Test

class ExternalEntityMappingTest {
  companion object {
    private const val INDEX_ID = "test.index.id"
    private const val ANOTHER_INDEX_ID = "test.another.index.id"
  }

  @Test
  fun `base mapping test`() {
    val builder = createEmptyBuilder()

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

    val storage = builder.toSnapshot()
    val newMapping = storage.getExternalMapping<Int>(INDEX_ID)
    assertEquals(3, newMapping.getDataByEntity(entity))
    assertEquals(entity, newMapping.getEntities(3).single())
  }

  @Test
  fun `update in diff test`() {
    val builder = createEmptyBuilder()

    val mapping = builder.getMutableExternalMapping<Int>(INDEX_ID)
    val entity = builder.addSourceEntity("hello", SampleEntitySource("source"))
    mapping.addMapping(entity, 1)
    assertEquals(1, mapping.getDataByEntity(entity))

    val diff = createBuilderFrom(builder.toSnapshot())
    val diffMapping = diff.getMutableExternalMapping<Int>(INDEX_ID)
    assertNotEquals(mapping, diffMapping)
    assertEquals(1, diffMapping.getDataByEntity(entity))
    diffMapping.addMapping(entity, 2)
    assertEquals(1, mapping.getDataByEntity(entity))
    assertEquals(2, diffMapping.getDataByEntity(entity))

    builder.addDiff(diff)
    assertEquals(2, mapping.getDataByEntity(entity))

    val storage = builder.toSnapshot()
    val newMapping = storage.getExternalMapping<Int>(INDEX_ID)
    assertNotEquals(mapping, newMapping)
    assertEquals(2, newMapping.getDataByEntity(entity))
  }

  @Test
  fun `remove from diff test`() {
    val builder = createEmptyBuilder()

    val mapping = builder.getMutableExternalMapping<Int>(INDEX_ID)
    val entity = builder.addSourceEntity("hello", SampleEntitySource("source"))
    mapping.addMapping(entity, 1)
    assertEquals(1, mapping.getDataByEntity(entity))

    val diff = createBuilderFrom(builder.toSnapshot())
    val diffMapping = diff.getMutableExternalMapping<Int>(INDEX_ID)
    assertNotEquals(mapping, diffMapping)
    assertEquals(1, diffMapping.getDataByEntity(entity))
    diffMapping.removeMapping(entity)
    assertEquals(1, mapping.getDataByEntity(entity))
    assertNull(diffMapping.getDataByEntity(entity))

    builder.addDiff(diff)
    assertNull(mapping.getDataByEntity(entity))

    val storage = builder.toSnapshot()
    val newMapping = storage.getExternalMapping<Int>(INDEX_ID)
    assertNotNull(newMapping)
    assertNotEquals(mapping, newMapping)
    assertNull(newMapping.getDataByEntity(entity))
  }

  @Test
  fun `add to diff test`() {
    val builder = createEmptyBuilder()

    val mapping = builder.getMutableExternalMapping<Int>(INDEX_ID)
    val entity = builder.addSourceEntity("hello", SampleEntitySource("source"))
    mapping.addMapping(entity, 1)
    assertEquals(1, mapping.getDataByEntity(entity))

    val diff = createBuilderFrom(builder.toSnapshot())
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

    val storage = builder.toSnapshot()
    val newMapping = storage.getExternalMapping<Int>(INDEX_ID)
    assertNotEquals(mapping, newMapping)
    assertEquals(1, newMapping.getDataByEntity(entity))
    assertEquals(2, newMapping.getDataByEntity(newEntity))
    assertEquals(entity, newMapping.getEntities(1).single())
    assertEquals(newEntity, newMapping.getEntities(2).single())
  }

  @Test
  fun `remove mapping from diff test`() {
    val builder = createEmptyBuilder()

    val mapping = builder.getMutableExternalMapping<Int>(INDEX_ID)
    val entity = builder.addSourceEntity("hello", SampleEntitySource("source"))
    mapping.addMapping(entity, 1)
    assertEquals(1, mapping.getDataByEntity(entity))

    val diff = createBuilderFrom(builder.toSnapshot())
    diff.indexes.removeExternalMapping(INDEX_ID)
    assertNull(diff.getExternalMapping<Int>(INDEX_ID).getDataByEntity(entity))
    assertEquals(1, mapping.getDataByEntity(entity))

    builder.addDiff(diff)
    assertNull(mapping.getDataByEntity(entity))
    assertNull(builder.getExternalMapping<Int>(INDEX_ID).getDataByEntity(entity))

    val storage = builder.toSnapshot()
    assertNull(storage.getExternalMapping<Int>(INDEX_ID).getDataByEntity(entity))
  }

  @Test
  fun `add mapping to diff test`() {
    val builder = createEmptyBuilder()

    val entity = builder.addSourceEntity("hello", SampleEntitySource("source"))
    assertNull(builder.getExternalMapping<Int>(INDEX_ID).getDataByEntity(entity))

    val diff = createBuilderFrom(builder.toSnapshot())
    val diffMapping = diff.getMutableExternalMapping<Int>(INDEX_ID)
    diffMapping.addMapping(entity, 1)
    assertNull(builder.getExternalMapping<Int>(INDEX_ID).getDataByEntity(entity))
    assertEquals(1, diffMapping.getDataByEntity(entity))

    builder.addDiff(diff)
    assertEquals(1, diffMapping.getDataByEntity(entity))
    val mapping = builder.getExternalMapping<Int>(INDEX_ID)
    assertNotEquals(diffMapping, mapping)
    assertEquals(1, mapping.getDataByEntity(entity))

    val storage = builder.toSnapshot()
    val newMapping = storage.getExternalMapping<Int>(INDEX_ID)
    assertNotEquals(mapping, newMapping)
    assertEquals(1, newMapping.getDataByEntity(entity))
  }

  @Test
  fun `remove mapping if entity is removed`() {
    val initialBuilder = createEmptyBuilder()
    val entity1 = initialBuilder.addSampleEntity("1")
    initialBuilder.getMutableExternalMapping<Int>(INDEX_ID).addMapping(entity1, 1)
    val entity2 = initialBuilder.addSampleEntity("2")
    initialBuilder.getMutableExternalMapping<Int>(INDEX_ID).addMapping(entity2, 2)
    val storage = initialBuilder.toSnapshot()
    assertEquals(1, storage.getExternalMapping<Int>(INDEX_ID).getDataByEntity(entity1))
    assertEquals(2, storage.getExternalMapping<Int>(INDEX_ID).getDataByEntity(entity2))

    val builder = createBuilderFrom(storage)
    val entity3 = builder.addSampleEntity("3")
    builder.getMutableExternalMapping<Int>(INDEX_ID).addMapping(entity3, 3)
    builder.removeEntity(entity1.from(builder))
    val diff = MutableEntityStorage.from(storage)
    diff.removeEntity(entity2.from(diff))
    builder.addDiff(diff)
    builder.removeEntity(entity3)

    fun checkStorage(storage: EntityStorage) {
      val mapping = storage.getExternalMapping<Int>(INDEX_ID)
      assertNull(mapping.getDataByEntity(entity1))
      assertNull(mapping.getDataByEntity(entity2))
      assertNull(mapping.getDataByEntity(entity3))
      assertEmpty(mapping.getEntities(1))
      assertEmpty(mapping.getEntities(2))
      assertEmpty(mapping.getEntities(3))
    }
    checkStorage(builder)
    checkStorage(builder.toSnapshot())
  }

  @Test
  fun `keep mapping if entity is modified`() {
    val initialBuilder = createEmptyBuilder()
    val entity1 = initialBuilder.addSampleEntity("1")
    initialBuilder.getMutableExternalMapping<Int>(INDEX_ID).addMapping(entity1, 1)
    val entity2 = initialBuilder.addSampleEntity("2")
    initialBuilder.getMutableExternalMapping<Int>(INDEX_ID).addMapping(entity2, 2)
    val storage = initialBuilder.toSnapshot()
    assertEquals(1, storage.getExternalMapping<Int>(INDEX_ID).getDataByEntity(entity1))
    assertEquals(2, storage.getExternalMapping<Int>(INDEX_ID).getDataByEntity(entity2))

    val builder = createBuilderFrom(storage)
    val entity3 = builder.addSampleEntity("3")
    builder.getMutableExternalMapping<Int>(INDEX_ID).addMapping(entity3, 3)
    val entity1a = builder.modifyEntity(entity1.from(builder)) {
      stringProperty = "1a"
    }
    val diff = MutableEntityStorage.from(storage)
    val entity2a = diff.modifyEntity(entity2.from(diff)) {
      stringProperty = "2a"
    }
    builder.addDiff(diff)
    val entity3a = builder.modifyEntity(entity3) {
      stringProperty = "3a"
    }

    fun checkStorage(storage: EntityStorage) {
      val mapping = storage.getExternalMapping<Int>(INDEX_ID)
      assertEquals(1, mapping.getDataByEntity(entity1a))
      assertEquals(2, mapping.getDataByEntity(entity2a))
      assertEquals(3, mapping.getDataByEntity(entity3a))
      assertEquals("1a", (mapping.getEntities(1).single() as SampleEntity).stringProperty)
      assertEquals("2a", (mapping.getEntities(2).single() as SampleEntity).stringProperty)
      assertEquals("3a", (mapping.getEntities(3).single() as SampleEntity).stringProperty)
    }

    checkStorage(builder)
    checkStorage(builder.toSnapshot())
  }

  @Test
  fun `update mapping when id changes on adding via diff`() {
    val builder = createEmptyBuilder()
    val diff = MutableEntityStorage.from(builder.toSnapshot())
    val entity1 = builder.addSampleEntity("1")
    builder.getMutableExternalMapping<Int>(INDEX_ID).addMapping(entity1, 1)
    val entity2 = diff.addSampleEntity("2")
    diff.getMutableExternalMapping<Int>(INDEX_ID).addMapping(entity2, 2)
    builder.addDiff(diff)
    val builderMapping = builder.getExternalMapping<Int>(INDEX_ID)
    val entities = builder.entities(SampleEntity::class.java).sortedBy { it.stringProperty }.toList()
    assertEquals(1, builderMapping.getDataByEntity(entities[0]))
    assertEquals(2, builderMapping.getDataByEntity(entities[1]))

    val storage = builder.toSnapshot()
    val storageMapping = storage.getExternalMapping<Int>(INDEX_ID)
    val entitiesFromStorage = storage.entities(SampleEntity::class.java).sortedBy { it.stringProperty }.toList()
    assertEquals(1, builderMapping.getDataByEntity(entitiesFromStorage[0]))
    assertEquals(2, builderMapping.getDataByEntity(entitiesFromStorage[1]))
  }

  @Test
  fun `merge mapping added after builder was created`() {
    val initialBuilder = createEmptyBuilder()
    initialBuilder.addSampleEntity("foo")
    val initialStorage = initialBuilder.toSnapshot()
    val diff1 = MutableEntityStorage.from(initialStorage)
    diff1.addSampleEntity("bar")

    val diff2 = MutableEntityStorage.from(initialStorage)
    diff2.getMutableExternalMapping<Int>(INDEX_ID).addMapping(initialStorage.singleSampleEntity(), 1)
    val updatedBuilder = createBuilderFrom(initialStorage)
    updatedBuilder.addDiff(diff2)
    val updatedStorage = updatedBuilder.toSnapshot()

    val newBuilder = createBuilderFrom(updatedStorage)
    newBuilder.addDiff(diff1)
    val newStorage = newBuilder.toSnapshot()
    val entities = newStorage.entities(SampleEntity::class.java).sortedByDescending { it.stringProperty }.toList()
    assertEquals(2, entities.size)
    val (foo, bar) = entities
    assertEquals("foo", foo.stringProperty)
    assertEquals("bar", bar.stringProperty)
    assertEquals(1, newStorage.getExternalMapping<Int>(INDEX_ID).getDataByEntity(foo))
  }

  @Test
  fun `replace by source add new mapping`() {
    val initialBuilder = createEmptyBuilder()
    initialBuilder.addSampleEntity("foo")

    val replacement = createBuilderFrom(initialBuilder)
    val entity = initialBuilder.singleSampleEntity()
    replacement.getMutableExternalMapping<Int>(INDEX_ID).addMapping(entity, 1)
    initialBuilder.replaceBySource({ it is SampleEntitySource }, replacement)
    assertEquals(1, initialBuilder.getExternalMapping<Int>(INDEX_ID).getDataByEntity(entity))
  }

  @Test
  fun `replace by source add new mapping with new entity`() {
    val initialBuilder = createEmptyBuilder()
    val fooEntity = initialBuilder.addSampleEntity("foo")

    val replacement = createBuilderFrom(initialBuilder)
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
    val initialBuilder = createEmptyBuilder()
    val fooEntity = initialBuilder.addSampleEntity("foo")
    var externalMapping = initialBuilder.getMutableExternalMapping<Int>(INDEX_ID)
    externalMapping.addMapping(fooEntity, 1)

    val replacement = createBuilderFrom(initialBuilder)
    externalMapping = replacement.getMutableExternalMapping(INDEX_ID)
    externalMapping.addMapping(fooEntity, 2)
    initialBuilder.replaceBySource({ it is SampleEntitySource }, replacement)

    assertEquals(2, initialBuilder.getExternalMapping<Int>(INDEX_ID).getDataByEntity(fooEntity))
  }

  @Test
  fun `replace by source update mapping for new entity`() {
    val initialBuilder = createEmptyBuilder()
    val fooEntity = initialBuilder.addSampleEntity("foo")
    var externalMapping = initialBuilder.getMutableExternalMapping<Int>(INDEX_ID)
    externalMapping.addMapping(fooEntity, 1)

    val replacement = createBuilderFrom(initialBuilder)
    val barEntity = replacement.addSampleEntity("bar")
    externalMapping = replacement.getMutableExternalMapping(INDEX_ID)
    externalMapping.addMapping(barEntity, 2)
    initialBuilder.replaceBySource({ it is SampleEntitySource }, replacement)

    assertEquals(1, initialBuilder.getExternalMapping<Int>(INDEX_ID).getDataByEntity(fooEntity))
    assertEquals(2, initialBuilder.getExternalMapping<Int>(INDEX_ID).getDataByEntity(barEntity))
  }

  @Test
  fun `replace by source remove from mapping`() {
    val initialBuilder = createEmptyBuilder()
    val fooEntity = initialBuilder.addSampleEntity("foo")
    var externalMapping = initialBuilder.getMutableExternalMapping<Int>(INDEX_ID)
    externalMapping.addMapping(fooEntity, 1)

    val replacement = createBuilderFrom(initialBuilder)
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
    val initialBuilder = createEmptyBuilder()
    val fooEntity = initialBuilder.addSampleEntity("foo")
    val externalMapping = initialBuilder.getMutableExternalMapping<Int>(INDEX_ID)
    externalMapping.addMapping(fooEntity, 1)

    val replacement = createBuilderFrom(initialBuilder)
    replacement.removeEntity(fooEntity.from(replacement))
    assertNull(replacement.getMutableExternalMapping<Int>(INDEX_ID).getDataByEntity(fooEntity))
    initialBuilder.replaceBySource({ it is SampleEntitySource }, replacement)

    assertNull(initialBuilder.getExternalMapping<Int>(INDEX_ID).getDataByEntity(fooEntity))
  }

  @Test
  fun `replace by source replace one mapping to another`() {
    val initialBuilder = createEmptyBuilder()
    val fooEntity = initialBuilder.addSampleEntity("foo")
    var externalMapping = initialBuilder.getMutableExternalMapping<Int>(INDEX_ID)
    externalMapping.addMapping(fooEntity, 1)

    val replacement = createEmptyBuilder()
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
    val initialBuilder = createEmptyBuilder()
    val fooEntity = initialBuilder.addSampleEntity("foo")
    var externalMapping = initialBuilder.getMutableExternalMapping<Int>(INDEX_ID)
    externalMapping.addMapping(fooEntity, 1)

    val replacement = createEmptyBuilder()
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
    val initialBuilder = createEmptyBuilder()
    var fooEntity = initialBuilder.addSampleEntity("foo")
    val externalMapping = initialBuilder.getMutableExternalMapping<Int>(INDEX_ID)
    externalMapping.addMapping(fooEntity, 1)

    val replacement = createEmptyBuilder()
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
    val initialBuilder = createEmptyBuilder()
    val fooEntity = initialBuilder.addSampleEntity("foo")
    val externalMapping = initialBuilder.getMutableExternalMapping<Int>(INDEX_ID)
    externalMapping.addMapping(fooEntity, 1)

    val replacement = createEmptyBuilder()
    initialBuilder.replaceBySource({ it is SampleEntitySource }, replacement)

    val mapping = initialBuilder.getExternalMapping<String>(INDEX_ID) as ExternalEntityMappingImpl
    assertEquals(0, mapping.size())
  }

  @Test
  fun `replace by source update id in the mapping`() {
    val initialBuilder = createEmptyBuilder()
    val fooEntity = initialBuilder.addSampleEntity("foo")
    initialBuilder.addSampleEntity("baz")
    val barEntity = initialBuilder.addSampleEntity("bar")

    val replacement = createEmptyBuilder()
    val externalMapping = replacement.getMutableExternalMapping<Int>(INDEX_ID)
    val fooEntity1 = replacement.addSampleEntity("foo")
    val barEntity1 = replacement.addSampleEntity("bar")
    externalMapping.addMapping(fooEntity1, 1)
    externalMapping.addMapping(barEntity1, 2)
    initialBuilder.replaceBySource({ it is SampleEntitySource }, replacement)

    val mapping = initialBuilder.getExternalMapping<String>(INDEX_ID) as ExternalEntityMappingImpl
    assertEquals(2, mapping.size())
    //suppressed untlil the https://youtrack.jetbrains.com/issue/KTIJ-21754 is being fixed
    @Suppress("AssertBetweenInconvertibleTypes")
    assertEquals(1, mapping.getDataByEntity(fooEntity))
    //suppressed untlil the https://youtrack.jetbrains.com/issue/KTIJ-21754 is being fixed
    @Suppress("AssertBetweenInconvertibleTypes")
    assertEquals(2, mapping.getDataByEntity(barEntity))
  }

  @Test
  fun `ignore added mapping for removed entity`() {
    val commonBuilder = createEmptyBuilder()

    val diff1 = createEmptyBuilder()
    val foo1 = diff1.addSampleEntity("foo1")
    diff1.getMutableExternalMapping<Int>(INDEX_ID).addMapping(foo1, 1)
    commonBuilder.addDiff(diff1)

    val diff2 = createEmptyBuilder()
    val foo2 = diff2.addSampleEntity("foo2")
    diff2.getMutableExternalMapping<Int>(INDEX_ID).addMapping(foo2, 2)
    diff2.removeEntity(foo2)
    commonBuilder.addDiff(diff2)

    val storage = commonBuilder.toSnapshot()
    val entity = storage.singleSampleEntity()
    assertEquals("foo1", entity.stringProperty)
    assertEquals(1, storage.getExternalMapping<Int>(INDEX_ID).getDataByEntity(entity))
  }

  @Test
  fun `remove mapping for removed entity after merge`() {
    val initialBuilder = createEmptyBuilder()
    val foo = initialBuilder.addSampleEntity("foo")
    initialBuilder.getMutableExternalMapping<Int>(INDEX_ID).addMapping(foo, 1)
    val initialStorage = initialBuilder.toSnapshot()

    val diff1 = createBuilderFrom(initialStorage)
    val diff2 = createBuilderFrom(initialStorage)
    diff1.removeEntity(foo.from(diff1))
    diff2.getMutableExternalMapping<Int>(INDEX_ID).addMapping(foo, 2)
    val updatedStorage = diff1.toSnapshot()
    val mergeBuilder = createBuilderFrom(updatedStorage)
    mergeBuilder.addDiff(diff2)
    val merged = mergeBuilder.toSnapshot()
    assertEmpty(merged.entities(SampleEntity::class.java).toList())
  }

  @Test
  fun `check double mapping adding`() {
    val initialBuilder = createEmptyBuilder()
    val foo = initialBuilder.addSampleEntity("foo")
    initialBuilder.getMutableExternalMapping<Int>(INDEX_ID).addMapping(foo, 1)
    initialBuilder.getMutableExternalMapping<Int>(INDEX_ID).addMapping(foo, 2)
    assertEquals(2, (((initialBuilder.getMutableExternalMapping<Int>(INDEX_ID) as MutableExternalEntityMappingImpl)
      .indexLogBunches
      .chain
      .single() as MutableExternalEntityMappingImpl.IndexLogOperation.Changes)
      .changes.values.single() as MutableExternalEntityMappingImpl.IndexLogRecord.Add<*>).data)
  }
}
