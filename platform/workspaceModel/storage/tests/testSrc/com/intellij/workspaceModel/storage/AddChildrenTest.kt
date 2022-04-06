// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.workspaceModel.storage

import com.intellij.workspaceModel.storage.entities.api.*
import junit.framework.TestCase.*
import org.jetbrains.deft.IntellijWs.modifyEntity
import org.junit.Test

class AddChildrenTest {
  @Test
  fun `child added to the store at parent modification`() {
    val builder = WorkspaceEntityStorageBuilder.create()
    val entity = ParentNullableEntity {
      entitySource = MySource
      parentData = "ParentData"
    }
    builder.addEntity(entity)

    builder.modifyEntity(entity) {
      this.child = ChildNullableEntity {
        entitySource = com.intellij.workspaceModel.storage.entities.api.MySource
        childData = "ChildData"
      }
    }
    assertNotNull(entity.child)
    val entities = builder.entities(ChildNullableEntity::class.java).single()
    assertEquals("ChildData", entity.child?.childData)
    assertEquals("ParentData", entities.parentEntity.parentData)
  }

  @Test
  fun `new child added to the store at list modification`() {
    val builder = WorkspaceEntityStorageBuilder.create()
    val parentEntity = ParentMultipleEntity {
      entitySource = MySource
      parentData = "ParentData"
    }

    val firstChild = ChildMultipleEntity {
      this.entitySource = MySource
      this.childData = "ChildOneData"
      this.parentEntity = parentEntity
    }
    builder.addEntity(parentEntity)

    val secondChild = ChildMultipleEntity {
      this.entitySource = MySource
      this.childData = "ChildTwoData"
      this.parentEntity = parentEntity
    }

    builder.modifyEntity(parentEntity) {
      children = listOf(firstChild, secondChild)
    }
    val children = builder.entities(ChildMultipleEntity::class.java).toList()
    assertEquals(2, children.size)
    assertEquals(2, parentEntity.children.size)
    children.forEach { assertEquals(parentEntity, it.parentEntity) }
  }

  @Test
  fun `child was removed from the store after list update`() {
    val builder = WorkspaceEntityStorageBuilder.create()
    val parentEntity = ParentMultipleEntity {
      entitySource = MySource
      parentData = "ParentData"
    }

    val firstChild = ChildMultipleEntity {
      this.entitySource = MySource
      this.childData = "ChildOneData"
      this.parentEntity = parentEntity
    }
    val secondChild = ChildMultipleEntity {
      this.entitySource = MySource
      this.childData = "ChildTwoData"
      this.parentEntity = parentEntity
    }
    builder.addEntity(parentEntity)
    val childrenFromStore = builder.entities(ChildMultipleEntity::class.java).toList()
    assertEquals(2, childrenFromStore.size)

    builder.modifyEntity(parentEntity) {
      children = listOf(firstChild)
    }
    val existingChild = builder.entities(ChildMultipleEntity::class.java).single()
    assertEquals("ChildOneData", existingChild.childData)
  }

  @Test
  fun `remove child from store at parent modification`() {
    val builder = WorkspaceEntityStorageBuilder.create()
    val entity = ParentNullableEntity {
      entitySource = MySource
      parentData = "ParentData"
      child = ChildNullableEntity {
        entitySource = MySource
        childData = "ChildData"
      }
    }
    builder.addEntity(entity)

    builder.modifyEntity(entity) {
      child = null
    }
    assertNull(entity.child)
    assertTrue(builder.entities(ChildNullableEntity::class.java).toList().isEmpty())
  }

  @Test
  fun `remove old child at parent entity update`() {
    val builder = WorkspaceEntityStorageBuilder.create()
    val commonChild = ChildNullableEntity {
      entitySource = MySource
      childData = "ChildDataTwo"
    }
    val entity = ParentNullableEntity {
      entitySource = MySource
      parentData = "ParentData"
      child = commonChild
    }
    builder.addEntity(entity)

    val anotherParent = ParentNullableEntity {
      entitySource = MySource
      parentData = "AnotherParentData"
      child = ChildNullableEntity {
        entitySource = MySource
        childData = "ChildDataTwo"
      }
    }
    builder.addEntity(anotherParent)
    val children = builder.entities(ChildNullableEntity::class.java).toList()
    assertEquals(2, children.size)

    builder.modifyEntity(commonChild) {
      parentEntity = anotherParent
    }
    assertNull(entity.child)
    val childFromStore = builder.entities(ChildNullableEntity::class.java).single()
    assertEquals("ChildDataTwo", childFromStore.childData)
    assertEquals(anotherParent, childFromStore.parentEntity)
  }
}