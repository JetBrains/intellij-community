// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests

import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.impl.url.VirtualFileUrlManagerImpl
import com.intellij.platform.workspace.storage.testEntities.entities.*
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal fun EntityStorage.singleSampleEntity() = entities(SampleEntity::class.java).single()

class SimplePropertiesInStorageTest {
  private lateinit var virtualFileManager: VirtualFileUrlManager

  @BeforeEach
  fun setUp() {
    virtualFileManager = VirtualFileUrlManagerImpl()
  }

  @Test
  fun `add entity`() {
    val builder = createEmptyBuilder()
    val source = SampleEntitySource("test")
    val entity = builder addEntity SampleEntity(true, "hello", mutableListOf("one", "two"), HashMap(),
                                                virtualFileManager.getOrCreateFromUri("file:///tmp"), source)
    assertTrue(entity.booleanProperty)
    assertEquals("hello", entity.stringProperty)
    assertEquals(listOf("one", "two"), entity.stringListProperty)
    assertEquals(entity, builder.singleSampleEntity())
    assertEquals(source, entity.entitySource)
  }

  @Test
  fun `remove entity`() {
    val builder = createEmptyBuilder()
    val entity = builder addEntity SampleEntity(false, "hello", ArrayList(), HashMap(), VirtualFileUrlManagerImpl().getOrCreateFromUri("file:///tmp"),
                                                SampleEntitySource("test"))
    builder.removeEntity(entity)
    assertTrue(builder.entities(SampleEntity::class.java).toList().isEmpty())
  }

  @Test
  @Disabled("Api change")
  fun `modify entity`() {
    val builder = createEmptyBuilder()
    val original = builder addEntity SampleEntity(false, "hello", ArrayList(), HashMap(),
                                                  VirtualFileUrlManagerImpl().getOrCreateFromUri("file:///tmp"), SampleEntitySource("test"))
    val modified = builder.modifyEntity(original) {
      stringProperty = "foo"
      stringListProperty.add("first")
      booleanProperty = true
      fileProperty = virtualFileManager.getOrCreateFromUri("file:///xxx")
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
      this addEntity SampleEntity(false, "hello", ArrayList(), HashMap(), VirtualFileUrlManagerImpl().getOrCreateFromUri("file:///tmp"),
                                  SampleEntitySource("test"))
    }.toSnapshot()

    assertEquals("hello", storage.singleSampleEntity().stringProperty)

    val builder = createBuilderFrom(storage)

    assertEquals("hello", builder.singleSampleEntity().stringProperty)
    builder.modifyEntity(builder.singleSampleEntity()) {
      stringProperty = "good bye"
    }

    assertEquals("hello", storage.singleSampleEntity().stringProperty)
    assertEquals("good bye", builder.singleSampleEntity().stringProperty)
  }

  @Test
  fun `snapshot from builder`() {
    val builder = createEmptyBuilder()
    builder addEntity SampleEntity(false, "hello", ArrayList(), HashMap(), VirtualFileUrlManagerImpl().getOrCreateFromUri("file:///tmp"),
                                   SampleEntitySource("test"))

    val snapshot = builder.toSnapshot()

    assertEquals("hello", builder.singleSampleEntity().stringProperty)
    assertEquals("hello", snapshot.singleSampleEntity().stringProperty)

    builder.modifyEntity(builder.singleSampleEntity()) {
      stringProperty = "good bye"
    }

    assertEquals("hello", snapshot.singleSampleEntity().stringProperty)
    assertEquals("good bye", builder.singleSampleEntity().stringProperty)
  }

  /*
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
  */

  @Test
  @Disabled("Api change")
  fun `change source`() {
    val builder = createEmptyBuilder()
    val source1 = SampleEntitySource("1")
    val source2 = SampleEntitySource("2")
    val foo = builder addEntity SampleEntity(false, "foo", ArrayList(), HashMap(), VirtualFileUrlManagerImpl().getOrCreateFromUri("file:///tmp"),
                                             source1)
    val foo2 = builder.modifyEntity(foo) { this.entitySource = source2 }
    assertEquals(source1, foo.entitySource)
    assertEquals(source2, foo2.entitySource)
    assertEquals(source2, builder.singleSampleEntity().entitySource)
    assertEquals(foo2, builder.entitiesBySource { it == source2 }.single())
    assertTrue(builder.entitiesBySource { it == source1 }.none())
  }

  @Test
  fun `test trying to modify non-existing entity`() {
    val builder = createEmptyBuilder()
    val sampleEntity = builder addEntity SampleEntity(false, "Prop", ArrayList(), HashMap(),
                                                      VirtualFileUrlManagerImpl().getOrCreateFromUri("file:///tmp"), SampleEntitySource("test"))
    val anotherBuilder = createEmptyBuilder()
    assertThrows<IllegalStateException> {
      anotherBuilder.modifyEntity(sampleEntity) {
        this.stringProperty = "Another prop"
      }
    }
  }
}

