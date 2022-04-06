// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.workspaceModel.storage

import com.intellij.workspaceModel.storage.entities.api.ChildSingleFirstEntity
import com.intellij.workspaceModel.storage.entities.api.MySource
import com.intellij.workspaceModel.storage.entities.api.ParentSingleAbEntity
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SingleAbstractTest {
  @Test
  fun `parent with child`() {
    val entity = ParentSingleAbEntity {
      child = ChildSingleFirstEntity {
        firstData = "ChildData"
      }
    }

    assertEquals("ChildData", (entity.child as ChildSingleFirstEntity).firstData)
  }

  @Test
  fun `parent with child and common data`() {
    val entity = ParentSingleAbEntity {
      child = ChildSingleFirstEntity {
        commonData = "ChildData"
      }
    }

    assertEquals("ChildData", (entity.child as ChildSingleFirstEntity).commonData)
  }

  @Test
  fun `parent with multiple child in builder`() {
    val entity = ParentSingleAbEntity {
      entitySource = MySource
      child = ChildSingleFirstEntity {
        entitySource = MySource
        commonData = "ChildData"
        firstData = "Data"
      }
    }

    val builder = WorkspaceEntityStorageBuilder.create()
    builder.addEntity(entity)

    val single = builder.entities(ParentSingleAbEntity::class.java).single()
    assertEquals("Data", (single.child as ChildSingleFirstEntity).firstData)
  }

  @Test
  fun `parent with three child in builder`() {
    val entity = ParentSingleAbEntity {
      entitySource = MySource
      child = ChildSingleFirstEntity {
        entitySource = MySource
        commonData = "Data"
        firstData = "ChildData1"
      }
    }

    val builder = WorkspaceEntityStorageBuilder.create()
    builder.addEntity(entity)

    val single = builder.entities(ParentSingleAbEntity::class.java).single()
    assertEquals("ChildData1", (single.child as ChildSingleFirstEntity).firstData)
  }

  @Test
  fun `parent with multiple child in builder and accessing`() {
    val entity = ParentSingleAbEntity {
      entitySource = MySource
      child = ChildSingleFirstEntity {
        entitySource = MySource
        firstData = "FirstData"
        commonData = "ChildData1"
      }
    }

    val builder = WorkspaceEntityStorageBuilder.create()
    builder.addEntity(entity)

    assertEquals("ChildData1", entity.child.commonData)
  }

  @Test
  fun `get parent from the child`() {
    val entity = ParentSingleAbEntity {
      entitySource = MySource
      child = ChildSingleFirstEntity {
        entitySource = MySource
        commonData = "Data"
        firstData = "ChildData1"
      }
    }

    val builder = WorkspaceEntityStorageBuilder.create()
    builder.addEntity(entity)

    val single = builder.entities(ChildSingleFirstEntity::class.java).single()
    assertEquals("ChildData1", (single.parentEntity.child as ChildSingleFirstEntity).firstData)
  }
}
