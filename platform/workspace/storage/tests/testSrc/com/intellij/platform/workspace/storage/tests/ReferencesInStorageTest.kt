// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests

import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.impl.assertConsistency
import com.intellij.platform.workspace.storage.impl.url.VirtualFileUrlManagerImpl
import com.intellij.platform.workspace.storage.testEntities.entities.*
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

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
    val child = XChildEntity("child", MySource) {
      dataClass = null
      parentEntity = XParentEntity("foo", MySource) {
        this.optionalChildren = emptyList()
        this.childChild = emptyList()
      }
      this.childChild = emptyList()
    }
    builder.addEntity(child)
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
    val parent1 = XParentEntity("parent1", MySource) {
      children = emptyList()
      this.optionalChildren = emptyList()
      this.childChild = emptyList()
    }
    val parent2 = XParentEntity("parent2", MySource) {
      children = emptyList()
      this.optionalChildren = emptyList()
      this.childChild = emptyList()
    }
    builder.addEntity(parent1)
    builder.addEntity(parent2)
    builder.assertConsistency()
    val child = XChildEntity("child", MySource) {
      dataClass = DataClassX("data", parent2.createPointer())
      this.parentEntity = parent1
      this.childChild = emptyList()
    }
    builder.addEntity(child)
    builder.assertConsistency()
    assertEquals(child, parent1.children.single())
    assertEquals(emptyList(), parent2.children.toList())
    assertEquals("parent1", child.parentEntity.parentProperty)
    assertEquals("parent2", child.dataClass!!.parent.resolve(builder)?.parentProperty)
    assertEquals(setOf(parent1, parent2), builder.entities(XParentEntity::class.java).toSet())

    builder.modifyEntity(child) {
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
      parentEntity = parent
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
      parentEntity = builder addEntity XParentEntity("parent", MySource)
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
      parentEntity = oldParent
    }
    val diff = createEmptyBuilder()
    val parent = diff addEntity XParentEntity("newParent", MySource)
    diff addEntity XChildEntity("newChild", MySource) {
      parentEntity = parent
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
      parentEntity = builder addEntity XParentEntity("parent", MySource)
      dataClass = null
      childChild = emptyList()
    }
    builder addEntity XChildEntity("child", MySource) {
      parentEntity = child1.parentEntity
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
      parentEntity = parent
    }
    builder addEntity XChildChildEntity(MySource) {
      parent1 = parent
      parent2 = child
    }
    builder.removeEntity(parent)
    builder.assertConsistency()
    assertEquals(emptyList(), builder.entities(XChildEntity::class.java).toList())
    assertEquals(emptyList(), builder.entities(XParentEntity::class.java).toList())
  }
}