class ExtensionParentListTest {
  @Test
  fun `access by extension without builder`() {
    val entity = AttachedEntityParentList("xyz", MySource) {
      ref = MainEntityParentList("123", MySource)
    }

    assertEquals("xyz", entity.data)
    val ref = entity.ref
    val children = ref!!.children
    assertEquals("xyz", children.single().data)
  }

  @Test
  fun `access by extension without builder on parent`() {
    val entity = MainEntityParentList("123", MySource) {
      this.children = listOf(
        AttachedEntityParentList("xyz", MySource)
      )
    }

    assertEquals("123", entity.x)
    val ref = entity.children.single()
    val children = ref.ref
    assertEquals("123", children!!.x)
  }

  @Test
  fun `access by extension without builder on parent with an additional children`() {
    val entity = MainEntityParentList("123", MySource) {
      this.children = listOf(
        AttachedEntityParentList("xyz", MySource)
      )
    }
    val newChild = AttachedEntityParentList("abc", MySource) {
      this.ref = entity
    }

    assertEquals("123", entity.x)
    val ref = entity.children.first()
    val children = ref.ref
    assertEquals("123", children!!.x)

    assertEquals(2, newChild.ref!!.children.size)
  }

  @Test
  fun `access by extension`() {
    val entity = AttachedEntityParentList("xyz", MySource) {
      ref = MainEntityParentList("123", MySource)
    }
    val builder = createEmptyBuilder()
    builder.addEntity(entity)

    assertEquals("xyz", entity.data)
    val ref = entity.ref
    val children = ref!!.children
    assertEquals("xyz", children.single().data)

    assertEquals("xyz", builder.entities(AttachedEntityParentList::class.java).single().data)
    assertEquals("123", builder.entities(MainEntityParentList::class.java).single().x)
    assertEquals("xyz", builder.entities(MainEntityParentList::class.java).single().children.single().data)
    assertEquals("123", builder.entities(AttachedEntityParentList::class.java).single().ref!!.x)
  }

  @Test
  fun `access by extension on parent`() {
    val entity = MainEntityParentList("123", MySource) {
      this.children = listOf(
        AttachedEntityParentList("xyz", MySource)
      )
    }
    val builder = createEmptyBuilder()
    builder.addEntity(entity)

    assertEquals("123", entity.x)
    val ref = entity.children.single()
    val children = ref.ref
    assertEquals("123", children!!.x)

    assertEquals("xyz", builder.entities(AttachedEntityParentList::class.java).single().data)
    assertEquals("123", builder.entities(MainEntityParentList::class.java).single().x)
    assertEquals("xyz", builder.entities(MainEntityParentList::class.java).single().children.single().data)
    assertEquals("123", builder.entities(AttachedEntityParentList::class.java).single().ref!!.x)
  }

  @Test
  fun `add via single children`() {
    val child = AttachedEntityParentList("abc", MySource)
    val entity = MainEntityParentList("123", MySource) {
      this.children = listOf(
        AttachedEntityParentList("xyz", MySource),
        child
      )
    }
    val builder = createEmptyBuilder()
    builder.addEntity(child)

    assertEquals("123", entity.x)
    val ref = entity.children.first()
    val children2 = ref.ref
    assertEquals("123", children2!!.x)

    assertEquals("xyz", builder.entities(AttachedEntityParentList::class.java).single { it.data == "xyz" }.data)
    assertEquals("123", builder.entities(MainEntityParentList::class.java).single().x)
    assertEquals("xyz", builder.entities(MainEntityParentList::class.java).single().children.single { it.data == "xyz" }.data)
    assertEquals("123", builder.entities(AttachedEntityParentList::class.java).single { it.data == "xyz" }.ref!!.x)
  }

  @Test
  fun `partially in builder`() {
    val entity = MainEntityParentList("123", MySource) {
      this.children = listOf(
        AttachedEntityParentList("xyz", MySource),
      )
    }
    val builder = createEmptyBuilder()
    builder.addEntity(entity)
    val children = AttachedEntityParentList("abc", MySource) {
      this.ref = entity
    }

    assertEquals(2, entity.children.size)

    assertEquals("xyz", entity.children.single { it.data == "xyz" }.data)
    assertEquals("abc", entity.children.single { it.data == "abc" }.data)

    assertEquals("123", children.ref!!.x)

    assertEquals("123", entity.x)
    val ref = entity.children.first()
    val children2 = ref.ref
    assertEquals("123", children2!!.x)

    assertEquals("xyz", builder.entities(AttachedEntityParentList::class.java).single { it.data == "xyz" }.data)
    assertEquals("123", builder.entities(MainEntityParentList::class.java).single().x)
    assertEquals("xyz", builder.entities(MainEntityParentList::class.java).single().children.single { it.data == "xyz" }.data)
    assertEquals("123", builder.entities(AttachedEntityParentList::class.java).single { it.data == "xyz" }.ref!!.x)
  }
}