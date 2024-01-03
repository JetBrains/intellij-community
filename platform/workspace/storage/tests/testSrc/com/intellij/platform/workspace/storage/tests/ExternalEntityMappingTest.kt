// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.workspace.storage.tests

import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.ExternalMappingKey
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.impl.external.ExternalEntityMappingImpl
import com.intellij.platform.workspace.storage.impl.external.MutableExternalEntityMappingImpl
import com.intellij.platform.workspace.storage.impl.url.VirtualFileUrlManagerImpl
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.testEntities.entities.SampleEntity
import com.intellij.platform.workspace.storage.testEntities.entities.SampleEntitySource
import com.intellij.platform.workspace.storage.testEntities.entities.SourceEntity
import com.intellij.platform.workspace.storage.testEntities.entities.modifyEntity
import com.intellij.platform.workspace.storage.toBuilder
import com.intellij.testFramework.UsefulTestCase.assertEmpty
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import kotlin.test.*

class ExternalEntityMappingTest {
  companion object {
    private val INDEX_ID = ExternalMappingKey.create<Any>("test.index.id")
    private val ANOTHER_INDEX_ID = ExternalMappingKey.create<Any>("test.another.index.id")
  }

  @Test
  fun `base mapping test`() {
    val builder = createEmptyBuilder()

    val mapping = builder.getMutableExternalMapping(INDEX_ID)
    val entity = builder addEntity SourceEntity("hello", SampleEntitySource("source"))
    mapping.addMapping(entity, 1)
    mapping.addMapping(entity, 2)
    assertEquals(2, mapping.getDataByEntity(entity))
    val countBeforeRemove = builder.modificationCount
    mapping.removeMapping(entity)
    assertTrue(builder.modificationCount > countBeforeRemove)
    assertNull(mapping.getDataByEntity(entity))
    assertEmpty(mapping.getEntities(1))
    assertEmpty(mapping.getEntities(2))
    val countBeforeAdd = builder.modificationCount
    mapping.addMapping(entity, 3)
    assertTrue(builder.modificationCount > countBeforeAdd)
    assertEquals(3, mapping.getDataByEntity(entity))

    val storage = builder.toSnapshot()
    val newMapping = storage.getExternalMapping(INDEX_ID)
    assertEquals(3, newMapping.getDataByEntity(entity))
    assertEquals(entity, newMapping.getEntities(3).single())
  }

  @Test
  fun `update in diff test`() {
    val builder = createEmptyBuilder()

    val mapping = builder.getMutableExternalMapping(INDEX_ID)
    val entity = builder addEntity SourceEntity("hello", SampleEntitySource("source"))
    mapping.addMapping(entity, 1)
    assertEquals(1, mapping.getDataByEntity(entity))

    val diff = createBuilderFrom(builder.toSnapshot())
    val diffMapping = diff.getMutableExternalMapping(INDEX_ID)
    Assertions.assertNotEquals(mapping, diffMapping)
    assertEquals(1, diffMapping.getDataByEntity(entity))
    diffMapping.addMapping(entity, 2)
    assertEquals(1, mapping.getDataByEntity(entity))
    assertEquals(2, diffMapping.getDataByEntity(entity))

    builder.addDiff(diff)
    assertEquals(2, mapping.getDataByEntity(entity))

    val storage = builder.toSnapshot()
    val newMapping = storage.getExternalMapping(INDEX_ID)
    Assertions.assertNotEquals(mapping, newMapping)
    assertEquals(2, newMapping.getDataByEntity(entity))
  }

  @Test
  fun `remove from diff test`() {
    val builder = createEmptyBuilder()

    val mapping = builder.getMutableExternalMapping(INDEX_ID)
    val entity = builder addEntity SourceEntity("hello", SampleEntitySource("source"))
    mapping.addMapping(entity, 1)
    assertEquals(1, mapping.getDataByEntity(entity))

    val diff = createBuilderFrom(builder.toSnapshot())
    val diffMapping = diff.getMutableExternalMapping(INDEX_ID)
    Assertions.assertNotEquals(mapping, diffMapping)
    assertEquals(1, diffMapping.getDataByEntity(entity))
    diffMapping.removeMapping(entity)
    assertEquals(1, mapping.getDataByEntity(entity))
    Assertions.assertNull(diffMapping.getDataByEntity(entity))

    builder.addDiff(diff)
    Assertions.assertNull(mapping.getDataByEntity(entity))

    val storage = builder.toSnapshot()
    val newMapping = storage.getExternalMapping(INDEX_ID)
    Assertions.assertNotNull(newMapping)
    Assertions.assertNotEquals(mapping, newMapping)
    Assertions.assertNull(newMapping.getDataByEntity(entity))
  }

