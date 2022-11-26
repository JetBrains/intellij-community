// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage

import com.intellij.workspaceModel.storage.entities.test.addSampleEntity
import com.intellij.workspaceModel.storage.entities.test.api.SampleEntity
import com.intellij.workspaceModel.storage.impl.MutableEntityStorageImpl
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class EntityReferenceTest {
  @Test
  fun basic() {
    val builder = createEmptyBuilder()
    val entity = builder.addSampleEntity("data")
    val reference = entity.createReference<SampleEntity>()
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
    val entity = builder.addSampleEntity("data")
    val entity2 = builder.addSampleEntity("data")
    val reference1 = entity.createReference<SampleEntity>()
    val reference2 = entity.createReference<SampleEntity>()
    val reference3 = entity2.createReference<SampleEntity>()
    assertEquals(reference1, reference2)
    assertNotEquals(reference1, reference3)
  }

  @Test
  fun `replace entity by equal`() {
    val builder = createEmptyBuilder()
    builder.addSampleEntity("foo")
    val snapshot = builder.toSnapshot()
    val entity = snapshot.singleSampleEntity()
    val reference = entity.createReference<SampleEntity>()
    val newBuilder = createBuilderFrom(snapshot)
    newBuilder.removeEntity(entity)
    newBuilder.addSampleEntity("foo")
    val changes = newBuilder.collectChanges(snapshot)
    //if there is an event about the change, the code which stores EntityReference is supposed to update it
    if (changes.isEmpty()) {
      val updated = newBuilder.toSnapshot()
      assertEquals(updated.singleSampleEntity(), reference.resolve(updated))
    }
  }

  @Test
  @Disabled("Wrong entity reference behaviour")
  fun `wrong entity ref resolve in different storage`() {
    val mutableStorage = MutableEntityStorageImpl.create()
    val sampleEntity = mutableStorage.addSampleEntity("hello")
    val reference = sampleEntity.createReference<SampleEntity>()

    val mutableStorage1 = MutableEntityStorageImpl.create()
    mutableStorage1.addSampleEntity("buy")
    assertNull(reference.resolve(mutableStorage1))
  }

  @Test
  @Disabled("Wrong entity reference behaviour")
  fun `wrong entity ref resolve in same storage`() {
    val mutableStorage = MutableEntityStorageImpl.create()
    val sampleEntity = mutableStorage.addSampleEntity("hello")
    val reference = sampleEntity.createReference<SampleEntity>()
    mutableStorage.addSampleEntity("buy")
    val toBuilder = mutableStorage.toSnapshot().toBuilder()
    val sampleEntity2 = toBuilder.entities(SampleEntity::class.java).first()
    toBuilder.removeEntity(sampleEntity2)
    val toBuilder1 = toBuilder.toSnapshot().toBuilder()
    val sampleEntity1 = toBuilder1.addSampleEntity("own")
    val resolve = reference.resolve(toBuilder1)
    assertEquals(sampleEntity.stringProperty, resolve!!.stringProperty)
  }
}