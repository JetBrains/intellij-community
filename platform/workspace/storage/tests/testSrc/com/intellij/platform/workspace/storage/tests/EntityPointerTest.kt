// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests

import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.impl.url.VirtualFileUrlManagerImpl
import com.intellij.platform.workspace.storage.testEntities.entities.SampleEntity
import com.intellij.platform.workspace.storage.testEntities.entities.SampleEntitySource
import com.intellij.platform.workspace.storage.toBuilder
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class EntityPointerTest {
  private lateinit var virtualFileUrlManager: VirtualFileUrlManager

  @BeforeEach
  fun setUp() {
    virtualFileUrlManager = VirtualFileUrlManagerImpl()
  }

  @Test
  fun basic() {
    val builder = createEmptyBuilder()
    val entity = builder addEntity SampleEntity(false, "data", ArrayList(), HashMap(), virtualFileUrlManager.getOrCreateFromUri("file:///tmp"),
                                                SampleEntitySource("test"))
    val reference = entity.createPointer<SampleEntity>()
    assertEquals(entity, reference.resolve(builder))

    val snapshot = builder.toSnapshot()
    assertEquals(snapshot.singleSampleEntity(), reference.resolve(snapshot))
    assertNull(reference.resolve(createEmptyBuilder()))

    val newBuilder = createBuilderFrom(snapshot)
    assertEquals(newBuilder.singleSampleEntity(), reference.resolve(newBuilder))
  }

  @Test
  fun equality() {
    val builder = createEmptyBuilder()
    val entity = builder addEntity SampleEntity(false, "data", ArrayList(), HashMap(), virtualFileUrlManager.getOrCreateFromUri("file:///tmp"),
                                                SampleEntitySource("test"))
    val entity2 = builder addEntity SampleEntity(false, "data", ArrayList(), HashMap(), virtualFileUrlManager.getOrCreateFromUri("file:///tmp"),
                                                 SampleEntitySource("test"))
    val reference1 = entity.createPointer<SampleEntity>()
    val reference2 = entity.createPointer<SampleEntity>()
    val reference3 = entity2.createPointer<SampleEntity>()
    assertEquals(reference1, reference2)
    assertNotEquals(reference1, reference3)
  }

  @Test
  fun `replace entity by equal`() {
    val builder = createEmptyBuilder()
    builder addEntity SampleEntity(false, "foo", ArrayList(), HashMap(), virtualFileUrlManager.getOrCreateFromUri("file:///tmp"),
                                   SampleEntitySource("test"))
    val snapshot = builder.toSnapshot()
    val entity = snapshot.singleSampleEntity()
    val reference = entity.createPointer<SampleEntity>()
    val newBuilder = createBuilderFrom(snapshot)
    newBuilder.removeEntity(entity)
    newBuilder addEntity SampleEntity(false, "foo", ArrayList(), HashMap(), virtualFileUrlManager.getOrCreateFromUri("file:///tmp"),
                                      SampleEntitySource("test"))
    val changes = newBuilder.collectChanges()
    //if there is an event about the change, the code which stores EntityReference is supposed to update it
    if (changes.isEmpty()) {
      val updated = newBuilder.toSnapshot()
      assertEquals(updated.singleSampleEntity(), reference.resolve(updated))
    }
  }

  @Test
  @Disabled("Wrong entity reference behaviour")
  fun `wrong entity ref resolve in different storage`() {
    val mutableStorage = MutableEntityStorage.create()
    val sampleEntity = mutableStorage addEntity SampleEntity(false, "hello", ArrayList(), HashMap(),
                                                             virtualFileUrlManager.getOrCreateFromUri("file:///tmp"),
                                                             SampleEntitySource("test"))
    val reference = sampleEntity.createPointer<SampleEntity>()

    val mutableStorage1 = MutableEntityStorage.create()
    mutableStorage1 addEntity SampleEntity(false, "buy", ArrayList(), HashMap(), virtualFileUrlManager.getOrCreateFromUri("file:///tmp"),
                                           SampleEntitySource("test"))
    assertNull(reference.resolve(mutableStorage1))
  }

  @Test
  @Disabled("Wrong entity reference behaviour")
  fun `wrong entity ref resolve in same storage`() {
    val mutableStorage = MutableEntityStorage.create()
    val sampleEntity = mutableStorage addEntity SampleEntity(false, "hello", ArrayList(), HashMap(),
                                                             virtualFileUrlManager.getOrCreateFromUri("file:///tmp"),
                                                             SampleEntitySource("test"))
    val reference = sampleEntity.createPointer<SampleEntity>()
    mutableStorage addEntity SampleEntity(false, "buy", ArrayList(), HashMap(), virtualFileUrlManager.getOrCreateFromUri("file:///tmp"),
                                          SampleEntitySource("test"))
    val toBuilder = mutableStorage.toSnapshot().toBuilder()
    val sampleEntity2 = toBuilder.entities(SampleEntity::class.java).first()
    toBuilder.removeEntity(sampleEntity2)
    val toBuilder1 = toBuilder.toSnapshot().toBuilder()
    val sampleEntity1 = toBuilder1 addEntity SampleEntity(false, "own", ArrayList(), HashMap(),
                                                          virtualFileUrlManager.getOrCreateFromUri("file:///tmp"), SampleEntitySource("test"))
    val resolve = reference.resolve(toBuilder1)
    assertEquals(sampleEntity.stringProperty, resolve!!.stringProperty)
  }
}