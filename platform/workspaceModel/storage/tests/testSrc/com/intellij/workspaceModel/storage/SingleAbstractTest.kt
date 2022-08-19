// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.workspaceModel.storage

import com.intellij.workspaceModel.storage.entities.test.api.ChildSingleFirstEntity
import com.intellij.workspaceModel.storage.entities.test.api.MySource
import com.intellij.workspaceModel.storage.entities.test.api.ParentSingleAbEntity
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SingleAbstractTest {
  @Test
  fun `parent with child`() {
    val entity = ParentSingleAbEntity(MySource) {
      child = ChildSingleFirstEntity("", "ChildData", MySource)
    }

    assertEquals("ChildData", (entity.child as ChildSingleFirstEntity).firstData)
  }

  @Test
  fun `parent with child and common data`() {
    val entity = ParentSingleAbEntity(MySource) {
      child = ChildSingleFirstEntity("ChildData", "Data", MySource)
    }

    assertEquals("ChildData", (entity.child as ChildSingleFirstEntity).commonData)
  }

  @Test
  fun `parent with multiple child in builder`() {
    val entity = ParentSingleAbEntity(MySource) {
      child = ChildSingleFirstEntity("ChildData", "Data", MySource)
    }

    val builder = MutableEntityStorage.create()
    builder.addEntity(entity)

    val single = builder.entities(ParentSingleAbEntity::class.java).single()
    assertEquals("Data", (single.child as ChildSingleFirstEntity).firstData)
  }

  @Test
  fun `parent with three child in builder`() {
    val entity = ParentSingleAbEntity(MySource) {
      child = ChildSingleFirstEntity("Data", "ChildData1", MySource)
    }

    val builder = MutableEntityStorage.create()
    builder.addEntity(entity)

    val single = builder.entities(ParentSingleAbEntity::class.java).single()
    assertEquals("ChildData1", (single.child as ChildSingleFirstEntity).firstData)
  }

  @Test
  fun `parent with multiple child in builder and accessing`() {
    val entity = ParentSingleAbEntity(MySource) {
      child = ChildSingleFirstEntity("ChildData1", "FirstData", MySource)
    }

    val builder = MutableEntityStorage.create()
    builder.addEntity(entity)

    assertEquals("ChildData1", entity.child?.commonData)
  }

  @Test
  fun `get parent from the child`() {
    val entity = ParentSingleAbEntity(MySource) {
      child = ChildSingleFirstEntity("Data", "ChildData1", MySource)
    }

    val builder = MutableEntityStorage.create()
    builder.addEntity(entity)

    val single = builder.entities(ChildSingleFirstEntity::class.java).single()
    assertEquals("ChildData1", (single.parentEntity.child as ChildSingleFirstEntity).firstData)
  }
}
