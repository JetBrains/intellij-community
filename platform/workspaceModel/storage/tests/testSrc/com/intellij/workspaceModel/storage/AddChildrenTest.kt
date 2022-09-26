// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.workspaceModel.storage

import com.intellij.workspaceModel.storage.entities.test.api.*
import junit.framework.TestCase.*
import org.junit.Test

class AddChildrenTest {
  @Test
  fun `child added to the store at parent modification`() {
    val builder = MutableEntityStorage.create()
    val entity = ParentNullableEntity("ParentData", MySource)
    builder.addEntity(entity)

    builder.modifyEntity(entity) {
      this.child = ChildNullableEntity("ChildData", MySource)
    }
    assertNotNull(entity.child)
    val entities = builder.entities(ChildNullableEntity::class.java).single()
    assertEquals("ChildData", entity.child?.childData)
    assertEquals("ParentData", entities.parentEntity.parentData)
  }

  @Test
  fun `new child added to the store at list modification`() {
    val builder = MutableEntityStorage.create()
    val parentEntity = ParentMultipleEntity("ParentData", MySource)

    val firstChild = ChildMultipleEntity("ChildOneData", MySource) {
      this.parentEntity = parentEntity
    }
    builder.addEntity(parentEntity)

    val secondChild = ChildMultipleEntity("ChildTwoData", MySource) {
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
    val builder = MutableEntityStorage.create()
    val parentEntity = ParentMultipleEntity("ParentData", MySource)

    val firstChild = ChildMultipleEntity("ChildOneData", MySource) {
      this.parentEntity = parentEntity
    }
    val secondChild = ChildMultipleEntity("ChildTwoData", MySource) {
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
    val builder = MutableEntityStorage.create()
    val entity = ParentNullableEntity("ParentData", MySource) {
      child = ChildNullableEntity("ChildData", MySource)
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
    val builder = MutableEntityStorage.create()
    val commonChild = ChildNullableEntity("ChildDataTwo", MySource)
    val entity = ParentNullableEntity("ParentData", MySource) {
      child = commonChild
    }
    builder.addEntity(entity)

    val anotherParent = ParentNullableEntity("AnotherParentData", MySource) {
      child = ChildNullableEntity("ChildDataTwo", MySource)
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

  @Test
  fun `adding new entity via modifyEntity`() {
    val builder = createEmptyBuilder()
    val right = builder addEntity RightEntity(MySource)

    builder.modifyEntity(right) {
      this.children = listOf(MiddleEntity("prop", MySource))
    }
  }

  @Test
  fun `adding new entity via modifyEntity 2`() {
    val builder = createEmptyBuilder()
    val right = builder addEntity ParentAbEntity(MySource)

    builder.modifyEntity(right) {
      this.children = listOf(ChildSecondEntity("data", "Data", MySource))
    }
  }
}