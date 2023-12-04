// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests

import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.testEntities.entities.ChildEntity
import com.intellij.platform.workspace.storage.testEntities.entities.MySource
import com.intellij.platform.workspace.storage.testEntities.entities.ParentEntity
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ParentAndNullableChildTest {
  @Test
  fun `parent with child`() {
    val entity = ParentEntity("ParentData", MySource) {
      child = ChildEntity("ChildData", MySource)
    }

    assertNotNull(entity.child)
    assertEquals("ChildData", entity.child?.childData)
  }

  @Test
  fun `parent with child in builder`() {
    val entity = ParentEntity("ParentData", MySource) {
      child = ChildEntity("ChildData", MySource)
    }

    val builder = MutableEntityStorage.create()
    builder.addEntity(entity)

    val single = builder.entities(ParentEntity::class.java).single()
    val child = single.child
    assertNotNull(child)
    assertEquals("ChildData", child.childData)
  }

  @Test
  fun `parent with null child in builder`() {
    val entity = ParentEntity("ParentData", MySource) {
      child = null
    }

    val builder = MutableEntityStorage.create()
    builder.addEntity(entity)

    val single = builder.entities(ParentEntity::class.java).single()
    assertNull(single.child)
  }

  @Test
  fun `parent with child in builder and accessing`() {
    val entity = ParentEntity("ParentData", MySource) {
      child = ChildEntity("ChildData", MySource)
    }

    val builder = MutableEntityStorage.create()
    builder.addEntity(entity)

    assertEquals("ChildData", entity.child?.childData)
  }

  @Test
  fun `parent with null child in builder and accessing`() {
    val entity = ParentEntity("ParentData", MySource) {
      child = null
    }

    val builder = MutableEntityStorage.create()
    builder.addEntity(entity)

    assertNull(entity.child)
  }

  @Test
  fun `get parent from the child`() {
    val entity = ParentEntity("ParentData", MySource) {
      child = ChildEntity("data", MySource)
    }

    val builder = MutableEntityStorage.create()
    builder.addEntity(entity)

    val single = builder.entities(ChildEntity::class.java).single()
    assertEquals("ParentData", single.parentEntity.parentData)
  }

  @Test
  fun `parent and child refs`() {
    val entity = ParentEntity("ParentData", MySource) {
      child = ChildEntity("data", MySource)
    }

    assertNotNull(entity.child)
    assertEquals("ParentData", entity.child!!.parentEntity.parentData)
  }

  @Test
  fun `parent in store and child`() {
    val entity = ParentEntity("ParentData", MySource)
    val builder = createEmptyBuilder()
    builder.addEntity(entity)

    val child = ChildEntity("data", MySource) {
      parentEntity = entity
    }

    assertNotNull(entity.child)
    assertEquals("ParentData", child.parentEntity.parentData)
    assertTrue(builder.entities(ChildEntity::class.java).toList().isEmpty())
    assertNull(builder.entities(ParentEntity::class.java).single().child)
  }

  @Test
  fun `parent in store and child add to store`() {
    val entity = ParentEntity("ParentData", MySource)
    val builder = createEmptyBuilder()
    builder.addEntity(entity)

    val child = ChildEntity("data", MySource) {
      parentEntity = entity
    }
    builder.addEntity(child)

    assertNotNull(entity.child)
    assertEquals("ParentData", child.parentEntity.parentData)
    assertNotNull(builder.entities(ChildEntity::class.java).toList().singleOrNull())
    assertNotNull(builder.entities(ParentEntity::class.java).single().child)
  }
}
