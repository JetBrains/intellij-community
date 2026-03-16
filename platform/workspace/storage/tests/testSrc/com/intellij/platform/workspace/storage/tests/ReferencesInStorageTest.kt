// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests

import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.entities
import com.intellij.platform.workspace.storage.impl.assertConsistency
import com.intellij.platform.workspace.storage.impl.url.VirtualFileUrlManagerImpl
import com.intellij.platform.workspace.storage.testEntities.entities.DataClassX
import com.intellij.platform.workspace.storage.testEntities.entities.MySource
import com.intellij.platform.workspace.storage.testEntities.entities.OoChildEntity
import com.intellij.platform.workspace.storage.testEntities.entities.OoParentEntity
import com.intellij.platform.workspace.storage.testEntities.entities.OptionalOneToOneChildEntity
import com.intellij.platform.workspace.storage.testEntities.entities.OptionalOneToOneParentEntity
import com.intellij.platform.workspace.storage.testEntities.entities.XChildChildEntity
import com.intellij.platform.workspace.storage.testEntities.entities.XChildEntity
import com.intellij.platform.workspace.storage.testEntities.entities.XChildWithOptionalParentEntity
import com.intellij.platform.workspace.storage.testEntities.entities.XParentEntity
import com.intellij.platform.workspace.storage.testEntities.entities.modifyOoParentEntity
import com.intellij.platform.workspace.storage.testEntities.entities.modifyOptionalOneToOneParentEntity
import com.intellij.platform.workspace.storage.testEntities.entities.modifyXChildEntity
import com.intellij.platform.workspace.storage.testEntities.entities.modifyXChildWithOptionalParentEntity
import com.intellij.platform.workspace.storage.testEntities.entities.modifyXParentEntity
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private fun EntityStorage.singleParent() = entities(XParentEntity::class.java).single()

private fun EntityStorage.singleChild() = entities(XChildEntity::class.java).single()

class ReferencesInStorageTest {
  private lateinit var virtualFileManager: VirtualFileUrlManager

  @BeforeEach
  fun setUp() {
    virtualFileManager = VirtualFileUrlManagerImpl()
  }

  @Test
  fun `add entity`() {
    val builder = createEmptyBuilder()
    val child = builder addEntity XChildEntity("child", MySource) {
      dataClass = null
      parentEntity = XParentEntity("foo", MySource) {
        this.optionalChildren = emptyList()
        this.childChild = emptyList()
      }
      this.childChild = emptyList()
    }
    builder.assertConsistency()
    assertEquals("foo", child.parentEntity.parentProperty)
    assertEquals(child, builder.singleChild())
    assertEquals(child.parentEntity, builder.singleParent())
    assertEquals(child, child.parentEntity.children.single())
  }

  @Test
  fun `add entity via diff`() {
    val builder = createEmptyBuilder()
    val parentEntity = XParentEntity("foo", MySource) {
      children = emptyList()
      this.optionalChildren = emptyList()
      this.childChild = emptyList()
    }
    builder.addEntity(parentEntity)

    val diff = createBuilderFrom(builder.toSnapshot())
    val childEntity = XChildEntity("child", MySource) {
      dataClass = null
      this.parentEntity = parentEntity
      this.childChild = emptyList()
    }
    diff.addEntity(childEntity)
    builder.applyChangesFrom(diff)
    builder.assertConsistency()

    val child = builder.singleChild()
    assertEquals("foo", child.parentEntity.parentProperty)
    assertEquals(child, builder.singleChild())
    assertEquals(child.parentEntity, builder.singleParent())
    assertEquals(child, child.parentEntity.children.single())
  }

  @Test
  fun `add remove reference inside data class`() {
    val builder = createEmptyBuilder()
    val parent1 = builder addEntity XParentEntity("parent1", MySource) {
      children = emptyList()
      this.optionalChildren = emptyList()
      this.childChild = emptyList()
    }
    val parent2 = builder addEntity XParentEntity("parent2", MySource) {
      children = emptyList()
      this.optionalChildren = emptyList()
      this.childChild = emptyList()
    }
    builder.assertConsistency()
    val child = builder addEntity XChildEntity("child", MySource) child@{
      builder.modifyXParentEntity(parent1) parent1@{
        this@child.dataClass = DataClassX("data", parent2.createPointer())
        this@child.parentEntity = this@parent1
        this@child.childChild = emptyList()
      }
    }
    builder.assertConsistency()
    assertEquals(child, parent1.children.single())
    assertEquals(emptyList(), parent2.children.toList())
    assertEquals("parent1", child.parentEntity.parentProperty)
    assertEquals("parent2", child.dataClass!!.parent.resolve(builder)?.parentProperty)
    assertEquals(setOf(parent1, parent2), builder.entities(XParentEntity::class.java).toSet())

    builder.modifyXChildEntity(child) {
      dataClass = null
    }
    builder.assertConsistency()
    assertEquals(setOf(parent1, parent2), builder.entities(XParentEntity::class.java).toSet())
  }

