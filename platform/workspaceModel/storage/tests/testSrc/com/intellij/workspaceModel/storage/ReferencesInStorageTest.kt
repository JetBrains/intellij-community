// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage

import com.intellij.workspaceModel.storage.impl.WorkspaceEntityStorageBuilderImpl
import com.intellij.workspaceModel.storage.impl.VirtualFileUrlManagerImpl
import com.intellij.workspaceModel.storage.entities.*
import com.intellij.workspaceModel.storage.entities.ModifiableChildEntity
import com.intellij.workspaceModel.storage.entities.ModifiableChildWithOptionalParentEntity
import com.intellij.workspaceModel.storage.entities.ModifiableParentEntity
import com.intellij.workspaceModel.storage.entities.ChildEntity
import com.intellij.workspaceModel.storage.entities.ChildWithOptionalParentEntity
import com.intellij.workspaceModel.storage.entities.DataClass
import com.intellij.workspaceModel.storage.entities.ParentEntity
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

private fun WorkspaceEntityStorage.singleParent() = entities(ParentEntity::class.java).single()

private fun WorkspaceEntityStorage.singleChild() = entities(ChildEntity::class.java).single()

class ReferencesInStorageTest {
  private lateinit var virtualFileManager: VirtualFileUrlManager
  @Before
  fun setUp() {
    virtualFileManager = VirtualFileUrlManagerImpl()
  }

  @Test
  fun `add entity`() {
    val builder = WorkspaceEntityStorageBuilderImpl.create()
    val child = builder.addChildEntity(builder.addParentEntity("foo"))
    builder.assertConsistency()
    assertEquals("foo", child.parent.parentProperty)
    assertEquals(child, builder.singleChild())
    assertEquals(child.parent, builder.singleParent())
    assertEquals(child, child.parent.children.single())
  }

  @Test
  fun `add entity via diff`() {
    val builder = WorkspaceEntityStorageBuilderImpl.create()
    val parentEntity = builder.addParentEntity("foo")

    val diff = WorkspaceEntityStorageBuilderImpl.from(builder.toStorage())
    diff.addChildEntity(parentEntity = parentEntity)
    builder.addDiff(diff)
    builder.assertConsistency()

    val child = builder.singleChild()
    assertEquals("foo", child.parent.parentProperty)
    assertEquals(child, builder.singleChild())
    assertEquals(child.parent, builder.singleParent())
    assertEquals(child, child.parent.children.single())
  }

  @Test
  fun `add remove reference inside data class`() {
    val builder = WorkspaceEntityStorageBuilderImpl.create()
    val parent1 = builder.addParentEntity("parent1")
    val parent2 = builder.addParentEntity("parent2")
    builder.assertConsistency()
    val child = builder.addChildEntity(parent1, "child", DataClass("data", builder.createReference(parent2)))
    builder.assertConsistency()
    assertEquals(child, parent1.children.single())
    assertEquals(emptyList<ChildEntity>(), parent2.children.toList())
    assertEquals("parent1", child.parent.parentProperty)
    assertEquals("parent2", child.dataClass!!.parent.resolve(builder).parentProperty)
    assertEquals(setOf(parent1, parent2), builder.entities(ParentEntity::class.java).toSet())

    builder.modifyEntity(ModifiableChildEntity::class.java, child) {
      dataClass = null
    }
    builder.assertConsistency()
    assertEquals(setOf(parent1, parent2), builder.entities(ParentEntity::class.java).toSet())
  }

  @Test
  fun `remove child entity`() {
    val builder = WorkspaceEntityStorageBuilderImpl.create()
    val parent = builder.addParentEntity()
    builder.assertConsistency()
    val child = builder.addChildEntity(parent)
    builder.assertConsistency()
    builder.removeEntity(child)
    builder.assertConsistency()
    assertEquals(emptyList<ChildEntity>(), builder.entities(ChildEntity::class.java).toList())
    assertEquals(emptyList<ChildEntity>(), parent.children.toList())
    assertEquals(parent, builder.singleParent())
  }

