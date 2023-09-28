// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests

import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.testEntities.entities.ChildFirstEntity
import com.intellij.platform.workspace.storage.testEntities.entities.ChildSecondEntity
import com.intellij.platform.workspace.storage.testEntities.entities.MySource
import com.intellij.platform.workspace.storage.testEntities.entities.ParentAbEntity
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AbstractTest {
  @Test
  fun `parent with child`() {
    val entity = ParentAbEntity(MySource) {
      children = listOf(ChildFirstEntity("", "ChildData", MySource))
    }

    assertTrue(entity.children.isNotEmpty())
    assertEquals("ChildData", (entity.children.single() as ChildFirstEntity).firstData)
  }

  @Test
  fun `parent with child and common data`() {
    val entity = ParentAbEntity(MySource) {
      children = listOf(ChildFirstEntity("ChildData", "", MySource))
    }

    assertTrue(entity.children.isNotEmpty())
    assertEquals("ChildData", (entity.children.single() as ChildFirstEntity).commonData)
  }

  @Test
  fun `parent with child multiple types`() {
    val entity = ParentAbEntity(MySource) {
      children = listOf(
        ChildFirstEntity("ChildData", "", MySource),
        ChildSecondEntity("ChildData", "", MySource),
      )
    }

    assertEquals(2, entity.children.size)
  }

  @Test
  fun `parent with multiple children in builder`() {
    val entity = ParentAbEntity(MySource) {
      children = listOf(ChildFirstEntity("ChildData", "Data", MySource))
    }

    val builder = MutableEntityStorage.create()
    builder.addEntity(entity)

    val parent = builder.entities(ParentAbEntity::class.java).single()
    val child = parent.children.single()
    assertEquals("ChildData", child.commonData)
  }

  @Test
  fun `parent with three children in builder`() {
    val entity = ParentAbEntity(MySource) {
      children = listOf(
        ChildFirstEntity("Data", "ChildData1", MySource),
        ChildSecondEntity("Data", "ChildData2", MySource),
      )
    }

    val builder = MutableEntityStorage.create()
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
    val entity = ParentAbEntity(MySource) {
      children = listOf(ChildFirstEntity("ChildData1", "FirstData", MySource))
    }

    val builder = MutableEntityStorage.create()
    builder.addEntity(entity)

    assertEquals("ChildData1", entity.children.single().commonData)
  }

  @Test
  fun `get parent by child`() {
    val entity = ParentAbEntity(MySource) {
      children = listOf(ChildFirstEntity("Data", "ChildData1", MySource))
    }

    val builder = MutableEntityStorage.create()
    builder.addEntity(entity)

    val child = builder.entities(ChildFirstEntity::class.java).single()
    assertEquals("Data", child.parentEntity.children.single().commonData)
  }

  @Test
  fun `add parent and then child`() {
    val builder = MutableEntityStorage.create()
    val parent = ParentAbEntity(MySource) {
      children = emptyList()
    }
    builder.addEntity(parent)

    val child = ChildFirstEntity("data", "data", MySource) {
      this.parentEntity = parent
    }
    builder.addEntity(child)

    assertEquals("data", builder.entities(ParentAbEntity::class.java).single().children.single().commonData)
  }

  @Test
  fun `add parent and then child 2`() {
    val builder = MutableEntityStorage.create()
    val parent = ParentAbEntity(MySource) {
      children = emptyList()
    }
    builder.addEntity(parent)

    val child = ChildFirstEntity("data", "data", MySource) {
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
    val builder = MutableEntityStorage.create()
    val parent = ParentAbEntity(MySource) {
      children = listOf(ChildFirstEntity("Data", "ChildData1", MySource))
    }
    builder.addEntity(parent)

    val child = ChildFirstEntity("data", "data", MySource) {
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
    val parent = ParentAbEntity(MySource) {
      children = listOf(ChildFirstEntity("data1", "data1", MySource))
    }

    val child = ChildFirstEntity("data2", "data2", MySource) {
      this.parentEntity = parent
    }

    assertDoesNotThrow { child.parentEntity.children.first().parentEntity }
    assertEquals(2, child.parentEntity.children.size)
    assertEquals(2, parent.children.size)
  }
}
