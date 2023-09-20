// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests

import com.intellij.platform.workspace.storage.testEntities.entities.MySource
import com.intellij.platform.workspace.storage.testEntities.entities.UnknownFieldEntity
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test
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