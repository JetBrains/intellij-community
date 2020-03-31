package com.intellij.workspace.api

import com.intellij.workspace.api.pstorage.*
import org.junit.Assert.*
import org.junit.Test

internal class PSampleEntityData : PEntityData<PSampleEntity> {
  override lateinit var entitySource: EntitySource
  override var id: Int = -1

  var booleanProperty: Boolean = false
  lateinit var stringProperty: String
  lateinit var stringListProperty: List<String>
  lateinit var fileProperty: VirtualFileUrl

  override fun createEntity(snapshot: PEntityStorage) =
    PSampleEntity(entitySource, id, booleanProperty, stringProperty, ArrayList(stringListProperty), fileProperty)

  override fun wrapAsModifiable(diff: PEntityStorageBuilder) = PModifiableSampleEntity(this, diff)
}

internal class PSampleEntity(
  override val entitySource: EntitySource,
  arrayId: Int,
  val booleanProperty: Boolean,
  val stringProperty: String,
  val stringListProperty: List<String>,
  val fileProperty: VirtualFileUrl
) : PTypedEntity<PSampleEntity>() {
  override val id: PId<PSampleEntity> = PId(arrayId, this.javaClass.kotlin)
}

@PEntityDataClass(PSampleEntityData::class)
@PEntityClass(PSampleEntity::class)
internal class PModifiableSampleEntity(val original: PSampleEntityData,
                                       val diff: PEntityStorageBuilder) : PModifiableTypedEntity<PSampleEntity>() {
  override fun hasEqualProperties(e: TypedEntity): Boolean = TODO("Not yet implemented")

  var booleanProperty: Boolean by Another(original)
  var stringProperty: String by Another(original)
  var stringListProperty: MutableList<String> by Another(original)
  var fileProperty: VirtualFileUrl by Another(original)

  override val id: PId<PSampleEntity> = PId(original.id, PSampleEntity::class)

  override val entitySource = original.entitySource
}

inline fun <reified M : PModifiableTypedEntity<T>, T : PTypedEntity<T>> TypedEntityStorageBuilder.addPEntity(source: EntitySource,
                                                                                                             noinline initializer: M.() -> Unit): T = addEntity(
  M::class.java, source, initializer)

internal fun TypedEntityStorageBuilder.addPSampleEntity(stringProperty: String,
                                                        source: EntitySource = PSampleEntitySource("test"),
                                                        booleanProperty: Boolean = false,
                                                        stringListProperty: MutableList<String> = ArrayList(),
                                                        fileProperty: VirtualFileUrl = VirtualFileUrlManager.fromUrl(
                                                          "file:///tmp")): PSampleEntity {
  return addPEntity<PModifiableSampleEntity, PSampleEntity>(source) {
    this.booleanProperty = booleanProperty
    this.stringProperty = stringProperty
    this.stringListProperty = stringListProperty
    this.fileProperty = fileProperty
  }
}

internal fun TypedEntityStorage.singlePSampleEntity() = entities(PSampleEntity::class.java).single()

internal data class PSampleEntitySource(val name: String) : EntitySource

class PSimplePropertiesInProxyBasedStorageTest {
  @Test
  fun `add entity`() {
    val builder = PEntityStorage.create()
    val source = PSampleEntitySource("test")
    val entity = builder.addPSampleEntity("hello", source, true, mutableListOf("one", "two"))
    assertTrue(entity.booleanProperty)
    assertEquals("hello", entity.stringProperty)
    assertEquals(listOf("one", "two"), entity.stringListProperty)
    assertEquals(entity, builder.singlePSampleEntity())
    assertEquals(source, entity.entitySource)
  }

  @Test
  fun `remove entity`() {
    val builder = PEntityStorage.create()
    val entity = builder.addPSampleEntity("hello")
    builder.removeEntity(entity)
    assertTrue(builder.entities(PSampleEntity::class.java).toList().isEmpty())
  }

  @Test
  fun `modify entity`() {
    val builder = PEntityStorage.create()
    val original = builder.addPSampleEntity("hello")
    val modified = builder.modifyEntity(PModifiableSampleEntity::class.java, original) {
      stringProperty = "foo"
      stringListProperty.add("first")
      booleanProperty = true
      fileProperty = VirtualFileUrlManager.fromUrl("file:///xxx")
    }
    assertEquals("hello", original.stringProperty)
    assertEquals(emptyList<String>(), original.stringListProperty)
    assertEquals("foo", modified.stringProperty)
    assertEquals(listOf("first"), modified.stringListProperty)
    assertTrue(modified.booleanProperty)
    assertEquals("file:///xxx", modified.fileProperty.url)
  }

  @Test
  fun `builder from storage`() {
    val storage = PEntityStorage.create().apply {
      addPSampleEntity("hello")
    }.toStorage()

    assertEquals("hello", storage.singlePSampleEntity().stringProperty)

    val builder = PEntityStorageBuilder.from(storage)

    assertEquals("hello", builder.singlePSampleEntity().stringProperty)
    builder.modifyEntity(PModifiableSampleEntity::class.java, builder.singlePSampleEntity()) {
      stringProperty = "good bye"
    }

    assertEquals("hello", storage.singlePSampleEntity().stringProperty)
    assertEquals("good bye", builder.singlePSampleEntity().stringProperty)
  }

  @Test
  fun `snapshot from builder`() {
    val builder = PEntityStorage.create()
    builder.addPSampleEntity("hello")

    val snapshot = builder.toStorage()

    assertEquals("hello", builder.singlePSampleEntity().stringProperty)
    assertEquals("hello", snapshot.singlePSampleEntity().stringProperty)

    builder.modifyEntity(PModifiableSampleEntity::class.java, builder.singlePSampleEntity()) {
      stringProperty = "good bye"
    }

    assertEquals("hello", snapshot.singlePSampleEntity().stringProperty)
    assertEquals("good bye", builder.singlePSampleEntity().stringProperty)
  }

  @Test
  fun `different entities with same properties`() {
    val builder = PEntityStorage.create()
    val foo1 = builder.addPSampleEntity("foo1")
    val foo2 = builder.addPSampleEntity("foo1")
    val bar = builder.addPSampleEntity("bar")
    assertFalse(foo1 == foo2)
    assertTrue(foo1.hasEqualProperties(foo1))
    assertTrue(foo1.hasEqualProperties(foo2))
    assertFalse(foo1.hasEqualProperties(bar))

    val bar2 = builder.modifyEntity(PModifiableSampleEntity::class.java, bar) {
      stringProperty = "bar2"
    }
    assertTrue(bar == bar2)
    assertFalse(bar.hasEqualProperties(bar2))

    val foo2a = builder.modifyEntity(PModifiableSampleEntity::class.java, foo2) {
      stringProperty = "foo2"
    }
    assertFalse(foo1.hasEqualProperties(foo2a))
  }

  @Test
  fun `change source`() {
    val builder = PEntityStorage.create()
    val source1 = PSampleEntitySource("1")
    val source2 = PSampleEntitySource("2")
    val foo = builder.addPSampleEntity("foo", source1)
    val foo2 = builder.changeSource(foo, source2)
    assertEquals(source1, foo.entitySource)
    assertEquals(source2, foo2.entitySource)
    assertEquals(source2, builder.singlePSampleEntity().entitySource)
    assertEquals(foo2, builder.entitiesBySource { it == source2 }.values.flatMap { it.values.flatten() }.single())
    assertTrue(builder.entitiesBySource { it == source1 }.values.all { it.isEmpty() })
  }
}