  @Test
  fun `remove parent entity`() {
    val builder = WorkspaceEntityStorageBuilderImpl.create()
    val child = builder.addChildEntity(builder.addParentEntity())
    builder.removeEntity(child.parent)
    builder.assertConsistency()
    assertEquals(emptyList<ChildEntity>(), builder.entities(ChildEntity::class.java).toList())
    assertEquals(emptyList<ParentEntity>(), builder.entities(ParentEntity::class.java).toList())
  }

  @Test
  fun `remove parent entity via diff`() {
    val builder = WorkspaceEntityStorageBuilderImpl.create()
    val oldParent = builder.addParentEntity("oldParent")
    val oldChild = builder.addChildEntity(oldParent, "oldChild")
    val diff = WorkspaceEntityStorageBuilderImpl.create()
    val parent = diff.addParentEntity("newParent")
    diff.addChildEntity(parent, "newChild")
    diff.removeEntity(parent)
    diff.assertConsistency()
    builder.addDiff(diff)
    builder.assertConsistency()
    assertEquals(listOf(oldChild), builder.entities(ChildEntity::class.java).toList())
    assertEquals(listOf(oldParent), builder.entities(ParentEntity::class.java).toList())
  }

  @Test
  fun `remove parent entity with two children`() {
    val builder = WorkspaceEntityStorageBuilderImpl.create()
    val child1 = builder.addChildEntity(builder.addParentEntity())
    builder.addChildEntity(parentEntity = child1.parent)
    builder.removeEntity(child1.parent)
    builder.assertConsistency()
    assertEquals(emptyList<ChildEntity>(), builder.entities(ChildEntity::class.java).toList())
    assertEquals(emptyList<ParentEntity>(), builder.entities(ParentEntity::class.java).toList())
  }

  @Test
  fun `remove parent entity in DAG`() {
    val builder = WorkspaceEntityStorageBuilderImpl.create()
    val parent = builder.addParentEntity()
    val child = builder.addChildEntity(parentEntity = parent)
    builder.addChildChildEntity(parent, child)
    builder.removeEntity(parent)
    builder.assertConsistency()
    assertEquals(emptyList<ChildEntity>(), builder.entities(ChildEntity::class.java).toList())
    assertEquals(emptyList<ParentEntity>(), builder.entities(ParentEntity::class.java).toList())
  }

  // UNSUPPORTED
/*
  @Test
  fun `remove parent entity referenced via data class`() {
    val builder = PEntityStorageBuilder.create()
    val parent1 = builder.addPParentEntity("parent1")
    val parent2 = builder.addPParentEntity("parent2")
    builder.addPChildEntity(parent1, "child", PDataClass("data", builder.createReference(parent2)))
    builder.removeEntity(parent2)
    builder.assertConsistency()
    assertEquals(emptyList<PChildEntity>(), builder.entities(PChildEntity::class.java).toList())
    assertEquals(listOf(parent1), builder.entities(PParentEntity::class.java).toList())
    assertEquals(emptyList<PChildEntity>(), parent1.children.toList())
  }
*/

  @Test
  fun `remove parent entity referenced via two paths`() {
    val builder = WorkspaceEntityStorageBuilderImpl.create()
    val parent = builder.addParentEntity()
    builder.addChildEntity(parent, "child", DataClass("data", builder.createReference(parent)))
    builder.assertConsistency()
    builder.removeEntity(parent)
    builder.assertConsistency()
    assertEquals(emptyList<ChildEntity>(), builder.entities(ChildEntity::class.java).toList())
    assertEquals(emptyList<ParentEntity>(), builder.entities(ParentEntity::class.java).toList())
  }

