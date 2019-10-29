package com.intellij.workspace.api

import gnu.trove.THashSet
import gnu.trove.TObjectHashingStrategy
import org.jetbrains.annotations.TestOnly
import org.junit.Assert.*
import org.junit.Test

internal interface SampleEntity : TypedEntity {
  val booleanProperty: Boolean
  val stringProperty: String
  val stringListProperty: List<String>
  val fileProperty: VirtualFileUrl
}

internal interface ModifiableSampleEntity : SampleEntity, ModifiableTypedEntity<SampleEntity> {
  override var booleanProperty: Boolean
  override var stringProperty: String
  override var stringListProperty: MutableList<String>
  override var fileProperty: VirtualFileUrl
}

internal interface SelfModifiableSampleEntity : ModifiableTypedEntity<SelfModifiableSampleEntity> {
  var intProperty: Int
}

internal fun TypedEntityStorageBuilder.addSampleEntity(stringProperty: String,
                                                       source: EntitySource = SampleEntitySource("test"),
                                                       booleanProperty: Boolean = false,
                                                       stringListProperty: MutableList<String> = ArrayList(),
                                                       fileProperty: VirtualFileUrl = VirtualFileUrlManager.fromUrl("file:///tmp")): SampleEntity {
  return addEntity<ModifiableSampleEntity, SampleEntity>(source) {
    this.booleanProperty = booleanProperty
    this.stringProperty = stringProperty
    this.stringListProperty = stringListProperty
    this.fileProperty = fileProperty
  }
}

internal fun TypedEntityStorage.singleSampleEntity() = entities(SampleEntity::class).single()

internal data class SampleEntitySource(val name: String) : EntitySource

class SimplePropertiesInProxyBasedStorageTest {
  @Test
  fun `add entity`() {
    val builder = TypedEntityStorageBuilder.create()
    val source = SampleEntitySource("test")
    val entity = builder.addSampleEntity("hello", source, true, mutableListOf("one", "two"))
    builder.checkConsistency()
    assertTrue(entity.booleanProperty)
    assertEquals("hello", entity.stringProperty)
    assertEquals(listOf("one", "two"), entity.stringListProperty)
    assertEquals(entity, builder.singleSampleEntity())
    assertEquals(source, entity.entitySource)
  }

  @Test
  fun `remove entity`() {
    val builder = TypedEntityStorageBuilder.create()
    val entity = builder.addSampleEntity("hello")
    builder.removeEntity(entity)
    builder.checkConsistency()
    assertTrue(builder.entities(SampleEntity::class).toList().isEmpty())
  }

  @Test
  fun `modify entity`() {
    val builder = TypedEntityStorageBuilder.create()
    val original = builder.addSampleEntity("hello")
    val modified = builder.modifyEntity(ModifiableSampleEntity::class.java, original) {
      stringProperty = "foo"
      stringListProperty.add("first")
      booleanProperty = true
      fileProperty = VirtualFileUrlManager.fromUrl("file:///xxx")
    }
    builder.checkConsistency()
    assertEquals("hello", original.stringProperty)
    assertEquals(emptyList<String>(), original.stringListProperty)
    assertEquals("foo", modified.stringProperty)
    assertEquals(listOf("first"), modified.stringListProperty)
    assertTrue(modified.booleanProperty)
    assertEquals("file:///xxx", modified.fileProperty.url)
  }

  @Test
  fun `edit self modifiable entity`() {
    val builder = TypedEntityStorageBuilder.create()
    val entity = builder.addEntity(SelfModifiableSampleEntity::class.java, SampleEntitySource("test")) {
      intProperty = 42
    }
    assertEquals(42, entity.intProperty)
    val modified = builder.modifyEntity(SelfModifiableSampleEntity::class.java, entity) {
      intProperty = 239
    }
    builder.checkConsistency()
    assertEquals(42, entity.intProperty)
    assertEquals(239, modified.intProperty)
    builder.removeEntity(modified)
    builder.checkConsistency()
    assertEquals(emptyList<SelfModifiableSampleEntity>(), builder.entities(SelfModifiableSampleEntity::class).toList())
  }

  @Test
  fun `builder from storage`() {
    val storage = TypedEntityStorageBuilder.create().apply {
      addSampleEntity("hello")
    }.toStorage()
    storage.checkConsistency()

    assertEquals("hello", storage.singleSampleEntity().stringProperty)

    val builder = TypedEntityStorageBuilder.from(storage)
    builder.checkConsistency()

    assertEquals("hello", builder.singleSampleEntity().stringProperty)
    builder.modifyEntity(ModifiableSampleEntity::class.java, builder.singleSampleEntity()) {
      stringProperty = "good bye"
    }
    builder.checkConsistency()

    assertEquals("hello", storage.singleSampleEntity().stringProperty)
    assertEquals("good bye", builder.singleSampleEntity().stringProperty)
  }

  @Test
  fun `snapshot from builder`() {
    val builder = TypedEntityStorageBuilder.create()
    builder.addSampleEntity("hello")

    val snapshot = builder.toStorage()

    snapshot.checkConsistency()
    assertEquals("hello", builder.singleSampleEntity().stringProperty)
    assertEquals("hello", snapshot.singleSampleEntity().stringProperty)

    builder.modifyEntity(ModifiableSampleEntity::class.java, builder.singleSampleEntity()) {
      stringProperty = "good bye"
    }
    builder.checkConsistency()

    assertEquals("hello", snapshot.singleSampleEntity().stringProperty)
    assertEquals("good bye", builder.singleSampleEntity().stringProperty)
  }

