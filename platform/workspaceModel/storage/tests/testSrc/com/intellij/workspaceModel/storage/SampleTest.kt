// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage

import com.intellij.workspaceModel.storage.entities.test.api.MySource
import com.intellij.workspaceModel.storage.entities.test.api.SampleEntity2
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SampleTest {
  @Test
  fun `entity creation`() {
    val entity = SampleEntity2("myData", true, MySource)
    assertEquals("myData", entity.data)
  }

  @Test
  fun `optional field`() {
    val entity = SampleEntity2("", true, MySource){
      this.optionalData = null
    }
    val builder = MutableEntityStorage.create()
    builder.addEntity(entity)
    Assertions.assertNull(entity.optionalData)
  }
}