  @Test
  fun `modify parent property`() {
    val builder = WorkspaceEntityStorageBuilderImpl.create()
    val child = builder.addChildEntity(builder.addParentEntity())
    val oldParent = child.parent
    val newParent = builder.modifyEntity(ModifiableParentEntity::class.java, child.parent) {
      parentProperty = "changed"
    }
    builder.assertConsistency()
    assertEquals("changed", newParent.parentProperty)
    assertEquals(newParent, builder.singleParent())
    assertEquals(newParent, child.parent)
    assertEquals(child, newParent.children.single())
    assertEquals("parent", oldParent.parentProperty)
  }

  @Test
  fun `modify parent property via diff`() {
    val builder = WorkspaceEntityStorageBuilderImpl.create()
    val child = builder.addChildEntity(builder.addParentEntity())
    val oldParent = child.parent

    val diff = WorkspaceEntityStorageBuilderImpl.from(builder)
    diff.modifyEntity(ModifiableParentEntity::class.java, child.parent) {
      parentProperty = "changed"
    }
    builder.addDiff(diff)
    builder.assertConsistency()
    val newParent = builder.singleParent()
    assertEquals("changed", newParent.parentProperty)
    assertEquals(newParent, builder.singleParent())
    assertEquals(newParent, child.parent)
    assertEquals(child, newParent.children.single())
    assertEquals("parent", oldParent.parentProperty)
  }

  @Test
  fun `modify child property`() {
    val builder = WorkspaceEntityStorageBuilderImpl.create()
    val child = builder.addChildEntity(builder.addParentEntity())
    val oldParent = child.parent
    val newChild = builder.modifyEntity(ModifiableChildEntity::class.java, child) {
      childProperty = "changed"
    }
    builder.assertConsistency()
    assertEquals("changed", newChild.childProperty)
    assertEquals(oldParent, builder.singleParent())
    assertEquals(newChild, builder.singleChild())
    assertEquals(oldParent, newChild.parent)
    assertEquals(oldParent, child.parent)
    assertEquals(newChild, oldParent.children.single())
    assertEquals("child", child.childProperty)
  }

  @Test
  fun `modify reference to parent`() {
    val builder = WorkspaceEntityStorageBuilderImpl.create()
    val child = builder.addChildEntity(builder.addParentEntity())
    val oldParent = child.parent
    val newParent = builder.addParentEntity("new")
    val newChild = builder.modifyEntity(ModifiableChildEntity::class.java, child) {
      parent = newParent
    }
    builder.assertConsistency()
    assertEquals("child", newChild.childProperty)
    assertEquals(setOf(oldParent, newParent), builder.entities(ParentEntity::class.java).toSet())
    assertEquals(newChild, builder.singleChild())
    assertEquals(newParent, newChild.parent)
    assertEquals(newChild, newParent.children.single())

    assertEquals(newParent, child.parent)
    //assertEquals(oldParent, child.parent)  // ProxyBasedStore behaviour

    assertEquals(emptyList<ChildEntity>(), oldParent.children.toList())
  }

  @Test
  fun `modify reference to parent via data class`() {
    val builder = WorkspaceEntityStorageBuilderImpl.create()
    val parent1 = builder.addParentEntity("parent1")
    val oldParent = builder.addParentEntity("parent2")
    val child = builder.addChildEntity(parent1, "child", DataClass("data", builder.createReference(oldParent)))
    val newParent = builder.addParentEntity("new")
    builder.assertConsistency()
    val newChild = builder.modifyEntity(ModifiableChildEntity::class.java, child) {
      dataClass = DataClass("data2", builder.createReference(newParent))
    }
    builder.assertConsistency()
    assertEquals("child", newChild.childProperty)
    assertEquals("data2", newChild.dataClass!!.stringProperty)
    assertEquals(setOf(oldParent, newParent, parent1), builder.entities(ParentEntity::class.java).toSet())
    assertEquals(newChild, builder.singleChild())
    assertEquals(newParent, newChild.dataClass.parent.resolve(builder))
    assertEquals(oldParent, child.dataClass!!.parent.resolve(builder))
  }

