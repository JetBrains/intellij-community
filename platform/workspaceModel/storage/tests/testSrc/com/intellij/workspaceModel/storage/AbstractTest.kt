// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage

import com.intellij.workspaceModel.storage.entities.test.api.ChildFirstEntity
import com.intellij.workspaceModel.storage.entities.test.api.ChildSecondEntity
import com.intellij.workspaceModel.storage.entities.test.api.MySource
import com.intellij.workspaceModel.storage.entities.test.api.ParentAbEntity
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.junit.Test
import org.junit.jupiter.api.assertDoesNotThrow

class AbstractTest {
  @Test
  fun `parent with child`() {
    val entity = ParentAbEntity {
      children = listOf(ChildFirstEntity {
        firstData = "ChildData"
      })
    }

    assertTrue(entity.children.isNotEmpty())
    assertEquals("ChildData", (entity.children.single() as ChildFirstEntity).firstData)
  }

  @Test
  fun `parent with child and common data`() {
    val entity = ParentAbEntity {
      children = listOf(ChildFirstEntity {
        commonData = "ChildData"
      })
    }

    assertTrue(entity.children.isNotEmpty())
    assertEquals("ChildData", (entity.children.single() as ChildFirstEntity).commonData)
  }

  @Test
  fun `parent with child multiple types`() {
    val entity = ParentAbEntity {
      children = listOf(
        ChildFirstEntity {
          commonData = "ChildData"
        },
        ChildSecondEntity {
          secondData = "ChildData"
        },
      )
    }

    assertEquals(2, entity.children.size)
  }

  @Test
  fun `parent with multiple children in builder`() {
    val entity = ParentAbEntity {
      entitySource = MySource
      children = listOf(ChildFirstEntity {
        entitySource = MySource
        commonData = "ChildData"
        firstData = "Data"
      })
    }

    val builder = WorkspaceEntityStorageBuilder.create()
    builder.addEntity(entity)

    val parent = builder.entities(ParentAbEntity::class.java).single()
    val child = parent.children.single()
    assertEquals("ChildData", child.commonData)
  }

  @Test
  fun `parent with three children in builder`() {
    val entity = ParentAbEntity {
      entitySource = MySource
      children = listOf(
        ChildFirstEntity {
          entitySource = MySource
          commonData = "Data"
          firstData = "ChildData1"
        },
        ChildSecondEntity {
          entitySource = MySource
          commonData = "Data"
          secondData = "ChildData2"
        },
      )
    }

    val builder = WorkspaceEntityStorageBuilder.create()
    builder.addEntity(entity)

    val parent = builder.entities(ParentAbEntity::class.java).single()
    assertEquals(2, parent.children.size)
    val child = parent.children.first()
    assertEquals("ChildData1", (child as ChildFirstEntity).firstData)
    val child2 = parent.children.last()
    assertEquals("ChildData2", (child2 as ChildSecondEntity).secondData)
  }

  @Test
  fun `parent with multiple children in builder and accessing`() {
    val entity = ParentAbEntity {
      entitySource = MySource
      children = listOf(
        ChildFirstEntity {
          entitySource = MySource
          firstData = "FirstData"
          commonData = "ChildData1"
        },
      )
    }

    val builder = WorkspaceEntityStorageBuilder.create()
    builder.addEntity(entity)

    assertEquals("ChildData1", entity.children.single().commonData)
  }

  @Test
  fun `get parent by child`() {
    val entity = ParentAbEntity {
      entitySource = MySource
      children = listOf(
        ChildFirstEntity {
          entitySource = MySource
          commonData = "Data"
          firstData = "ChildData1"
        },
      )
    }

    val builder = WorkspaceEntityStorageBuilder.create()
    builder.addEntity(entity)

    val child = builder.entities(ChildFirstEntity::class.java).single()
    assertEquals("Data", child.parentEntity.children.single().commonData)
  }

  @Test
  fun `add parent and then child`() {
    val builder = WorkspaceEntityStorageBuilder.create()
    val parent = ParentAbEntity {
      entitySource = MySource
      children = emptyList()
    }
    builder.addEntity(parent)

    val child = ChildFirstEntity {
      entitySource = MySource
      commonData = "data"
      firstData = "data"
      this.parentEntity = parent
    }
    builder.addEntity(child)

    assertEquals("data", builder.entities(ParentAbEntity::class.java).single().children.single().commonData)
  }

  @Test
  fun `add parent and then child 2`() {
    val builder = WorkspaceEntityStorageBuilder.create()
    val parent = ParentAbEntity {
      entitySource = MySource
      children = emptyList()
    }
    builder.addEntity(parent)

    val child = ChildFirstEntity {
      entitySource = MySource
      commonData = "data"
      firstData = "data"
      this.parentEntity = parent
    }

    assertDoesNotThrow { child.parentEntity.children.first().parentEntity }
    assertEquals(1, parent.children.size)
    assertEquals(1, builder.entities(ParentAbEntity::class.java).toList().size)
    assertEquals(0, builder.entities(ParentAbEntity::class.java).single().children.size)
    assertEquals(0, builder.entities(ChildFirstEntity::class.java).toList().size)
  }

  @Test
  fun `add parent and then child 3`() {
    val builder = WorkspaceEntityStorageBuilder.create()
    val parent = ParentAbEntity {
      entitySource = MySource
      children = listOf(
        ChildFirstEntity {
          entitySource = MySource
          commonData = "Data"
          firstData = "ChildData1"
        },
      )
    }
    builder.addEntity(parent)

    val child = ChildFirstEntity {
      entitySource = MySource
      commonData = "data"
      firstData = "data"
      this.parentEntity = parent
    }

    assertDoesNotThrow { child.parentEntity.children.first().parentEntity }

    assertEquals(2, child.parentEntity.children.size)
    assertEquals(2, parent.children.size)
    assertEquals(1, builder.entities(ParentAbEntity::class.java).toList().size)
    assertEquals(1, builder.entities(ParentAbEntity::class.java).single().children.size)
    assertEquals(1, builder.entities(ChildFirstEntity::class.java).toList().size)
  }

  @Test
  fun `add parent and then child 4`() {
    val parent = ParentAbEntity {
      entitySource = MySource
      children = listOf(ChildFirstEntity {
        entitySource = MySource
        commonData = "data1"
        firstData = "data1"
      })
    }

    val child = ChildFirstEntity {
      entitySource = MySource
      commonData = "data2"
      firstData = "data2"
      this.parentEntity = parent
    }

    assertDoesNotThrow { child.parentEntity.children.first().parentEntity }
    assertEquals(2, child.parentEntity.children.size)
    assertEquals(2, parent.children.size)
  }
}
