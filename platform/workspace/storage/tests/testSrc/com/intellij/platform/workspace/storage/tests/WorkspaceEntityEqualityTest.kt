// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests

import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.impl.url.VirtualFileUrlManagerImpl
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.testEntities.entities.SampleEntity
import com.intellij.platform.workspace.storage.testEntities.entities.SampleEntitySource
import com.intellij.platform.workspace.storage.testEntities.entities.modifyEntity
import com.intellij.platform.workspace.storage.toBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import kotlin.test.*

@OptIn(EntityStorageInstrumentationApi::class)
class WorkspaceEntityEqualityTest {

  private lateinit var builderOne: MutableEntityStorageInstrumentation
  private lateinit var builderTwo: MutableEntityStorageInstrumentation

  @BeforeEach
  fun setUp() {
    builderOne = MutableEntityStorage.create() as MutableEntityStorageInstrumentation
    builderTwo = MutableEntityStorage.create() as MutableEntityStorageInstrumentation
  }

  @Test
  fun `equality from different stores`() {
    val entityOne = builderOne addEntity SampleEntity(false, "Data", ArrayList(), HashMap(),
                                                      VirtualFileUrlManagerImpl().fromUrl("file:///tmp"), SampleEntitySource("test"))
    val entityTwo = builderTwo addEntity SampleEntity(false, "Data", ArrayList(), HashMap(),
                                                      VirtualFileUrlManagerImpl().fromUrl("file:///tmp"), SampleEntitySource("test"))

    assertNotEquals(entityOne, entityTwo)
  }

  @Test
  fun `equality modified entity in builder`() {
    val entityOne = builderOne addEntity SampleEntity(false, "Data", ArrayList(), HashMap(),
                                                      VirtualFileUrlManagerImpl().fromUrl("file:///tmp"), SampleEntitySource("test"))
    val entityTwo = builderOne.modifyEntity(entityOne) {
      stringProperty = "AnotherData"
    }

    assertEquals(entityOne, entityTwo)
    assertEquals(entityTwo, entityOne)
  }

  @Test
  fun `equality modified entity`() {
    builderOne addEntity SampleEntity(false, "Data", ArrayList(), HashMap(), VirtualFileUrlManagerImpl().fromUrl("file:///tmp"),
                                      SampleEntitySource("test"))
    val storage = builderOne.toSnapshot()
    val entityOne = storage.entities(SampleEntity::class.java).single()
    val builder = storage.toBuilder()
    builder.modifyEntity(entityOne) {
      stringProperty = "AnotherData"
    }
    val entityTwo = builder.toSnapshot().entities(SampleEntity::class.java).single()

    assertNotEquals(entityOne, entityTwo)
  }

  @Test
  fun `equality modified another entity`() {
    builderOne addEntity SampleEntity(false, "Data1", ArrayList(), HashMap(), VirtualFileUrlManagerImpl().fromUrl("file:///tmp"),
                                      SampleEntitySource("test"))
    builderOne addEntity SampleEntity(false, "Data2", ArrayList(), HashMap(), VirtualFileUrlManagerImpl().fromUrl("file:///tmp"),
                                      SampleEntitySource("test"))
    val storage = builderOne.toSnapshot()
    val entityOne = storage.entities(SampleEntity::class.java).single { it.stringProperty == "Data1" }
    val entityForModification = storage.entities(SampleEntity::class.java).single { it.stringProperty == "Data2" }
    val builder = storage.toBuilder()
    builder.modifyEntity(entityForModification) {
      stringProperty = "AnotherData"
    }
    val entityTwo = builder.toSnapshot().entities(SampleEntity::class.java).single { it.stringProperty == "Data1" }


    assertEquals(entityOne, entityTwo)
  }

  @Test
  fun `equality in set`() {
    builderOne addEntity SampleEntity(false, "Data1", ArrayList(), HashMap(), VirtualFileUrlManagerImpl().fromUrl("file:///tmp"),
                                      SampleEntitySource("test"))
    builderOne addEntity SampleEntity(false, "Data2", ArrayList(), HashMap(), VirtualFileUrlManagerImpl().fromUrl("file:///tmp"),
                                      SampleEntitySource("test"))

    val checkSet = HashSet<SampleEntity>()

    var storage = builderOne.toSnapshot()
    val entityOne = storage.entities(SampleEntity::class.java).single { it.stringProperty == "Data1" }
    checkSet += entityOne

    var entityForModification = storage.entities(SampleEntity::class.java).single { it.stringProperty == "Data2" }
    var builder = storage.toBuilder()
    builder.modifyEntity(entityForModification) {
      stringProperty = "AnotherData"
    }
    storage = builder.toSnapshot()
    val entityTwo = storage.entities(SampleEntity::class.java).single { it.stringProperty == "Data1" }

    assertTrue(entityTwo in checkSet)

    entityForModification = storage.entities(SampleEntity::class.java).single { it.stringProperty == "Data1" }
    builder = storage.toBuilder()
    builder.modifyEntity(entityForModification) {
      stringProperty = "AnotherData2"
    }
    val entityThree = builder.toSnapshot().entities(SampleEntity::class.java).single { it.stringProperty == "AnotherData2" }

    assertFalse(entityThree in checkSet)
  }

  @Disabled
  @Test
  fun `equality for entity from event and from updated snapshot after dummy modification`() {
    builderOne addEntity SampleEntity(false, "Data", ArrayList(), HashMap(), VirtualFileUrlManagerImpl().fromUrl("file:///tmp"),
                                      SampleEntitySource("test"))
    builderTwo.applyChangesFrom(builderOne)
    val entityInEvent = builderTwo.collectChanges()[SampleEntity::class.java]!!.single().newEntity!!
    val snapshot = builderTwo.toSnapshot()
    val entityInSnapshot = snapshot.singleSampleEntity()
    assertEquals(entityInEvent, entityInSnapshot)
    assertEquals(entityInSnapshot, entityInEvent)

    val newBuilder = MutableEntityStorage.from(snapshot) as MutableEntityStorageInstrumentation
    newBuilder.modifyEntity(entityInSnapshot) {
      stringProperty = "Data"
    }
    //no events will be fired because nothing was changed
    assertEquals(emptySet<Map.Entry<*, *>>(), newBuilder.collectChanges().entries)

    val newSnapshot = newBuilder.toSnapshot()
    assertEquals(entityInEvent, newSnapshot.singleSampleEntity())
  }

  @Test
  fun `cache for requests works`() {
    builderOne addEntity SampleEntity(false, "Data", ArrayList(), HashMap(), VirtualFileUrlManagerImpl().fromUrl("file:///tmp"),
                                      SampleEntitySource("test"))
    val snapshot = builderOne.toSnapshot()

    assertSame(snapshot.entities(SampleEntity::class.java).single(), snapshot.entities(SampleEntity::class.java).single())
  }
}