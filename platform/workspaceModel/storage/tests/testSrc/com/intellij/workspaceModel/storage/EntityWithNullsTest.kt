// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage

import com.intellij.workspaceModel.storage.entities.test.api.MySource
import com.intellij.workspaceModel.storage.entities.test.api.OptionalIntEntity
import org.junit.jupiter.api.Test
import kotlin.test.assertNull

class EntityWithNullsTest {
  @Test
  fun `data int with null`() {
    val builder = createEmptyBuilder()
    builder.addEntity(OptionalIntEntity(MySource) {
      this.data = null
    })
    assertNull(builder.entities(OptionalIntEntity::class.java).single().data)
  }
}