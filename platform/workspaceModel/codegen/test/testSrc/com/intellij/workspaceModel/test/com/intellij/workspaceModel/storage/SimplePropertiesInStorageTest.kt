// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage

import com.intellij.workspaceModel.storage.entities.SampleEntity
import com.intellij.workspaceModel.storage.entities.SampleEntity.Companion.booleanProperty
import com.intellij.workspaceModel.storage.entities.SampleEntity.Companion.stringListProperty
import com.intellij.workspaceModel.storage.entities.SampleEntity.Companion.stringProperty
import com.intellij.workspaceModel.storage.entities.SecondSampleEntity.Companion.intProperty
import com.intellij.workspaceModel.storage.entities.addSampleEntity
import com.intellij.workspaceModel.storage.impl.url.VirtualFileUrlManagerImpl
import com.intellij.workspaceModel.codegen.storage.url.VirtualFileUrlManager
import org.jetbrains.deft.IntellijWsTestIj.modifyEntity
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal fun WorkspaceEntityStorage.singleSampleEntity() = entities(SampleEntity::class.java).single()

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
    val modified = builder.modifyEntity(original) {
      stringProperty = "foo"
      stringListProperty = stringListProperty + "first"
      booleanProperty = true
      fileProperty = virtualFileManager.fromUrl("file:///xxx")
    }
    assertEquals("foo", original.stringProperty)
    assertEquals(listOf("first"), original.stringListProperty)
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
    builder.modifyEntity(builder.singleSampleEntity()) {
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

    builder.modifyEntity(builder.singleSampleEntity()) {
      stringProperty = "good bye"
    }

    assertEquals("hello", snapshot.singleSampleEntity().stringProperty)
    assertEquals("good bye", builder.singleSampleEntity().stringProperty)
  }

  @Test
  fun `change source`() {
    val builder = createEmptyBuilder()
    val source1 = SampleEntitySource("1")
    val source2 = SampleEntitySource("2")
    val foo = builder.addSampleEntity("foo", source1)
    assertEquals(source1, foo.entitySource)
    val foo2 = builder.changeSource(foo, source2)
    assertEquals(source2, foo.entitySource)
    assertEquals(source2, foo2.entitySource)
    assertEquals(source2, builder.singleSampleEntity().entitySource)
    assertEquals(foo2, builder.entitiesBySource { it == source2 }.values.flatMap { it.values.flatten() }.single())
    assertTrue(builder.entitiesBySource { it == source1 }.values.all { it.isEmpty() })
  }

  @Test
  fun `test trying to modify non-existing entity`() {
    val builder = createEmptyBuilder()
    val sampleEntity = builder.addSampleEntity("Prop")
    val anotherBuilder = createEmptyBuilder()
    assertThrows<IllegalStateException> {
      anotherBuilder.modifyEntity(sampleEntity) {
        this.stringProperty = "Another prop"
      }
    }
  }
}