  @Test
  fun `builder from storage`() {
    val storage = WorkspaceEntityStorageBuilderImpl.create().apply {
      addChildEntity(addParentEntity())
    }.toStorage()
    storage.assertConsistency()

    assertEquals("parent", storage.singleParent().parentProperty)

    val builder = WorkspaceEntityStorageBuilderImpl.from(storage)
    builder.assertConsistency()

    val oldParent = builder.singleParent()
    assertEquals("parent", oldParent.parentProperty)
    val newParent = builder.modifyEntity(ModifiableParentEntity::class.java, oldParent) {
      parentProperty = "changed"
    }
    builder.assertConsistency()
    assertEquals("changed", builder.singleParent().parentProperty)
    assertEquals("parent", storage.singleParent().parentProperty)
    assertEquals(newParent, builder.singleChild().parent)
    assertEquals("changed", builder.singleChild().parent.parentProperty)
    assertEquals("parent", storage.singleChild().parent.parentProperty)

    val parent2 = builder.addParentEntity("parent2")
    builder.modifyEntity(ModifiableChildEntity::class.java, builder.singleChild()) {
      dataClass = DataClass("data", builder.createReference(parent2))
    }
    builder.assertConsistency()
    assertEquals("parent", storage.singleParent().parentProperty)
    assertEquals(null, storage.singleChild().dataClass)
    assertEquals("data", builder.singleChild().dataClass!!.stringProperty)
    assertEquals(parent2, builder.singleChild().dataClass!!.parent.resolve(builder))
    assertEquals(setOf(parent2, newParent), builder.entities(ParentEntity::class.java).toSet())
  }

  @Test
  fun `storage from builder`() {
    val builder = WorkspaceEntityStorageBuilderImpl.create()
    val child = builder.addChildEntity(builder.addParentEntity())

    val snapshot = builder.toStorage()
    builder.assertConsistency()

    builder.modifyEntity(ModifiableParentEntity::class.java, child.parent) {
      parentProperty = "changed"
    }
    builder.assertConsistency()
    assertEquals("changed", builder.singleParent().parentProperty)
    assertEquals("changed", builder.singleChild().parent.parentProperty)
    assertEquals("parent", snapshot.singleParent().parentProperty)
    assertEquals("parent", snapshot.singleChild().parent.parentProperty)

    val parent2 = builder.addParentEntity("new")
    builder.modifyEntity(ModifiableChildEntity::class.java, child) {
      dataClass = DataClass("data", builder.createReference(parent2))
    }
    builder.assertConsistency()
    assertEquals("parent", snapshot.singleParent().parentProperty)
    assertEquals(null, snapshot.singleChild().dataClass)
    assertEquals(parent2, builder.singleChild().dataClass!!.parent.resolve(builder))
  }

  @Test
  fun `modify optional parent property`() {
    val builder = WorkspaceEntityStorageBuilderImpl.create()
    val child = builder.addChildWithOptionalParentEntity(null)
    Assert.assertNull(child.optionalParent)
    val newParent = builder.addParentEntity()
    assertEquals(emptyList<ChildWithOptionalParentEntity>(), newParent.optionalChildren.toList())
    val newChild = builder.modifyEntity(ModifiableChildWithOptionalParentEntity::class.java, child) {
      optionalParent = newParent
    }
    builder.assertConsistency()
    assertEquals(newParent, newChild.optionalParent)
    assertEquals(newChild, newParent.optionalChildren.single())

    val veryNewChild = builder.modifyEntity(ModifiableChildWithOptionalParentEntity::class.java, newChild) {
      optionalParent = null
    }
    Assert.assertNull(veryNewChild.optionalParent)
    assertEquals(emptyList<ChildWithOptionalParentEntity>(), newParent.optionalChildren.toList())
  }
}