  @Test(expected = IllegalStateException::class)
  fun `modifications are allowed inside special methods only`() {
    val entity = TypedEntityStorageBuilder.create().addEntity(SelfModifiableSampleEntity::class.java, SampleEntitySource("test")) {
      intProperty = 10
    }
    entity.intProperty = 30
  }

  @Test
  fun `different entities with same properties`() {
    val builder = TypedEntityStorageBuilder.create()
    val foo1 = builder.addSampleEntity("foo1")
    val foo2 = builder.addSampleEntity("foo1")
    val bar = builder.addSampleEntity("bar")
    builder.checkConsistency()
    assertFalse(foo1 == foo2)
    assertTrue(foo1.hasEqualProperties(foo1))
    assertTrue(foo1.hasEqualProperties(foo2))
    assertFalse(foo1.hasEqualProperties(bar))

    val bar2 = builder.modifyEntity(ModifiableSampleEntity::class.java, bar) {
      stringProperty = "bar2"
    }
    assertTrue(bar == bar2)
    assertFalse(bar.hasEqualProperties(bar2))

    val foo2a = builder.modifyEntity(ModifiableSampleEntity::class.java, foo2) {
      stringProperty = "foo2"
    }
    assertFalse(foo1.hasEqualProperties(foo2a))
  }

  @Test
  fun `change source`() {
    val builder = TypedEntityStorageBuilder.create()
    val source1 = SampleEntitySource("1")
    val source2 = SampleEntitySource("2")
    val foo = builder.addSampleEntity("foo", source1)
    val foo2 = builder.changeSource(foo, source2)
    assertEquals(source1, foo.entitySource)
    assertEquals(source2, foo2.entitySource)
    assertEquals(source2, builder.singleSampleEntity().entitySource)
    assertEquals(foo2, builder.entitiesBySource { it == source2 }.values.flatMap { it.values.flatten() }.single())
    assertTrue(builder.entitiesBySource { it == source1 }.values.all { it.isEmpty() })
  }
}

@TestOnly
fun TypedEntityStorage.checkConsistency() {
  val storage = this as ProxyBasedEntityStorage
  storage.entitiesByType.forEach { (clazz, entities) ->
    entities.forEach { assertTrue("Incorrect type key $clazz for entity of type ${it.unmodifiableEntityType}", it.unmodifiableEntityType.isAssignableFrom(clazz)) }
  }
  storage.entitiesBySource.forEach { (source, entities) ->
    entities.forEach { assertEquals("Incorrect source key $source for entity ${it.id} with source ${it.entitySource}", source, it.entitySource) }
  }
  storage.entityById.forEach { (id, entity) ->
    assertEquals("Incorrect id key $id for entity with id ${entity.id}", id, entity.id)
  }

  val allEntitiesByType = storage.entitiesByType.flatMapTo(THashSet(TObjectHashingStrategy.IDENTITY)) { it.value }
  val allEntitiesBySource = storage.entitiesBySource.flatMapTo(THashSet(TObjectHashingStrategy.IDENTITY)) { it.value }
  assertEquals(emptySet<TypedEntity>(), allEntitiesBySource - allEntitiesByType)
  assertEquals(emptySet<TypedEntity>(), allEntitiesByType - allEntitiesBySource)

  val allEntitiesByPersistentId = storage.entitiesByPersistentIdHash.flatMapTo(THashSet(TObjectHashingStrategy.IDENTITY)) { it.value }
  val expectedEntitiesByPersistentId = allEntitiesByType.filterTo(THashSet(TObjectHashingStrategy.IDENTITY)) { TypedEntityWithPersistentId::class.java.isAssignableFrom((it as EntityData).unmodifiableEntityType) }
  assertEquals(expectedEntitiesByPersistentId, allEntitiesByPersistentId)
  storage.entitiesByPersistentIdHash.forEach { (hash, list) ->
    list.forEach {
      assertEquals(hash, (storage.createEntityInstance(it) as TypedEntityWithPersistentId).persistentId().hashCode())
    }
  }

  val allEntitiesById = storage.entityById.values.toCollection(THashSet(TObjectHashingStrategy.IDENTITY))
  assertEquals(emptySet<TypedEntity>(), allEntitiesBySource - allEntitiesById)
  assertEquals(emptySet<TypedEntity>(), allEntitiesById - allEntitiesBySource)

  val expectedReferrers = storage.entityById.values.flatMap { data ->
    val result = mutableListOf<Pair<Long, Long>>()
    data.collectReferences { result.add(it to data.id) }
    result
  }.groupBy({it.first}, {it.second})
  storage.entityById.values.forEach { data ->
    val expected = expectedReferrers[data.id]?.toSet()
    val actual = storage.referrers[data.id]?.toSet()
    assertEquals("Different referrers to $data", expected, actual)
  }
  val staleKeys = storage.referrers.keys - storage.entityById.keys
  assertEquals(emptySet<Long>(), staleKeys)

  fun assertReferrersEqual(expected: Map<Long, List<Long>>, actual: Map<Long, List<Long>>) {
    assertEquals(expected.keys, actual.keys)
    for (key in expected.keys) {
      assertEquals(expected.getValue(key).toSet(), actual.getValue(key).toSet())
    }
  }

  assertReferrersEqual(expectedReferrers, storage.referrers)
}