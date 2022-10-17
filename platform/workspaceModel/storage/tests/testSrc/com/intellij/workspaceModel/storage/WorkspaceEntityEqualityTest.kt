// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage

import com.intellij.workspaceModel.storage.entities.test.addSampleEntity
import com.intellij.workspaceModel.storage.entities.test.api.SampleEntity
import com.intellij.workspaceModel.storage.entities.test.api.modifyEntity
import org.junit.Assert.*
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

class WorkspaceEntityEqualityTest {

  private lateinit var builderOne: MutableEntityStorage
  private lateinit var builderTwo: MutableEntityStorage

  @Before
  fun setUp() {
    builderOne = MutableEntityStorage.create()
    builderTwo = MutableEntityStorage.create()
  }

  @Test
  fun `equality from different stores`() {
    val entityOne = builderOne.addSampleEntity("Data")
    val entityTwo = builderTwo.addSampleEntity("Data")

    assertNotEquals(entityOne, entityTwo)
  }

  @Test
  fun `equality modified entity in builder`() {
    val entityOne = builderOne.addSampleEntity("Data")
    val entityTwo = builderOne.modifyEntity(entityOne) {
      stringProperty = "AnotherData"
    }

    assertEquals(entityOne, entityTwo)
    assertEquals(entityTwo, entityOne)
  }

  @Test
  fun `equality modified entity`() {
    builderOne.addSampleEntity("Data")
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
    builderOne.addSampleEntity("Data1")
    builderOne.addSampleEntity("Data2")
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
    builderOne.addSampleEntity("Data1")
    builderOne.addSampleEntity("Data2")

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

  @Ignore
  @Test
  fun `equality for entity from event and from updated snapshot after dummy modification`() {
    builderOne.addSampleEntity("Data")
    builderTwo.addDiff(builderOne)
    val entityInEvent = builderTwo.collectChanges(EntityStorageSnapshot.empty())[SampleEntity::class.java]!!.single().newEntity!!
    val snapshot = builderTwo.toSnapshot()
    val entityInSnapshot = snapshot.singleSampleEntity()
    assertEquals(entityInEvent, entityInSnapshot)
    assertEquals(entityInSnapshot, entityInEvent)

    val newBuilder = MutableEntityStorage.from(snapshot)
    newBuilder.modifyEntity(entityInSnapshot) {
      stringProperty = "Data"
    }
    //no events will be fired because nothing was changed
    assertEquals(emptySet<Map.Entry<*,*>>(), newBuilder.collectChanges(snapshot).entries)
    
    val newSnapshot = newBuilder.toSnapshot()
    assertEquals(entityInEvent, newSnapshot.singleSampleEntity())
  }

  @Test
  fun `cache for requests works`() {
    builderOne.addSampleEntity("Data")
    val snapshot = builderOne.toSnapshot()

    assertSame(snapshot.entities(SampleEntity::class.java).single(), snapshot.entities(SampleEntity::class.java).single())
  }
}