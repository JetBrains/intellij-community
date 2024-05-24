// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.platform.workspace.storage.tests

import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.testEntities.entities.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AddChildrenTest {
  @Test
  fun `child added to the store at parent modification`() {
    val builder = MutableEntityStorage.create()
    val entity = builder.addEntity(ParentEntity("ParentData", MySource))

    builder.modifyParentEntity(entity) {
      this.child = ChildEntity("ChildData", MySource)
    }
    assertNotNull(entity.child)
    val entities = builder.entities(ChildEntity::class.java).single()
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
    val addedParentEntity = builder.addEntity(parentEntity)

    val secondChild = ChildMultipleEntity("ChildTwoData", MySource)

    builder.modifyParentMultipleEntity(addedParentEntity) {
      children = listOf(firstChild, secondChild)
    }
    val children = builder.entities(ChildMultipleEntity::class.java).toList()
    assertEquals(2, children.size)
    assertEquals(2, parentEntity.children.size)
    children.forEach { assertEquals(addedParentEntity, it.parentEntity) }
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
    val addedParentEntity = builder.addEntity(parentEntity)
    val childrenFromStore = builder.entities(ChildMultipleEntity::class.java).toList()
    assertEquals(2, childrenFromStore.size)

    builder.modifyParentMultipleEntity(addedParentEntity) {
      children = listOf(firstChild)
    }
    val existingChild = builder.entities(ChildMultipleEntity::class.java).single()
    assertEquals("ChildOneData", existingChild.childData)
  }

  @Test
  fun `remove child from store at parent modification`() {
    val builder = MutableEntityStorage.create()
    val entity = ParentEntity("ParentData", MySource) {
      child = ChildEntity("ChildData", MySource)
    }
    val addedEntity = builder.addEntity(entity)

    builder.modifyParentEntity(addedEntity) {
      child = null
    }
    assertNull(entity.child)
    assertTrue(builder.entities(ChildEntity::class.java).toList().isEmpty())
  }

  @Test
  fun `remove old child at parent entity update`() {
    val builder = MutableEntityStorage.create()
    val commonChild = ChildEntity("ChildDataTwo", MySource)
    val entity = ParentEntity("ParentData", MySource) {
      child = commonChild
    }
    val addedEntity = builder.addEntity(entity)

    val anotherParent = ParentEntity("AnotherParentData", MySource) {
      child = ChildEntity("ChildDataTwo", MySource)
    }
    val addedAnotherParent = builder.addEntity(anotherParent)
    val children = builder.entities(ChildEntity::class.java).toList()
    assertEquals(2, children.size)

    builder.modifyChildEntity(addedEntity.child!!) {
      parentEntity = anotherParent
    }
    assertNull(entity.child)
    val childFromStore = builder.entities(ChildEntity::class.java).single()
    assertEquals("ChildDataTwo", childFromStore.childData)
    assertEquals(addedAnotherParent, childFromStore.parentEntity)
  }

  @Test
  fun `adding new entity via modifyEntity`() {
    val builder = createEmptyBuilder()
    val right = builder addEntity RightEntity(MySource)

    builder.modifyRightEntity(right) {
      this.children = listOf(MiddleEntity("prop", MySource))
    }

    assertEquals(right, builder.entities(MiddleEntity::class.java).single().parentEntity)
  }

  @Test
  fun `adding new entity via modifyEntity 2`() {
    val builder = createEmptyBuilder()
    val right = builder addEntity ParentAbEntity(MySource)

    builder.modifyParentAbEntity(right) {
      this.children = listOf(ChildSecondEntity("data", "Data", MySource))
    }
  }
}
