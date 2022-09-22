// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage

import com.intellij.workspaceModel.storage.entities.test.api.MySource
import com.intellij.workspaceModel.storage.entities.test.api.SampleEntity2
import com.intellij.workspaceModel.storage.entities.test.api.modifyEntity
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class SampleTest {
  @Test
  fun `entity creation`() {
    val entity = SampleEntity2("myData", true, MySource)
    assertEquals("myData", entity.data)
  }

  @Test
  fun `optional field`() {
    val entity = SampleEntity2("", true, MySource) {
      this.optionalData = null
    }
    val builder = MutableEntityStorage.create()
    builder.addEntity(entity)
    Assertions.assertNull(entity.optionalData)
  }

  /**
   * When we turn builder to snapshot, we "lock" all the entity data to write, so we need to recreate entity data in order to write to it.
   * In previous implementation we stored entity data in entity builder, so it didn't modify in such way.
   */
  @Test
  fun `modify entity in builder that is turned to snapshot`() {
    val builder = MutableEntityStorage.create()
    val entity = SampleEntity2("data", true, MySource)
    builder.addEntity(entity)

    builder.toSnapshot()
    builder.modifyEntity(entity) {
      this.data = "data2"
    }

    assertEquals("data2", entity.data)
    assertEquals("data2", builder.entities(SampleEntity2::class.java).single().data)
  }

  @Test
  fun `add and remove from builder`() {
    val builder = MutableEntityStorage.create()
    val entity = SampleEntity2("data", true, MySource)
    builder.addEntity(entity)
    builder.removeEntity(entity)

    assertThrows<IllegalStateException> { entity.data }
  }
}
