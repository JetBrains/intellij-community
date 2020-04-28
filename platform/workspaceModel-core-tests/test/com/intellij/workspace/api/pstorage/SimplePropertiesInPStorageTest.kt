// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage

import com.intellij.openapi.util.Ref
import com.intellij.workspace.api.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

internal class PSampleEntityData : PEntityData<PSampleEntity>() {
  var booleanProperty: Boolean = false
  lateinit var stringProperty: String
  lateinit var stringListProperty: List<String>
  lateinit var fileProperty: VirtualFileUrl
  override fun createEntity(snapshot: TypedEntityStorage): PSampleEntity {
    return PSampleEntity(booleanProperty, stringProperty, stringListProperty.toList(), fileProperty).also { addMetaData(it, snapshot) }
  }
}

internal class PSampleEntity(
  val booleanProperty: Boolean,
  val stringProperty: String,
  val stringListProperty: List<String>,
  val fileProperty: VirtualFileUrl
) : PTypedEntity()

internal class ModifiablePSampleEntity : PModifiableTypedEntity<PSampleEntity>() {
  var booleanProperty: Boolean by EntityDataDelegation()
  var stringProperty: String by EntityDataDelegation()
  var stringListProperty: MutableList<String> by EntityDataDelegation()
  var fileProperty: VirtualFileUrl by EntityDataDelegation()
}

internal fun TypedEntityStorageBuilder.addPSampleEntity(stringProperty: String,
                                                        source: EntitySource = PSampleEntitySource("test"),
                                                        booleanProperty: Boolean = false,
                                                        stringListProperty: MutableList<String> = ArrayList(),
                                                        virtualFileManager: VirtualFileUrlManager = VirtualFileUrlManager(),
                                                        fileProperty: VirtualFileUrl = virtualFileManager.fromUrl(
                                                          "file:///tmp")): PSampleEntity {
  return addEntity(ModifiablePSampleEntity::class.java, source) {
    this.booleanProperty = booleanProperty
    this.stringProperty = stringProperty
    this.stringListProperty = stringListProperty
    this.fileProperty = fileProperty
  }
}

internal fun TypedEntityStorage.singlePSampleEntity() = entities(PSampleEntity::class.java).single()

internal data class PSampleEntitySource(val name: String) : EntitySource

class PSimplePropertiesInProxyBasedStorageTest {
  private lateinit var virtualFileManager: VirtualFileUrlManager
  @Before
  fun setUp() {
    virtualFileManager = VirtualFileUrlManager()
  }

  @Test
  fun `add entity`() {
    val builder = PEntityStorageBuilder.create()
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
    val builder = PEntityStorageBuilder.create()
    val entity = builder.addPSampleEntity("hello")
    builder.removeEntity(entity)
    assertTrue(builder.entities(PSampleEntity::class.java).toList().isEmpty())
  }

  @Test
  fun `modify entity`() {
    val builder = PEntityStorageBuilder.create()
    val original = builder.addPSampleEntity("hello")
    val modified = builder.modifyEntity(ModifiablePSampleEntity::class.java, original) {
      stringProperty = "foo"
      stringListProperty.add("first")
      booleanProperty = true
      fileProperty = virtualFileManager.fromUrl("file:///xxx")
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
    val storage = PEntityStorageBuilder.create().apply {
      addPSampleEntity("hello")
    }.toStorage()

    assertEquals("hello", storage.singlePSampleEntity().stringProperty)

    val builder = PEntityStorageBuilder.from(storage)

    assertEquals("hello", builder.singlePSampleEntity().stringProperty)
    builder.modifyEntity(ModifiablePSampleEntity::class.java, builder.singlePSampleEntity()) {
      stringProperty = "good bye"
    }

    assertEquals("hello", storage.singlePSampleEntity().stringProperty)
    assertEquals("good bye", builder.singlePSampleEntity().stringProperty)
  }

  @Test
  fun `snapshot from builder`() {
    val builder = PEntityStorageBuilder.create()
    builder.addPSampleEntity("hello")

    val snapshot = builder.toStorage()

    assertEquals("hello", builder.singlePSampleEntity().stringProperty)
    assertEquals("hello", snapshot.singlePSampleEntity().stringProperty)

    builder.modifyEntity(ModifiablePSampleEntity::class.java, builder.singlePSampleEntity()) {
      stringProperty = "good bye"
    }

    assertEquals("hello", snapshot.singlePSampleEntity().stringProperty)
    assertEquals("good bye", builder.singlePSampleEntity().stringProperty)
  }

  @Test
  fun `different entities with same properties`() {
    val builder = PEntityStorageBuilder.create()
    val foo1 = builder.addPSampleEntity("foo1", virtualFileManager = virtualFileManager)
    val foo2 = builder.addPSampleEntity("foo1", virtualFileManager = virtualFileManager)
    val bar = builder.addPSampleEntity("bar", virtualFileManager = virtualFileManager)
    assertFalse(foo1 == foo2)
    assertTrue(foo1.hasEqualProperties(foo1))
    assertTrue(foo1.hasEqualProperties(foo2))
    assertFalse(foo1.hasEqualProperties(bar))

    val bar2 = builder.modifyEntity(ModifiablePSampleEntity::class.java, bar) {
      stringProperty = "bar2"
    }
    assertTrue(bar == bar2)
    assertFalse(bar.hasEqualProperties(bar2))

    val foo2a = builder.modifyEntity(ModifiablePSampleEntity::class.java, foo2) {
      stringProperty = "foo2"
    }
    assertFalse(foo1.hasEqualProperties(foo2a))
  }

  @Test
  fun `change source`() {
    val builder = PEntityStorageBuilder.create()
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

  @Test(expected = IllegalStateException::class)
  fun `modifications are allowed inside special methods only`() {
    val thief = Ref.create<ModifiableSecondSampleEntity>()
    val builder = PEntityStorageBuilder.create()
    builder.addEntity(ModifiableSecondSampleEntity::class.java, SampleEntitySource("test")) {
      thief.set(this)
      intProperty = 10
    }
    thief.get().intProperty = 30
  }
}
