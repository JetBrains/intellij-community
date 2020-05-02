package com.intellij.workspace.api

import org.junit.Assert.*
import org.junit.Before
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
                                                       virtualFileManager: VirtualFileUrlManager = VirtualFileUrlManager(),
                                                       fileProperty: VirtualFileUrl = virtualFileManager.fromUrl("file:///tmp")): SampleEntity {
  return addEntity<ModifiableSampleEntity, SampleEntity>(source) {
    this.booleanProperty = booleanProperty
    this.stringProperty = stringProperty
    this.stringListProperty = stringListProperty
    this.fileProperty = fileProperty
  }
}

internal fun TypedEntityStorage.singleSampleEntity() = entities(SampleEntity::class.java).single()

internal data class SampleEntitySource(val name: String) : EntitySource

class SimplePropertiesInProxyBasedStorageTest {
  private lateinit var virtualFileManager: VirtualFileUrlManager
  @Before
  fun setUp() {
    virtualFileManager = VirtualFileUrlManager()
  }

  @Test
  fun `add entity`() {
    val builder = TypedEntityStorageBuilder.createProxy()
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
    val builder = TypedEntityStorageBuilder.createProxy()
    val entity = builder.addSampleEntity("hello")
    builder.removeEntity(entity)
    builder.checkConsistency()
    assertTrue(builder.entities(SampleEntity::class.java).toList().isEmpty())
  }

  @Test
  fun `modify entity`() {
    val builder = TypedEntityStorageBuilder.createProxy()
    val original = builder.addSampleEntity("hello")
    val modified = builder.modifyEntity(ModifiableSampleEntity::class.java, original) {
      stringProperty = "foo"
      stringListProperty.add("first")
      booleanProperty = true
      fileProperty = virtualFileManager.fromUrl("file:///xxx")
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
    val builder = TypedEntityStorageBuilder.createProxy()
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
    assertEquals(emptyList<SelfModifiableSampleEntity>(), builder.entities(SelfModifiableSampleEntity::class.java).toList())
  }

  @Test
  fun `builder from storage`() {
    val storage = TypedEntityStorageBuilder.createProxy().apply {
      addSampleEntity("hello")
    }.toStorage()
    storage.checkConsistency()

    assertEquals("hello", storage.singleSampleEntity().stringProperty)

    val builder = TypedEntityStorageBuilder.fromProxy(storage)
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
    val builder = TypedEntityStorageBuilder.createProxy()
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
    val entity = TypedEntityStorageBuilder.createProxy().addEntity(SelfModifiableSampleEntity::class.java, SampleEntitySource("test")) {
      intProperty = 10
    }
    entity.intProperty = 30
  }

  @Test
  fun `different entities with same properties`() {
    val builder = TypedEntityStorageBuilder.createProxy()
    val foo1 = builder.addSampleEntity("foo1", virtualFileManager = virtualFileManager)
    val foo2 = builder.addSampleEntity("foo1", virtualFileManager = virtualFileManager)
    val bar = builder.addSampleEntity("bar", virtualFileManager = virtualFileManager)
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
    val builder = TypedEntityStorageBuilder.createProxy()
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
