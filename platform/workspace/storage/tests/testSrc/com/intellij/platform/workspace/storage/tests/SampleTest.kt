// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests

import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.testEntities.entities.MySource
import com.intellij.platform.workspace.storage.testEntities.entities.SampleEntity2
import com.intellij.platform.workspace.storage.testEntities.entities.modifySampleEntity2
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
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
    val entity = builder addEntity SampleEntity2("data", true, MySource)

    builder.toSnapshot()
    val updatedEntity = builder.modifySampleEntity2(entity) {
      this.data = "data2"
    }

    assertEquals("data", entity.data)
    assertEquals("data2", updatedEntity.data)
    assertEquals("data2", builder.entities(SampleEntity2::class.java).single().data)
  }
}
