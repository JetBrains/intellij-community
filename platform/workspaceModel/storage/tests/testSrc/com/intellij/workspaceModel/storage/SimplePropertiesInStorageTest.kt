// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage

import com.intellij.openapi.util.Ref
import com.intellij.workspaceModel.storage.entities.*
import com.intellij.workspaceModel.storage.impl.url.VirtualFileUrlManagerImpl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

internal fun WorkspaceEntityStorage.singleSampleEntity() = entities(SampleEntity::class.java).single()

class SimplePropertiesInStorageTest {
  private lateinit var virtualFileManager: VirtualFileUrlManager
  @Before
  fun setUp() {
    virtualFileManager = VirtualFileUrlManagerImpl()
  }

  @Test
  fun `add entity`() {
    val builder = createEmptyBuilder()
    val source = SampleEntitySource("test")
    val entity = builder.addSampleEntity("hello", source, true, mutableListOf("one", "two"))
    assertTrue(entity.booleanProperty)
    assertEquals("hello", entity.stringProperty)
    assertEquals(listOf("one", "two"), entity.stringListProperty)
    assertEquals(entity, builder.singleSampleEntity())
    assertEquals(source, entity.entitySource)
  }

  @Test
  fun `remove entity`() {
    val builder = createEmptyBuilder()
    val entity = builder.addSampleEntity("hello")
    builder.removeEntity(entity)
    assertTrue(builder.entities(SampleEntity::class.java).toList().isEmpty())
  }

  @Test
  fun `modify entity`() {
    val builder = createEmptyBuilder()
    val original = builder.addSampleEntity("hello")
    val modified = builder.modifyEntity(ModifiableSampleEntity::class.java, original) {
      stringProperty = "foo"
      stringListProperty = stringListProperty + "first"
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
    val storage = createEmptyBuilder().apply {
      addSampleEntity("hello")
    }.toStorage()

    assertEquals("hello", storage.singleSampleEntity().stringProperty)

    val builder = createBuilderFrom(storage)

    assertEquals("hello", builder.singleSampleEntity().stringProperty)
    builder.modifyEntity(ModifiableSampleEntity::class.java, builder.singleSampleEntity()) {
      stringProperty = "good bye"
    }

    assertEquals("hello", storage.singleSampleEntity().stringProperty)
    assertEquals("good bye", builder.singleSampleEntity().stringProperty)
  }

  @Test
  fun `snapshot from builder`() {
    val builder = createEmptyBuilder()
    builder.addSampleEntity("hello")

    val snapshot = builder.toStorage()

    assertEquals("hello", builder.singleSampleEntity().stringProperty)
    assertEquals("hello", snapshot.singleSampleEntity().stringProperty)

    builder.modifyEntity(ModifiableSampleEntity::class.java, builder.singleSampleEntity()) {
      stringProperty = "good bye"
    }

    assertEquals("hello", snapshot.singleSampleEntity().stringProperty)
    assertEquals("good bye", builder.singleSampleEntity().stringProperty)
  }

  @Test
  fun `different entities with same properties`() {
    val builder = createEmptyBuilder()
    val foo1 = builder.addSampleEntity("foo1", virtualFileManager = virtualFileManager)
    val foo2 = builder.addSampleEntity("foo1", virtualFileManager = virtualFileManager)
    val bar = builder.addSampleEntity("bar", virtualFileManager = virtualFileManager)
    assertFalse(foo1 == foo2)
    assertTrue(foo1.hasEqualProperties(foo1))
    assertTrue(foo1.hasEqualProperties(foo2))
    assertFalse(foo1.hasEqualProperties(bar))
    val builder2 = createEmptyBuilder()
    val foo1a = builder2.addSampleEntity("foo1", virtualFileManager = virtualFileManager)
    assertTrue(foo1.hasEqualProperties(foo1a))

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
    val builder = createEmptyBuilder()
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

  @Test(expected = IllegalStateException::class)
  fun `modifications are allowed inside special methods only`() {
    val thief = Ref.create<ModifiableSecondSampleEntity>()
    val builder = createEmptyBuilder()
    builder.addEntity(ModifiableSecondSampleEntity::class.java, SampleEntitySource("test")) {
      thief.set(this)
      intProperty = 10
    }
    thief.get().intProperty = 30
  }

  @Test(expected = IllegalStateException::class)
  fun `test trying to modify non-existing entity`() {
    val builder = createEmptyBuilder()
    val sampleEntity = builder.addSampleEntity("Prop")
    val anotherBuilder = createEmptyBuilder()
    anotherBuilder.modifyEntity(ModifiableSampleEntity::class.java, sampleEntity) {
      this.stringProperty = "Another prop"
    }
  }
}
