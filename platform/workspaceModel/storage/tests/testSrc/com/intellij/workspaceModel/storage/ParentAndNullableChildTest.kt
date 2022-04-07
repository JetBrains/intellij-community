// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage

import com.intellij.workspaceModel.storage.entities.test.api.ChildNullableEntity
import com.intellij.workspaceModel.storage.entities.test.api.MySource
import com.intellij.workspaceModel.storage.entities.test.api.ParentNullableEntity
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ParentAndNullableChildTest {
  @Test
  fun `parent with child`() {
    val entity = ParentNullableEntity {
      parentData = "ParentData"
      child = ChildNullableEntity {
        childData = "ChildData"
      }
    }

    assertNotNull(entity.child)
    assertEquals("ChildData", entity.child?.childData)
  }

  @Test
  fun `parent with child in builder`() {
    val entity = ParentNullableEntity {
      entitySource = MySource
      parentData = "ParentData"
      child = ChildNullableEntity {
        entitySource = MySource
        childData = "ChildData"
      }
    }

    val builder = WorkspaceEntityStorageBuilder.create()
    builder.addEntity(entity)

    val single = builder.entities(ParentNullableEntity::class.java).single()
    val child = single.child
    assertNotNull(child)
    assertEquals("ChildData", child.childData)
  }

  @Test
  fun `parent with null child in builder`() {
    val entity = ParentNullableEntity {
      entitySource = MySource
      parentData = "ParentData"
      child = null
    }

    val builder = WorkspaceEntityStorageBuilder.create()
    builder.addEntity(entity)

    val single = builder.entities(ParentNullableEntity::class.java).single()
    assertNull(single.child)
  }

  @Test
  fun `parent with child in builder and accessing`() {
    val entity = ParentNullableEntity {
      entitySource = MySource
      parentData = "ParentData"
      child = ChildNullableEntity {
        entitySource = MySource
        childData = "ChildData"
      }
    }

    val builder = WorkspaceEntityStorageBuilder.create()
    builder.addEntity(entity)

    assertEquals("ChildData", entity.child?.childData)
  }

  @Test
  fun `parent with null child in builder and accessing`() {
    val entity = ParentNullableEntity {
      entitySource = MySource
      parentData = "ParentData"
      child = null
    }

    val builder = WorkspaceEntityStorageBuilder.create()
    builder.addEntity(entity)

    assertNull(entity.child)
  }

  @Test
  fun `get parent from the child`() {
    val entity = ParentNullableEntity {
      entitySource = MySource
      parentData = "ParentData"
      child = ChildNullableEntity {
        entitySource = MySource
        childData = "data"
      }
    }

    val builder = WorkspaceEntityStorageBuilder.create()
    builder.addEntity(entity)

    val single = builder.entities(ChildNullableEntity::class.java).single()
    assertEquals("ParentData", single.parentEntity.parentData)
  }

  @Test
  fun `parent and child refs`() {
    val entity = ParentNullableEntity {
      entitySource = MySource
      parentData = "ParentData"
      child = ChildNullableEntity {
        entitySource = MySource
        childData = "data"
      }
    }

    assertNotNull(entity.child)
    assertEquals("ParentData", entity.child!!.parentEntity.parentData)
  }

  @Test
  fun `parent in store and child`() {
    val entity = ParentNullableEntity {
      entitySource = MySource
      parentData = "ParentData"
    }
    val builder = createEmptyBuilder()
    builder.addEntity(entity)

    val child = ChildNullableEntity {
      entitySource = MySource
      childData = "data"
      parentEntity = entity
    }

    assertNotNull(entity.child)
    assertEquals("ParentData", child.parentEntity.parentData)
    assertTrue(builder.entities(ChildNullableEntity::class.java).toList().isEmpty())
    assertNull(builder.entities(ParentNullableEntity::class.java).single().child)
  }

  @Test
  fun `parent in store and child add to store`() {
    val entity = ParentNullableEntity {
      entitySource = MySource
      parentData = "ParentData"
    }
    val builder = createEmptyBuilder()
    builder.addEntity(entity)

    val child = ChildNullableEntity {
      entitySource = MySource
      childData = "data"
      parentEntity = entity
    }
    builder.addEntity(child)

    assertNotNull(entity.child)
    assertEquals("ParentData", child.parentEntity.parentData)
    assertNotNull(builder.entities(ChildNullableEntity::class.java).toList().singleOrNull())
    assertNotNull(builder.entities(ParentNullableEntity::class.java).single().child)
  }
}