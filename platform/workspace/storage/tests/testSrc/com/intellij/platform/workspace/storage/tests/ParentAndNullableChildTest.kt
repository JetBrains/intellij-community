// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests

import com.intellij.platform.workspace.storage.testEntities.entities.ChildNullableEntity
import com.intellij.platform.workspace.storage.testEntities.entities.MySource
import com.intellij.platform.workspace.storage.testEntities.entities.ParentNullableEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ParentAndNullableChildTest {
  @Test
  fun `parent with child`() {
    val entity = ParentNullableEntity("ParentData", MySource) {
      child = ChildNullableEntity("ChildData", MySource)
    }

    assertNotNull(entity.child)
    assertEquals("ChildData", entity.child?.childData)
  }

  @Test
  fun `parent with child in builder`() {
    val entity = ParentNullableEntity("ParentData", MySource) {
      child = ChildNullableEntity("ChildData", MySource)
    }

    val builder = MutableEntityStorage.create()
    builder.addEntity(entity)

    val single = builder.entities(ParentNullableEntity::class.java).single()
    val child = single.child
    assertNotNull(child)
    assertEquals("ChildData", child.childData)
  }

  @Test
  fun `parent with null child in builder`() {
    val entity = ParentNullableEntity("ParentData", MySource) {
      child = null
    }

    val builder = MutableEntityStorage.create()
    builder.addEntity(entity)

    val single = builder.entities(ParentNullableEntity::class.java).single()
    assertNull(single.child)
  }

  @Test
  fun `parent with child in builder and accessing`() {
    val entity = ParentNullableEntity("ParentData", MySource) {
      child = ChildNullableEntity("ChildData", MySource)
    }

    val builder = MutableEntityStorage.create()
    builder.addEntity(entity)

    assertEquals("ChildData", entity.child?.childData)
  }

  @Test
  fun `parent with null child in builder and accessing`() {
    val entity = ParentNullableEntity("ParentData", MySource) {
      child = null
    }

    val builder = MutableEntityStorage.create()
    builder.addEntity(entity)

    assertNull(entity.child)
  }

  @Test
  fun `get parent from the child`() {
    val entity = ParentNullableEntity("ParentData", MySource) {
      child = ChildNullableEntity("data", MySource)
    }

    val builder = MutableEntityStorage.create()
    builder.addEntity(entity)

    val single = builder.entities(ChildNullableEntity::class.java).single()
    assertEquals("ParentData", single.parentEntity.parentData)
  }

  @Test
  fun `parent and child refs`() {
    val entity = ParentNullableEntity("ParentData", MySource) {
      child = ChildNullableEntity("data", MySource)
    }

    assertNotNull(entity.child)
    assertEquals("ParentData", entity.child!!.parentEntity.parentData)
  }

  @Test
  fun `parent in store and child`() {
    val entity = ParentNullableEntity("ParentData", MySource)
    val builder = createEmptyBuilder()
    builder.addEntity(entity)

    val child = ChildNullableEntity("data", MySource) {
      parentEntity = entity
    }

    assertNotNull(entity.child)
    assertEquals("ParentData", child.parentEntity.parentData)
    assertTrue(builder.entities(ChildNullableEntity::class.java).toList().isEmpty())
    assertNull(builder.entities(ParentNullableEntity::class.java).single().child)
  }

  @Test
  fun `parent in store and child add to store`() {
    val entity = ParentNullableEntity("ParentData", MySource)
    val builder = createEmptyBuilder()
    builder.addEntity(entity)

    val child = ChildNullableEntity("data", MySource) {
      parentEntity = entity
    }
    builder.addEntity(child)

    assertNotNull(entity.child)
    assertEquals("ParentData", child.parentEntity.parentData)
    assertNotNull(builder.entities(ChildNullableEntity::class.java).toList().singleOrNull())
    assertNotNull(builder.entities(ParentNullableEntity::class.java).single().child)
  }
}