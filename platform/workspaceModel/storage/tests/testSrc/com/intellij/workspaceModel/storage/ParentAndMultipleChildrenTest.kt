// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage

import com.intellij.workspaceModel.storage.entities.test.api.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ParentAndMultipleChildrenTest {
  @Test
  fun `parent with multiple children`() {
    val entity = ParentMultipleEntity("ParentData", MySource) {
      children = listOf(ChildMultipleEntity("ChildData", MySource))
    }

    assertTrue(entity.children.isNotEmpty())
    assertEquals("ChildData", entity.children.single().childData)
  }

  @Test
  fun `parent with multiple children in builder`() {
    val entity = ParentMultipleEntity("ParentData", MySource) {
      children = listOf(ChildMultipleEntity("ChildData", MySource))
    }

    val builder = MutableEntityStorage.create()
    builder.addEntity(entity)

    val parent = builder.entities(ParentMultipleEntity::class.java).single()
    assertEquals("ParentData", parent.parentData)
    val child = parent.children.single()
    assertEquals("ChildData", child.childData)
  }

  @Test
  fun `parent with three children in builder`() {
    val entity = ParentMultipleEntity("ParentData", MySource) {
      children = listOf(
        ChildMultipleEntity("ChildData1", MySource),
        ChildMultipleEntity("ChildData2", MySource),
      )
    }

    val builder = MutableEntityStorage.create()
    builder.addEntity(entity)

    val parent = builder.entities(ParentMultipleEntity::class.java).single()
    assertEquals("ParentData", parent.parentData)
    assertEquals(2, parent.children.size)
    val child = parent.children.first()
    assertEquals("ChildData1", child.childData)
    val child2 = parent.children.last()
    assertEquals("ChildData2", child2.childData)
  }

  @Test
  fun `parent with multiple children in builder and accessing`() {
    val entity = ParentMultipleEntity("ParentData", MySource) {
      children = listOf(
        ChildMultipleEntity("ChildData1", MySource),
      )
    }

    val builder = MutableEntityStorage.create()
    builder.addEntity(entity)

    assertEquals("ChildData1", entity.children.single().childData)
  }

  @Test
  fun `get parent from the child`() {
    val entity = ParentMultipleEntity("ParentData", MySource) {
      children = listOf(
        ChildMultipleEntity("ChildData1", MySource),
      )
    }

    val builder = MutableEntityStorage.create()
    builder.addEntity(entity)

    val child = builder.entities(ChildMultipleEntity::class.java).single()
    assertEquals("ParentData", child.parentEntity.parentData)
  }

  @Test
  fun `add parent and then child`() {
    val builder = MutableEntityStorage.create()
    val parent = ParentMultipleEntity("ParentData", MySource) {
      children = emptyList()
    }
    builder.addEntity(parent)

    val child = ChildMultipleEntity("data", MySource) {
      this.parentEntity = parent
    }
    builder.addEntity(child)

    assertEquals("data", builder.entities(ParentMultipleEntity::class.java).single().children.single().childData)
  }

  @Test
  fun `add parent and then child 2`() {
    val builder = MutableEntityStorage.create()
    val parent = ParentMultipleEntity("Parent", MySource) {
      children = emptyList()
    }
    builder.addEntity(parent)

    val child = ChildMultipleEntity("data", MySource) {
      this.parentEntity = parent
    }

    assertEquals("Parent", child.parentEntity.children.single().parentEntity.parentData)
    assertEquals(1, parent.children.size)
    assertEquals(1, builder.entities(ParentMultipleEntity::class.java).toList().size)
    assertEquals(0, builder.entities(ParentMultipleEntity::class.java).single().children.size)
    assertEquals(0, builder.entities(ChildMultipleEntity::class.java).toList().size)
  }

  @Test
  fun `add parent and then child 3`() {
    val builder = MutableEntityStorage.create()
    val parent = ParentMultipleEntity("Parent", MySource) {
      children = listOf(ChildMultipleEntity("data1", MySource))
    }
    builder.addEntity(parent)

    val child = ChildMultipleEntity("data", MySource) {
      this.parentEntity = parent
    }

    assertEquals("Parent", child.parentEntity.children.first().parentEntity.parentData)
    assertEquals(2, child.parentEntity.children.size)
    assertEquals(2, parent.children.size)
    assertEquals(1, builder.entities(ParentMultipleEntity::class.java).toList().size)
    assertEquals(1, builder.entities(ParentMultipleEntity::class.java).single().children.size)
    assertEquals(1, builder.entities(ChildMultipleEntity::class.java).toList().size)
  }

  @Test
  fun `add parent and then child 4`() {
    val parent = ParentMultipleEntity("Parent", MySource) {
      children = listOf(ChildMultipleEntity("data1", MySource))
    }

    val child = ChildMultipleEntity("data", MySource) {
      this.parentEntity = parent
    }

    assertEquals("Parent", child.parentEntity.children.first().parentEntity.parentData)
    assertEquals(2, child.parentEntity.children.size)
    assertEquals(2, parent.children.size)
  }

  @Test
  fun `add parent and then child 5`() {
    val parent = ParentMultipleEntity("Parent", MySource) {
      children = listOf()
    }
    val builder = MutableEntityStorage.create()
    builder.addEntity(parent)

    val child = ChildMultipleEntity("data", MySource) {
      this.parentEntity = parent
    }

    assertEquals("Parent", child.parentEntity.children.first().parentEntity.parentData)
    assertEquals(1, child.parentEntity.children.size)
    assertEquals(1, parent.children.size)
  }

  @Test
  fun `add parent and then child 6`() {
    val parent = ParentMultipleEntity("Parent", MySource) {
      children = listOf()
    }
    val builder = MutableEntityStorage.create()
    builder.addEntity(parent)

    val child = ChildMultipleEntity("data", MySource) {
      this.parentEntity = parent
    }

    assertEquals("Parent", child.parentEntity.children.first().parentEntity.parentData)
    assertEquals(1, child.parentEntity.children.size)
    assertEquals(1, parent.children.size)
  }

  @Test
  fun `adding entity via modify`() {
    val target = createEmptyBuilder()
    target addEntity ParentMultipleEntity("Parent", MySource)
    val source = createBuilderFrom(target)
    val parentToModify = source.toSnapshot().entities(ParentMultipleEntity::class.java).single()
    source.modifyEntity(parentToModify) {
      this.children = listOf(
        ChildMultipleEntity("child1", MySource),
        ChildMultipleEntity("child2", MySource),
      )
    }
  }

  @Test
  fun `adding entity via modify 1`() {
    val target = createEmptyBuilder()
    target addEntity ParentMultipleEntity("Parent", MySource)
    val source = createBuilderFrom(target)
    val parentToModify = source.toSnapshot().entities(ParentMultipleEntity::class.java).single()
    source.modifyEntity(parentToModify) {
      this.children = listOf(
        ChildMultipleEntity("child1", MySource),
        ChildMultipleEntity("child2", MySource),
      )
    }
  }

  @Test
  fun `adding entity via modify 2`() {
    val target = createEmptyBuilder()
    target addEntity XChildWithOptionalParentEntity("Data", MySource)

    val source = createBuilderFrom(target)
    val childToModify = source.toSnapshot().entities(XChildWithOptionalParentEntity::class.java).single()
    source.modifyEntity(childToModify) {
      this.optionalParent = XParentEntity("parent", MySource)
    }
  }
}