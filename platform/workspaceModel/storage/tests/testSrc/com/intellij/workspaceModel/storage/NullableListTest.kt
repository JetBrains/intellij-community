// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage

import com.intellij.workspaceModel.storage.entities.test.api.MySource
import com.intellij.workspaceModel.storage.entities.test.api.OptionalListIntEntity
import org.junit.jupiter.api.Test

class NullableListTest {
  @Test
  fun `nullable list`() {
    val builder = createEmptyBuilder()
    builder.addEntity(OptionalListIntEntity {
      entitySource = MySource
      //data = null
    })
  }
}