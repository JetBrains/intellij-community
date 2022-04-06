// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage

import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.entities.api.MySource
import com.intellij.workspaceModel.storage.entities.api.SampleEntity2
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SampleTest {
  @Test
  fun `entity creation`() {
    val entity = SampleEntity2 {
      data = "myData"
    }
    assertEquals("myData", entity.data)
  }

  @Test
  fun `check entity initialized`() {
    val builder = WorkspaceEntityStorageBuilder.create()
    var entity = SampleEntity2 {
      entitySource = MySource
      boolData = true
    }
    try {
      builder.addEntity(entity)
    }
    catch (e: IllegalStateException) {
      assertEquals("Field SampleEntity2#data should be initialized", e.message)
    }

    entity = SampleEntity2 {
      data = "TestData"
      boolData = true
    }
    try {
      builder.addEntity(entity)
    }
    catch (e: Throwable) {
      assertTrue("entitySource" in e.message!!)
      assertTrue("initialized" in e.message!!)
    }
  }
}
