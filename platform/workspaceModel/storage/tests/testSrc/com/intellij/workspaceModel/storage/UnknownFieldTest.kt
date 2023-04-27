// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage

import com.intellij.workspaceModel.storage.entities.test.api.MySource
import com.intellij.workspaceModel.storage.entities.test.api.UnknownFieldEntity
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals

class UnknownFieldTest {
  @Test
  fun `unknown date`() {
    val builder = createEmptyBuilder()
    builder.addEntity(UnknownFieldEntity(Date(0), MySource))

    val entityData = builder.entities(UnknownFieldEntity::class.java).single().data
    assertEquals(Date(0), entityData)
  }
}