  @Test
  fun `add to diff test`() {
    val builder = createEmptyBuilder()

    val mapping = builder.getMutableExternalMapping(INDEX_ID)
    val entity = builder addEntity SourceEntity("hello", SampleEntitySource("source"))
    mapping.addMapping(entity, 1)
    assertEquals(1, mapping.getDataByEntity(entity))

    val diff = createBuilderFrom(builder.toSnapshot())
    val newEntity = builder addEntity SourceEntity("world", SampleEntitySource("source"))
    val diffMapping = diff.getMutableExternalMapping(INDEX_ID)
    Assertions.assertNotEquals(mapping, diffMapping)
    assertEquals(1, diffMapping.getDataByEntity(entity))
    diffMapping.addMapping(newEntity, 2)
    assertEquals(1, mapping.getDataByEntity(entity))
    assertEquals(1, diffMapping.getDataByEntity(entity))
    assertEquals(2, diffMapping.getDataByEntity(newEntity))

    builder.addDiff(diff)
    assertEquals(1, mapping.getDataByEntity(entity))
    assertEquals(2, mapping.getDataByEntity(newEntity))

    val storage = builder.toSnapshot()
    val newMapping = storage.getExternalMapping(INDEX_ID)
    Assertions.assertNotEquals(mapping, newMapping)
    assertEquals(1, newMapping.getDataByEntity(entity))
    assertEquals(2, newMapping.getDataByEntity(newEntity))
    assertEquals(entity, newMapping.getEntities(1).single())
    assertEquals(newEntity, newMapping.getEntities(2).single())
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  @Test
  fun `remove mapping from diff test`() {
    val builder = createEmptyBuilder()

    val mapping = builder.getMutableExternalMapping(INDEX_ID)
    val entity = builder addEntity SourceEntity("hello", SampleEntitySource("source"))
    mapping.addMapping(entity, 1)
    assertEquals(1, mapping.getDataByEntity(entity))

    val diff = createBuilderFrom(builder.toSnapshot())

    val entities = diff.indexes.externalMappings[INDEX_ID]!!.index.build()
    entities.forEach { key, value ->
      val myEntity = diff.entityDataByIdOrDie(key).createEntity(diff)
      diff.getMutableExternalMapping(INDEX_ID).removeMapping(myEntity)
    }

    assertNull(diff.getExternalMapping(INDEX_ID).getDataByEntity(entity))
    assertEmpty(diff.getExternalMapping(INDEX_ID).getEntities(1))
    assertEquals(1, mapping.getDataByEntity(entity))
    assertEquals(1, mapping.getEntities(1).size)

    builder.addDiff(diff)
    assertNull(mapping.getDataByEntity(entity))
    assertEmpty(mapping.getEntities(1))
    assertNull(builder.getExternalMapping(INDEX_ID).getDataByEntity(entity))

    val storage = builder.toSnapshot()
    assertNull(storage.getExternalMapping(INDEX_ID).getDataByEntity(entity))
    assertEmpty(storage.getExternalMapping(INDEX_ID).getEntities(1))
  }

  @Test
  fun `add mapping to diff test`() {
    val builder = createEmptyBuilder()

    val entity = builder addEntity SourceEntity("hello", SampleEntitySource("source"))
    Assertions.assertNull(builder.getExternalMapping(INDEX_ID).getDataByEntity(entity))

    val diff = createBuilderFrom(builder.toSnapshot())
    val diffMapping = diff.getMutableExternalMapping(INDEX_ID)
    diffMapping.addMapping(entity, 1)
    Assertions.assertNull(builder.getExternalMapping(INDEX_ID).getDataByEntity(entity))
    assertEquals(1, diffMapping.getDataByEntity(entity))

    builder.addDiff(diff)
    assertEquals(1, diffMapping.getDataByEntity(entity))
    val mapping = builder.getExternalMapping(INDEX_ID)
    Assertions.assertNotEquals(diffMapping, mapping)
    assertEquals(1, mapping.getDataByEntity(entity))

    val storage = builder.toSnapshot()
    val newMapping = storage.getExternalMapping(INDEX_ID)
    Assertions.assertNotEquals(mapping, newMapping)
    assertEquals(1, newMapping.getDataByEntity(entity))
  }

  @Test
  fun `remove mapping if entity is removed`() {
    val initialBuilder = createEmptyBuilder()
    val entity1 = initialBuilder addEntity SampleEntity(false, "1", ArrayList(), HashMap(),
                                                        VirtualFileUrlManagerImpl().fromUrl("file:///tmp"), SampleEntitySource("test"))
    initialBuilder.getMutableExternalMapping(INDEX_ID).addMapping(entity1, 1)
    val entity2 = initialBuilder addEntity SampleEntity(false, "2", ArrayList(), HashMap(),
                                                        VirtualFileUrlManagerImpl().fromUrl("file:///tmp"), SampleEntitySource("test"))
    initialBuilder.getMutableExternalMapping(INDEX_ID).addMapping(entity2, 2)
    val storage = initialBuilder.toSnapshot()
    assertEquals(1, storage.getExternalMapping(INDEX_ID).getDataByEntity(entity1))
    assertEquals(2, storage.getExternalMapping(INDEX_ID).getDataByEntity(entity2))

    val builder = createBuilderFrom(storage)
    val entity3 = builder addEntity SampleEntity(false, "3", ArrayList(), HashMap(), VirtualFileUrlManagerImpl().fromUrl("file:///tmp"),
                                                 SampleEntitySource("test"))
    builder.getMutableExternalMapping(INDEX_ID).addMapping(entity3, 3)
    builder.removeEntity(entity1.from(builder))
    val diff = MutableEntityStorage.from(storage)
    diff.removeEntity(entity2.from(diff))
    builder.addDiff(diff)
    builder.removeEntity(entity3)

    fun checkStorage(storage: EntityStorage) {
      val mapping = storage.getExternalMapping(INDEX_ID)
      Assertions.assertNull(mapping.getDataByEntity(entity1))
      Assertions.assertNull(mapping.getDataByEntity(entity2))
      Assertions.assertNull(mapping.getDataByEntity(entity3))
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
    val entity1 = initialBuilder addEntity SampleEntity(false, "1", ArrayList(), HashMap(),
                                                        VirtualFileUrlManagerImpl().fromUrl("file:///tmp"), SampleEntitySource("test"))
    initialBuilder.getMutableExternalMapping(INDEX_ID).addMapping(entity1, 1)
    val entity2 = initialBuilder addEntity SampleEntity(false, "2", ArrayList(), HashMap(),
                                                        VirtualFileUrlManagerImpl().fromUrl("file:///tmp"), SampleEntitySource("test"))
    initialBuilder.getMutableExternalMapping(INDEX_ID).addMapping(entity2, 2)
    val storage = initialBuilder.toSnapshot()
    assertEquals(1, storage.getExternalMapping(INDEX_ID).getDataByEntity(entity1))
    assertEquals(2, storage.getExternalMapping(INDEX_ID).getDataByEntity(entity2))

    val builder = createBuilderFrom(storage)
    val entity3 = builder addEntity SampleEntity(false, "3", ArrayList(), HashMap(), VirtualFileUrlManagerImpl().fromUrl("file:///tmp"),
                                                 SampleEntitySource("test"))
    builder.getMutableExternalMapping(INDEX_ID).addMapping(entity3, 3)
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
      val mapping = storage.getExternalMapping(INDEX_ID)
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
    val entity1 = builder addEntity SampleEntity(false, "1", ArrayList(), HashMap(), VirtualFileUrlManagerImpl().fromUrl("file:///tmp"),
                                                 SampleEntitySource("test"))
    builder.getMutableExternalMapping(INDEX_ID).addMapping(entity1, 1)
    val entity2 = diff addEntity SampleEntity(false, "2", ArrayList(), HashMap(), VirtualFileUrlManagerImpl().fromUrl("file:///tmp"),
                                              SampleEntitySource("test"))
    diff.getMutableExternalMapping(INDEX_ID).addMapping(entity2, 2)
    builder.addDiff(diff)
    val builderMapping = builder.getExternalMapping(INDEX_ID)
    val entities = builder.entities(SampleEntity::class.java).sortedBy { it.stringProperty }.toList()
    assertEquals(1, builderMapping.getDataByEntity(entities[0]))
    assertEquals(2, builderMapping.getDataByEntity(entities[1]))

    val storage = builder.toSnapshot()
    val storageMapping = storage.getExternalMapping(INDEX_ID)
    val entitiesFromStorage = storage.entities(SampleEntity::class.java).sortedBy { it.stringProperty }.toList()
    assertEquals(1, builderMapping.getDataByEntity(entitiesFromStorage[0]))
    assertEquals(2, builderMapping.getDataByEntity(entitiesFromStorage[1]))
  }

  @Test
  fun `merge mapping added after builder was created`() {
    val initialBuilder = createEmptyBuilder()
    initialBuilder addEntity SampleEntity(false, "foo", ArrayList(), HashMap(), VirtualFileUrlManagerImpl().fromUrl("file:///tmp"),
                                          SampleEntitySource("test"))
    val initialStorage = initialBuilder.toSnapshot()
    val diff1 = MutableEntityStorage.from(initialStorage)
    diff1 addEntity SampleEntity(false, "bar", ArrayList(), HashMap(), VirtualFileUrlManagerImpl().fromUrl("file:///tmp"),
                                 SampleEntitySource("test"))

    val diff2 = MutableEntityStorage.from(initialStorage)
    diff2.getMutableExternalMapping(INDEX_ID).addMapping(initialStorage.singleSampleEntity(), 1)
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
    assertEquals(1, newStorage.getExternalMapping(INDEX_ID).getDataByEntity(foo))
  }

  @Test
  fun `replace by source add new mapping`() {
    val initialBuilder = createEmptyBuilder()
    initialBuilder addEntity SampleEntity(false, "foo", ArrayList(), HashMap(), VirtualFileUrlManagerImpl().fromUrl("file:///tmp"),
                                          SampleEntitySource("test"))

    val replacement = createBuilderFrom(initialBuilder)
    val entity = initialBuilder.singleSampleEntity()
    replacement.getMutableExternalMapping(INDEX_ID).addMapping(entity, 1)
    initialBuilder.replaceBySource({ it is SampleEntitySource }, replacement)
    assertEquals(1, initialBuilder.getExternalMapping(INDEX_ID).getDataByEntity(entity))
  }

  @Test
  fun `replace by source add new mapping with new entity`() {
    val initialBuilder = createEmptyBuilder()
    val fooEntity = initialBuilder addEntity SampleEntity(false, "foo", ArrayList(), HashMap(),
                                                          VirtualFileUrlManagerImpl().fromUrl("file:///tmp"), SampleEntitySource("test"))

    val replacement = createBuilderFrom(initialBuilder)
    val barEntity = replacement addEntity SampleEntity(false, "bar", ArrayList(), HashMap(),
                                                       VirtualFileUrlManagerImpl().fromUrl("file:///tmp"), SampleEntitySource("test"))
    var externalMapping = replacement.getMutableExternalMapping(INDEX_ID)
    externalMapping.addMapping(fooEntity, 1)
    externalMapping = replacement.getMutableExternalMapping(ANOTHER_INDEX_ID)
    externalMapping.addMapping(barEntity, 2)
    initialBuilder.replaceBySource({ it is SampleEntitySource }, replacement)

    assertEquals(1, initialBuilder.getExternalMapping(INDEX_ID).getDataByEntity(fooEntity))
    assertEquals(2, initialBuilder.getExternalMapping(ANOTHER_INDEX_ID).getDataByEntity(barEntity))
    Assertions.assertNull(initialBuilder.getExternalMapping(INDEX_ID).getDataByEntity(barEntity))
  }

  @Test
  fun `replace by source update mapping for old entity`() {
    val initialBuilder = createEmptyBuilder()
    val fooEntity = initialBuilder addEntity SampleEntity(false, "foo", ArrayList(), HashMap(),
                                                          VirtualFileUrlManagerImpl().fromUrl("file:///tmp"), SampleEntitySource("test"))
    var externalMapping = initialBuilder.getMutableExternalMapping(INDEX_ID)
    externalMapping.addMapping(fooEntity, 1)

    val replacement = createBuilderFrom(initialBuilder)
    externalMapping = replacement.getMutableExternalMapping(INDEX_ID)
    externalMapping.addMapping(fooEntity, 2)
    initialBuilder.replaceBySource({ it is SampleEntitySource }, replacement)

    assertEquals(2, initialBuilder.getExternalMapping(INDEX_ID).getDataByEntity(fooEntity))
  }

  @Test
  fun `replace by source update mapping for new entity`() {
    val initialBuilder = createEmptyBuilder()
    val fooEntity = initialBuilder addEntity SampleEntity(false, "foo", ArrayList(), HashMap(),
                                                          VirtualFileUrlManagerImpl().fromUrl("file:///tmp"), SampleEntitySource("test"))
    var externalMapping = initialBuilder.getMutableExternalMapping(INDEX_ID)
    externalMapping.addMapping(fooEntity, 1)

    val replacement = createBuilderFrom(initialBuilder)
    val barEntity = replacement addEntity SampleEntity(false, "bar", ArrayList(), HashMap(),
                                                       VirtualFileUrlManagerImpl().fromUrl("file:///tmp"), SampleEntitySource("test"))
    externalMapping = replacement.getMutableExternalMapping(INDEX_ID)
    externalMapping.addMapping(barEntity, 2)
    initialBuilder.replaceBySource({ it is SampleEntitySource }, replacement)

    assertEquals(1, initialBuilder.getExternalMapping(INDEX_ID).getDataByEntity(fooEntity))
    assertEquals(2, initialBuilder.getExternalMapping(INDEX_ID).getDataByEntity(barEntity))
  }

  @Test
  fun `replace by source remove from mapping`() {
    val initialBuilder = createEmptyBuilder()
    val fooEntity = initialBuilder addEntity SampleEntity(false, "foo", ArrayList(), HashMap(),
                                                          VirtualFileUrlManagerImpl().fromUrl("file:///tmp"), SampleEntitySource("test"))
    var externalMapping = initialBuilder.getMutableExternalMapping(INDEX_ID)
    externalMapping.addMapping(fooEntity, 1)

    val replacement = createBuilderFrom(initialBuilder)
    val barEntity = replacement addEntity SampleEntity(false, "bar", ArrayList(), HashMap(),
                                                       VirtualFileUrlManagerImpl().fromUrl("file:///tmp"), SampleEntitySource("test"))
    externalMapping = replacement.getMutableExternalMapping(INDEX_ID)
    externalMapping.addMapping(barEntity, 2)
    externalMapping.removeMapping(barEntity)
    externalMapping.removeMapping(fooEntity)
    initialBuilder.replaceBySource({ it is SampleEntitySource }, replacement)

    assertEquals(1, initialBuilder.getExternalMapping(INDEX_ID).getDataByEntity(fooEntity))
    assertNull(initialBuilder.getExternalMapping(INDEX_ID).getDataByEntity(barEntity))
  }

  @Test
  fun `replace by source cleanup mapping by entity remove`() {
    val initialBuilder = createEmptyBuilder()
    val fooEntity = initialBuilder addEntity SampleEntity(false, "foo", ArrayList(), HashMap(),
                                                          VirtualFileUrlManagerImpl().fromUrl("file:///tmp"), SampleEntitySource("test"))
    val externalMapping = initialBuilder.getMutableExternalMapping(INDEX_ID)
    externalMapping.addMapping(fooEntity, 1)

    val replacement = createBuilderFrom(initialBuilder)
    replacement.removeEntity(fooEntity.from(replacement))
    Assertions.assertNull(replacement.getMutableExternalMapping(INDEX_ID).getDataByEntity(fooEntity))
    initialBuilder.replaceBySource({ it is SampleEntitySource }, replacement)

    Assertions.assertNull(initialBuilder.getExternalMapping(INDEX_ID).getDataByEntity(fooEntity))
  }

  @Test
  fun `replace by source replace one mapping to another`() {
    val initialBuilder = createEmptyBuilder()
    val fooEntity = initialBuilder addEntity SampleEntity(false, "foo", ArrayList(), HashMap(),
                                                          VirtualFileUrlManagerImpl().fromUrl("file:///tmp"), SampleEntitySource("test"))
    var externalMapping = initialBuilder.getMutableExternalMapping(INDEX_ID)
    externalMapping.addMapping(fooEntity, 1)

    val replacement = createEmptyBuilder()
    var barEntity = replacement addEntity SampleEntity(false, "bar", ArrayList(), HashMap(),
                                                       VirtualFileUrlManagerImpl().fromUrl("file:///tmp"), SampleEntitySource("test"))
    externalMapping = replacement.getMutableExternalMapping(ANOTHER_INDEX_ID)
    externalMapping.addMapping(barEntity, 2)
    initialBuilder.replaceBySource({ it is SampleEntitySource }, replacement)

    barEntity = initialBuilder.entities(SampleEntity::class.java).first { it.stringProperty == "bar" }
    assertEquals(2, initialBuilder.getExternalMapping(ANOTHER_INDEX_ID).getDataByEntity(barEntity))
    val mapping = initialBuilder.getExternalMapping(INDEX_ID) as ExternalEntityMappingImpl
    assertEquals(0, mapping.size())
  }

  @Test
  fun `replace by source replace mappings`() {
    val initialBuilder = createEmptyBuilder()
    val fooEntity = initialBuilder addEntity SampleEntity(false, "foo", ArrayList(), HashMap(),
                                                          VirtualFileUrlManagerImpl().fromUrl("file:///tmp"), SampleEntitySource("test"))
    var externalMapping = initialBuilder.getMutableExternalMapping(INDEX_ID)
    externalMapping.addMapping(fooEntity, 1)

    val replacement = createEmptyBuilder()
    var barEntity = replacement addEntity SampleEntity(false, "bar", ArrayList(), HashMap(),
                                                       VirtualFileUrlManagerImpl().fromUrl("file:///tmp"), SampleEntitySource("test"))
    externalMapping = replacement.getMutableExternalMapping(INDEX_ID)
    externalMapping.addMapping(barEntity, 2)
    initialBuilder.replaceBySource({ it is SampleEntitySource }, replacement)

    barEntity = initialBuilder.entities(SampleEntity::class.java).first { it.stringProperty == "bar" }

    val mapping = initialBuilder.getExternalMapping(INDEX_ID) as ExternalEntityMappingImpl
    assertEquals(1, mapping.size())
    assertEquals(2, mapping.getDataByEntity(barEntity))
  }

  @Test
  fun `replace by source update mapping content and type`() {
    val initialBuilder = createEmptyBuilder()
    var fooEntity = initialBuilder addEntity SampleEntity(false, "foo", ArrayList(), HashMap(),
                                                          VirtualFileUrlManagerImpl().fromUrl("file:///tmp"), SampleEntitySource("test"))
    val externalMapping = initialBuilder.getMutableExternalMapping(INDEX_ID)
    externalMapping.addMapping(fooEntity, 1)

    val replacement = createEmptyBuilder()
    val secondFooEntity = replacement addEntity SampleEntity(false, "foo", ArrayList(), HashMap(),
                                                             VirtualFileUrlManagerImpl().fromUrl("file:///tmp"),
                                                             SampleEntitySource("test"))
    val newExternalMapping = replacement.getMutableExternalMapping(INDEX_ID)
    newExternalMapping.addMapping(secondFooEntity, "test")
    initialBuilder.replaceBySource({ it is SampleEntitySource }, replacement)

    fooEntity = initialBuilder.entities(SampleEntity::class.java).first { it.stringProperty == "foo" }
    val mapping = initialBuilder.getExternalMapping(INDEX_ID) as ExternalEntityMappingImpl
    assertEquals(1, mapping.size())
    assertEquals("test", mapping.getDataByEntity(fooEntity))
  }

  @Test
  fun `replace by source empty mapping`() {
    val initialBuilder = createEmptyBuilder()
    val fooEntity = initialBuilder addEntity SampleEntity(false, "foo", ArrayList(), HashMap(),
                                                          VirtualFileUrlManagerImpl().fromUrl("file:///tmp"), SampleEntitySource("test"))
    val externalMapping = initialBuilder.getMutableExternalMapping(INDEX_ID)
    externalMapping.addMapping(fooEntity, 1)

    val replacement = createEmptyBuilder()
    initialBuilder.replaceBySource({ it is SampleEntitySource }, replacement)

    val mapping = initialBuilder.getExternalMapping(INDEX_ID) as ExternalEntityMappingImpl
    assertEquals(0, mapping.size())
  }

  @Test
  fun `replace by source update id in the mapping`() {
    val initialBuilder = createEmptyBuilder()
    val fooEntity = initialBuilder addEntity SampleEntity(false, "foo", ArrayList(), HashMap(),
                                                          VirtualFileUrlManagerImpl().fromUrl("file:///tmp"), SampleEntitySource("test"))
    initialBuilder addEntity SampleEntity(false, "baz", ArrayList(), HashMap(), VirtualFileUrlManagerImpl().fromUrl("file:///tmp"),
                                          SampleEntitySource("test"))
    val barEntity = initialBuilder addEntity SampleEntity(false, "bar", ArrayList(), HashMap(),
                                                          VirtualFileUrlManagerImpl().fromUrl("file:///tmp"), SampleEntitySource("test"))

    val replacement = createEmptyBuilder()
    val externalMapping = replacement.getMutableExternalMapping(INDEX_ID)
    val fooEntity1 = replacement addEntity SampleEntity(false, "foo", ArrayList(), HashMap(),
                                                        VirtualFileUrlManagerImpl().fromUrl("file:///tmp"), SampleEntitySource("test"))
    val barEntity1 = replacement addEntity SampleEntity(false, "bar", ArrayList(), HashMap(),
                                                        VirtualFileUrlManagerImpl().fromUrl("file:///tmp"), SampleEntitySource("test"))
    externalMapping.addMapping(fooEntity1, 1)
    externalMapping.addMapping(barEntity1, 2)
    initialBuilder.replaceBySource({ it is SampleEntitySource }, replacement)

    val mapping = initialBuilder.getExternalMapping(INDEX_ID) as ExternalEntityMappingImpl
    assertEquals(2, mapping.size())
    Assertions.assertEquals(1, mapping.getDataByEntity(fooEntity))
    Assertions.assertEquals(2, mapping.getDataByEntity(barEntity))
  }

  @Test
  fun `ignore added mapping for removed entity`() {
    val commonBuilder = createEmptyBuilder()

    val diff1 = createEmptyBuilder()
    val foo1 = diff1 addEntity SampleEntity(false, "foo1", ArrayList(), HashMap(), VirtualFileUrlManagerImpl().fromUrl("file:///tmp"),
                                            SampleEntitySource("test"))
    diff1.getMutableExternalMapping(INDEX_ID).addMapping(foo1, 1)
    commonBuilder.addDiff(diff1)

    val diff2 = createEmptyBuilder()
    val foo2 = diff2 addEntity SampleEntity(false, "foo2", ArrayList(), HashMap(), VirtualFileUrlManagerImpl().fromUrl("file:///tmp"),
                                            SampleEntitySource("test"))
    diff2.getMutableExternalMapping(INDEX_ID).addMapping(foo2, 2)
    diff2.removeEntity(foo2)
    commonBuilder.addDiff(diff2)

    val storage = commonBuilder.toSnapshot()
    val entity = storage.singleSampleEntity()
    assertEquals("foo1", entity.stringProperty)
    assertEquals(1, storage.getExternalMapping(INDEX_ID).getDataByEntity(entity))
  }

  @Test
  fun `remove mapping for removed entity after merge`() {
    val initialBuilder = createEmptyBuilder()
    val foo = initialBuilder addEntity SampleEntity(false, "foo", ArrayList(), HashMap(),
                                                    VirtualFileUrlManagerImpl().fromUrl("file:///tmp"), SampleEntitySource("test"))
    initialBuilder.getMutableExternalMapping(INDEX_ID).addMapping(foo, 1)
    val initialStorage = initialBuilder.toSnapshot()

    val diff1 = createBuilderFrom(initialStorage)
    val diff2 = createBuilderFrom(initialStorage)
    diff1.removeEntity(foo.from(diff1))
    diff2.getMutableExternalMapping(INDEX_ID).addMapping(foo, 2)
    val updatedStorage = diff1.toSnapshot()
    val mergeBuilder = createBuilderFrom(updatedStorage)
    mergeBuilder.addDiff(diff2)
    val merged = mergeBuilder.toSnapshot()
    assertEmpty(merged.entities(SampleEntity::class.java).toList())
  }

  @Test
  fun `check double mapping adding`() {
    val initialBuilder = createEmptyBuilder()
    val foo = initialBuilder addEntity SampleEntity(false, "foo", ArrayList(), HashMap(),
                                                    VirtualFileUrlManagerImpl().fromUrl("file:///tmp"), SampleEntitySource("test"))
    initialBuilder.getMutableExternalMapping(INDEX_ID).addMapping(foo, 1)
    initialBuilder.getMutableExternalMapping(INDEX_ID).addMapping(foo, 2)
    assertEquals(2, ((initialBuilder.getMutableExternalMapping(INDEX_ID) as MutableExternalEntityMappingImpl)
      .indexLogBunches
      .changes
      .values.single().first as MutableExternalEntityMappingImpl.IndexLogRecord.Add<*>).data)
  }

  @Test
  fun `mapping replacement generates remove plus add events`() {
    val initialBuilder = createEmptyBuilder()
    val foo = initialBuilder addEntity SampleEntity(false, "foo", ArrayList(), HashMap(),
                                                    VirtualFileUrlManagerImpl().fromUrl("file:///tmp"), SampleEntitySource("test"))
    initialBuilder.getMutableExternalMapping(INDEX_ID).addMapping(foo, 1)

    val newBuilder = initialBuilder.toSnapshot().toBuilder()

    newBuilder.getMutableExternalMapping(INDEX_ID).addMapping(foo, 2)
    val changelogEntries = (newBuilder.getMutableExternalMapping(INDEX_ID) as MutableExternalEntityMappingImpl)
      .indexLogBunches
      .changes
      .values
    assertEquals(2, changelogEntries.single().toList().filterNotNull().size)
    assertIs<MutableExternalEntityMappingImpl.IndexLogRecord.Remove>(changelogEntries.single().first)
    assertEquals(2, (changelogEntries.single().second as MutableExternalEntityMappingImpl.IndexLogRecord.Add<*>).data)
  }

  @Test
  fun `index log don't have removed entity that was not commited`() {
    val initialBuilder = createEmptyBuilder()
    val foo = initialBuilder addEntity SampleEntity(false, "foo", ArrayList(), HashMap(),
                                                    VirtualFileUrlManagerImpl().fromUrl("file:///tmp"), SampleEntitySource("test"))
    initialBuilder.getMutableExternalMapping(INDEX_ID).addMapping(foo, 1)

    val newBuilder = initialBuilder.toSnapshot().toBuilder()
    val addedEntity = newBuilder addEntity SampleEntity(false, "foo", ArrayList(), HashMap(),
                                                    VirtualFileUrlManagerImpl().fromUrl("file:///tmp"), SampleEntitySource("test"))
    newBuilder.removeEntity(addedEntity)

    assertEquals(0, (newBuilder.getMutableExternalMapping(INDEX_ID) as MutableExternalEntityMappingImpl)
      .indexLogBunches
      .changes.size
    )
  }

  @Test
  fun `two instances of keys are not the same and not equal`() {
    val first = ExternalMappingKey.create<String>("this.is.my.key")
    val second = ExternalMappingKey.create<String>("this.is.my.key")
    assertNotSame(first, second)
    assertNotEquals(first, second)
  }
}