  @Test
  fun `remove child entity`() {
    val builder = createEmptyBuilder()
    val parent = builder addEntity XParentEntity("parent", MySource)
    builder.assertConsistency()
    val child = builder addEntity XChildEntity("child", MySource) {
      parentEntity = parent.builderFrom(builder)
    }
    builder.assertConsistency()
    builder.removeEntity(child)
    builder.assertConsistency()
    assertEquals(emptyList(), builder.entities(XChildEntity::class.java).toList())
    assertEquals(emptyList(), parent.children.toList())
    assertEquals(parent, builder.singleParent())
  }

  @Test
  fun `remove parent entity`() {
    val builder = createEmptyBuilder()
    val child = builder addEntity XChildEntity("child", MySource) {
      parentEntity = XParentEntity("parent", MySource)
    }
    builder.removeEntity(child.parentEntity)
    builder.assertConsistency()
    assertEquals(emptyList(), builder.entities(XChildEntity::class.java).toList())
    assertEquals(emptyList(), builder.entities(XParentEntity::class.java).toList())
  }

  @Test
  fun `remove parent entity via diff`() {
    val builder = createEmptyBuilder()
    val oldParent = builder addEntity XParentEntity("oldParent", MySource)
    val oldChild = builder addEntity XChildEntity("oldChild", MySource) {
      parentEntity = oldParent.builderFrom(builder)
    }
    val diff = createEmptyBuilder()
    val parent = diff addEntity XParentEntity("newParent", MySource)
    diff addEntity XChildEntity("newChild", MySource) {
      parentEntity = parent.builderFrom(diff)
    }
    diff.removeEntity(parent)
    diff.assertConsistency()
    builder.applyChangesFrom(diff)
    builder.assertConsistency()
    assertEquals(listOf(oldChild), builder.entities(XChildEntity::class.java).toList())
    assertEquals(listOf(oldParent), builder.entities(XParentEntity::class.java).toList())
  }

  @Test
  fun `remove parent entity with two children`() {
    val builder = createEmptyBuilder()
    val child1 = builder addEntity XChildEntity("child", MySource) {
      parentEntity = XParentEntity("parent", MySource)
      dataClass = null
      childChild = emptyList()
    }
    builder addEntity XChildEntity("child", MySource) {
      parentEntity = child1.parentEntity.builderFrom(builder)
    }
    builder.removeEntity(child1.parentEntity)
    builder.assertConsistency()
    assertEquals(emptyList(), builder.entities(XChildEntity::class.java).toList())
    assertEquals(emptyList(), builder.entities(XParentEntity::class.java).toList())
  }

  @Test
  fun `remove parent entity in DAG`() {
    val builder = createEmptyBuilder()
    val parent = builder addEntity XParentEntity("parent", MySource)
    val child = builder addEntity XChildEntity("child", MySource) {
      parentEntity = parent.builderFrom(builder)
    }
    builder addEntity XChildChildEntity(MySource) {
      parent1 = parent.builderFrom(builder)
      parent2 = child.builderFrom(builder)
    }
    builder.removeEntity(parent)
    builder.assertConsistency()
    assertEquals(emptyList(), builder.entities(XChildEntity::class.java).toList())
    assertEquals(emptyList(), builder.entities(XParentEntity::class.java).toList())
  }

  @Test
  fun `removing a parent with a child that has optional parent`() {
    val target = createEmptyBuilder()
    val parent = target addEntity XParentEntity("Parent", MySource) {
      this.optionalChildren = listOf(
        XChildWithOptionalParentEntity("child", MySource),
      )
    }

    target.removeEntity(parent)

    // Child entity is cascade removed
    val optionalChildren = target.entities<XChildWithOptionalParentEntity>().toList()
    assertEquals(0, optionalChildren.size)
  }

