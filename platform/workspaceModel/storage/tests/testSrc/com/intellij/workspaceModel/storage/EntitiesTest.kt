// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage

import com.intellij.workspaceModel.storage.entities.AnotherSource
import com.intellij.workspaceModel.storage.entities.MySource
import com.intellij.workspaceModel.storage.entities.addSampleEntity
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityStorageBuilderImpl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test

class EntitiesTest {

  lateinit var builder: WorkspaceEntityStorageBuilder

  @Before
  fun setUp() {
    builder = createEmptyBuilder()
  }

  @Test
  fun `equal data is equal`() {
    val first = builder.addSampleEntity("One")
    val second = builder.addSampleEntity("One")

    val firstData = (builder as WorkspaceEntityStorageBuilderImpl).entityDataByIdOrDie(first.id)
    val secondData = (builder as WorkspaceEntityStorageBuilderImpl).entityDataByIdOrDie(second.id)

    assertEquals(firstData, secondData)
  }

  @Test
  fun `data with different properties`() {
    val first = builder.addSampleEntity("One")
    val second = builder.addSampleEntity("Two")

    val firstData = (builder as WorkspaceEntityStorageBuilderImpl).entityDataByIdOrDie(first.id)
    val secondData = (builder as WorkspaceEntityStorageBuilderImpl).entityDataByIdOrDie(second.id)

    assertNotEquals(firstData, secondData)
  }

  @Test
  fun `data with different sources`() {
    val first = builder.addSampleEntity("One", source = MySource)
    val second = builder.addSampleEntity("One", source = AnotherSource)

    val firstData = (builder as WorkspaceEntityStorageBuilderImpl).entityDataByIdOrDie(first.id)
    val secondData = (builder as WorkspaceEntityStorageBuilderImpl).entityDataByIdOrDie(second.id)

    assertNotEquals(firstData, secondData)
  }
}