  @Test
  fun `removing a parent with a child that has optional parent but preserve the child`() {
    val target = createEmptyBuilder()
    val parent = target addEntity XParentEntity("Parent", MySource) {
      this.optionalChildren = listOf(
        XChildWithOptionalParentEntity("child", MySource),
      )
    }

    target.modifyXChildWithOptionalParentEntity(parent.optionalChildren.single()) {
      this.optionalParent = null
    }
    target.removeEntity(parent)

    // Child entity is cascade removed
    val optionalChildren = target.entities<XChildWithOptionalParentEntity>().toList()
    assertEquals(1, optionalChildren.size)
  }

  @Test
  fun `modify parent by setting empty list to children with not null parent field`() {
    val target = createEmptyBuilder()
    val parent = target addEntity XParentEntity("Parent", MySource) {
      this.children = listOf(
        XChildEntity("child", MySource),
      )
    }

    target.modifyXParentEntity(parent) {
      this.children = emptyList()
    }

    // Child entity is removed from the storage because it has not nullable parent field
    val optionalChildren = target.entities<XChildEntity>().toList()
    assertEquals(0, optionalChildren.size)
  }

  @Test
  fun `modify parent by setting empty list to children with nullable parent field`() {
    val target = createEmptyBuilder()
    val parent = target addEntity XParentEntity("Parent", MySource) {
      this.optionalChildren = listOf(
        XChildWithOptionalParentEntity("child", MySource),
      )
    }

    target.modifyXParentEntity(parent) {
      this.optionalChildren = emptyList()
    }

    // Child entity is NOT removed from the storage because it has a nullable parent field
    val optionalChildren = target.entities<XChildWithOptionalParentEntity>().toList()
    assertEquals(1, optionalChildren.size)
    assertNull(optionalChildren.single().optionalParent)
  }

  @Test
  fun `modify parent by setting null to child with not nullable parent field`() {
    val target = createEmptyBuilder()
    val parent = target addEntity OoParentEntity("Parent", MySource) {
      this.child = OoChildEntity("child", MySource)
    }

    target.modifyOoParentEntity(parent) {
      this.child = null
    }

    // Child entity is removed from the storage because it has a not nullable parent field
    val optionalChildren = target.entities<XChildWithOptionalParentEntity>().toList()
    assertEquals(0, optionalChildren.size)
  }

  @Test
  fun `modify parent by setting new child with not nullable parent field`() {
    val target = createEmptyBuilder()
    val parent = target addEntity OoParentEntity("Parent", MySource) {
      this.child = OoChildEntity("child", MySource)
    }

    target.modifyOoParentEntity(parent) {
      this.child = OoChildEntity("new child", MySource)
    }

    // Child entity is removed from the storage because it has a not nullable parent field
    val optionalChildren = target.entities<OoChildEntity>().toList()
    assertEquals(1, optionalChildren.size)
    assertEquals("new child", optionalChildren.single().childProperty)
  }

  @Test
  fun `modify parent by setting null to child with nullable parent field`() {
    val target = createEmptyBuilder()
    val parent = target addEntity OptionalOneToOneParentEntity(MySource) {
      this.child = OptionalOneToOneChildEntity("data", MySource)
    }

    target.modifyOptionalOneToOneParentEntity(parent) {
      this.child = null
    }

    // Child entity is NOT removed from the storage because it has a nullable parent field
    val optionalChildren = target.entities<OptionalOneToOneChildEntity>().toList()
    assertEquals(1, optionalChildren.size)
    assertNull(optionalChildren.single().parent)
  }

  @Test
  fun `modify parent by setting new child with nullable parent field`() {
    val target = createEmptyBuilder()
    val parent = target addEntity OptionalOneToOneParentEntity(MySource) {
      this.child = OptionalOneToOneChildEntity("data", MySource)
    }

    target.modifyOptionalOneToOneParentEntity(parent) {
      this.child = OptionalOneToOneChildEntity("new data", MySource)
    }

    // Child entity is NOT removed from the storage because it has a nullable parent field
    val optionalChildren = target.entities<OptionalOneToOneChildEntity>().toList()
    assertEquals(2, optionalChildren.size)
  }
}
