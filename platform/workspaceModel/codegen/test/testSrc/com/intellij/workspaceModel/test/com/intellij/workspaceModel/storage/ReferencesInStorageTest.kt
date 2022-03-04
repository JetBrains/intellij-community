// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage

import com.intellij.workspaceModel.storage.ChildEntity.Companion.dataClass
import com.intellij.workspaceModel.storage.impl.AbstractEntityStorage
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityStorageBuilderImpl
import com.intellij.workspaceModel.storage.impl.assertConsistency
import com.intellij.workspaceModel.storage.impl.url.VirtualFileUrlManagerImpl
import com.intellij.workspaceModel.codegen.storage.url.VirtualFileUrlManager
import org.jetbrains.deft.IntellijWsTestIj.modifyEntity
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun WorkspaceEntityStorage.singleParent() = entities(ParentEntity::class.java).single()

private fun WorkspaceEntityStorage.singleChild() = entities(ChildEntity::class.java).single()

class ReferencesInStorageTest {
  private lateinit var virtualFileManager: VirtualFileUrlManager
  @BeforeEach
  fun setUp() {
    //ClassToIntConverter.clear()
    virtualFileManager = VirtualFileUrlManagerImpl()
  }

  @Test
  fun `add entity`() {
    val builder = createEmptyBuilder()
    val child = builder.addChildEntity(builder.addParentEntity("foo"))
    (builder as WorkspaceEntityStorageBuilderImpl).assertConsistency()
    assertEquals("foo", child.parentEntity.parentProperty)
    assertEquals(child, builder.singleChild())
    assertEquals(child.parentEntity, builder.singleParent())
    assertEquals(child, child.parentEntity.children.single())
  }

  @Test
  fun `add entity via diff`() {
    val builder = createEmptyBuilder()
    val parentEntity = builder.addParentEntity("foo")

    val diff = createBuilderFrom(builder.toStorage())
    diff.addChildEntity(parentEntity = parentEntity)
    builder.addDiff(diff)
    (builder as WorkspaceEntityStorageBuilderImpl).assertConsistency()

    val child = builder.singleChild()
    assertEquals("foo", child.parentEntity.parentProperty)
    assertEquals(child, builder.singleChild())
    assertEquals(child.parentEntity, builder.singleParent())
    assertEquals(child, child.parentEntity.children.single())
  }

  @Test
  fun `add remove reference inside data class`() {
    val builder = createEmptyBuilder()
    val parent1 = builder.addParentEntity("parent1")
    val parent2 = builder.addParentEntity("parent2")
    (builder as AbstractEntityStorage).assertConsistency()
    val child = builder.addChildEntity(parent1, "child", DataClass("data", builder.createReference(parent2)))
    (builder as AbstractEntityStorage).assertConsistency()
    assertEquals(child, parent1.children.single())
    assertEquals(emptyList<ChildEntity>(), parent2.children.toList())
    assertEquals("parent1", child.parentEntity.parentProperty)
    assertEquals("parent2", child.dataClass!!.parent.resolve(builder)?.parentProperty)
    assertEquals(setOf(parent1, parent2), builder.entities(ParentEntity::class.java).toSet())

    builder.modifyEntity(child) {
      dataClass = null
    }
    (builder as AbstractEntityStorage).assertConsistency()
    assertEquals(setOf(parent1, parent2), builder.entities(ParentEntity::class.java).toSet())
  }

  @Test
  fun `remove child entity`() {
    val builder = createEmptyBuilder()
    val parent = builder.addParentEntity()
    (builder as WorkspaceEntityStorageBuilderImpl).assertConsistency()
    val child = builder.addChildEntity(parent)
    assertTrue(parent.children.toList().isNotEmpty())
    (builder as WorkspaceEntityStorageBuilderImpl).assertConsistency()
    builder.removeEntity(child)
    (builder as WorkspaceEntityStorageBuilderImpl).assertConsistency()
    assertEquals(emptyList<ChildEntity>(), builder.entities(ChildEntity::class.java).toList())
    assertTrue(parent.children.toList().isEmpty())
    assertEquals(parent, builder.singleParent())
  }

  @Test
  fun `remove parent entity`() {
    val builder = createEmptyBuilder()
    val child = builder.addChildEntity(builder.addParentEntity())
    builder.removeEntity(child.parentEntity)
    (builder as WorkspaceEntityStorageBuilderImpl).assertConsistency()
    assertEquals(emptyList<ChildEntity>(), builder.entities(ChildEntity::class.java).toList())
    assertEquals(emptyList<ParentEntity>(), builder.entities(ParentEntity::class.java).toList())
  }

  @Test
  fun `remove parent entity via diff`() {
    val builder = createEmptyBuilder()
    val oldParent = builder.addParentEntity("oldParent")
    val oldChild = builder.addChildEntity(oldParent, "oldChild")
    val diff = createEmptyBuilder()
    val parent = diff.addParentEntity("newParent")
    diff.addChildEntity(parent, "newChild")
    diff.removeEntity(parent)
    (diff as WorkspaceEntityStorageBuilderImpl).assertConsistency()
    builder.addDiff(diff)
    (builder as WorkspaceEntityStorageBuilderImpl).assertConsistency()
    assertEquals(listOf(oldChild), builder.entities(ChildEntity::class.java).toList())
    assertEquals(listOf(oldParent), builder.entities(ParentEntity::class.java).toList())
  }

  @Test
  fun `remove parent entity with two children`() {
    val builder = createEmptyBuilder()
    val child1 = builder.addChildEntity(builder.addParentEntity())
    builder.addChildEntity(parentEntity = child1.parentEntity)
    builder.removeEntity(child1.parentEntity)
    (builder as WorkspaceEntityStorageBuilderImpl).assertConsistency()
    assertEquals(emptyList<ChildEntity>(), builder.entities(ChildEntity::class.java).toList())
    assertEquals(emptyList<ParentEntity>(), builder.entities(ParentEntity::class.java).toList())
  }

  @Test
  fun `remove parent entity in DAG`() {
    val builder = createEmptyBuilder()
    val parent = builder.addParentEntity()
    val child = builder.addChildEntity(parentEntity = parent)
    builder.addChildChildEntity(parent, child)
    builder.removeEntity(parent)
    (builder as WorkspaceEntityStorageBuilderImpl).assertConsistency()
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
    (builder as WorkspaceEntityStorageBuilderImpl).assertConsistency()
    assertEquals(emptyList<PChildEntity>(), builder.entities(PChildEntity::class.java).toList())
    assertEquals(listOf(parent1), builder.entities(PParentEntity::class.java).toList())
    assertEquals(emptyList<PChildEntity>(), parent1.children.toList())
  }
*/

  @Test
  fun `remove parent entity referenced via two paths`() {
    val builder = createEmptyBuilder()
    val parent = builder.addParentEntity()
    builder.addChildEntity(parent, "child", DataClass("data", builder.createReference(parent)))
    (builder as WorkspaceEntityStorageBuilderImpl).assertConsistency()
    builder.removeEntity(parent)
    (builder as WorkspaceEntityStorageBuilderImpl).assertConsistency()
    assertEquals(emptyList<ChildEntity>(), builder.entities(ChildEntity::class.java).toList())
    assertEquals(emptyList<ParentEntity>(), builder.entities(ParentEntity::class.java).toList())
  }

  @Test
  fun `remove parent entity referenced via two paths via entity ref`() {
    val builder = createEmptyBuilder()
    val parent = builder.addParentEntity()
    builder.addChildEntity(parent, "child", DataClass("data", parent.createReference()))
    (builder as WorkspaceEntityStorageBuilderImpl).assertConsistency()
    builder.removeEntity(parent)
    (builder as WorkspaceEntityStorageBuilderImpl).assertConsistency()
    assertEquals(emptyList<ChildEntity>(), builder.entities(ChildEntity::class.java).toList())
    assertEquals(emptyList<ParentEntity>(), builder.entities(ParentEntity::class.java).toList())
  }

  @Test
  fun `modify parent property`() {
    val builder = createEmptyBuilder()
    val child = builder.addChildEntity(builder.addParentEntity())
    val oldParent = child.parentEntity
    val newParent = builder.modifyEntity(child.parentEntity) {
      parentProperty = "changed"
    }
    (builder as WorkspaceEntityStorageBuilderImpl).assertConsistency()
    assertEquals("changed", newParent.parentProperty)
    assertEquals(newParent, builder.singleParent())
    assertEquals(newParent, child.parentEntity)
    assertEquals(child, newParent.children.single())
    assertEquals("parent", oldParent.parentProperty)
  }

  @Test
  fun `modify parent property via diff`() {
    val builder = createEmptyBuilder()
    val child = builder.addChildEntity(builder.addParentEntity())
    val oldParent = child.parentEntity

    val diff = createBuilderFrom(builder)
    diff.modifyEntity(child.parentEntity) {
      parentProperty = "changed"
    }
    builder.addDiff(diff)
    (builder as WorkspaceEntityStorageBuilderImpl).assertConsistency()
    val newParent = builder.singleParent()
    assertEquals("changed", newParent.parentProperty)
    assertEquals(newParent, builder.singleParent())
    assertEquals(newParent, child.parentEntity)
    assertEquals(child, newParent.children.single())
    assertEquals("parent", oldParent.parentProperty)
  }

  @Test
  fun `modify child property`() {
    val builder = createEmptyBuilder()
    builder.addChildEntity(builder.addParentEntity())
    val child = builder.entities(ChildEntity::class.java).single()
    val oldParent = child.parentEntity
    val newChild = builder.modifyEntity(child) {
      childProperty = "changed"
    }
    (builder as WorkspaceEntityStorageBuilderImpl).assertConsistency()
    assertEquals("changed", newChild.childProperty)
    assertEquals(oldParent, builder.singleParent())
    assertEquals(newChild, builder.singleChild())
    assertEquals(oldParent, newChild.parentEntity)
    assertEquals(oldParent, child.parentEntity)
    assertEquals(newChild, oldParent.children.single())
    assertEquals("changed", child.childProperty)
  }

  @Test
  fun `modify reference to parent`() {
    val builder = createEmptyBuilder()
    val child = builder.addChildEntity(builder.addParentEntity())
    val oldParent = child.parentEntity
    val newParent = builder.addParentEntity("new")
    val newChild = builder.modifyEntity(child) {
      parentEntity = newParent
    }
    (builder as WorkspaceEntityStorageBuilderImpl).assertConsistency()
    assertEquals("child", newChild.childProperty)
    assertEquals(setOf(oldParent, newParent), builder.entities(ParentEntity::class.java).toSet())
    assertEquals(newChild, builder.singleChild())
    assertEquals(newParent, newChild.parentEntity)
    assertEquals(newChild, newParent.children.single())

    assertEquals(newParent, child.parentEntity)
    //assertEquals(oldParent, child.parent)  // ProxyBasedStore behaviour

    assertEquals(emptyList<ChildEntity>(), oldParent.children.toList())
  }

  @Test
  fun `modify reference to parent via data class`() {
    val builder = createEmptyBuilder()
    val parent1 = builder.addParentEntity("parent1")
    val oldParent = builder.addParentEntity("parent2")
    val child = builder.addChildEntity(parent1, "child", DataClass("data", builder.createReference(oldParent)))
    val newParent = builder.addParentEntity("new")
    (builder as WorkspaceEntityStorageBuilderImpl).assertConsistency()
    val newChild = builder.modifyEntity(child) {
      dataClass = DataClass("data2", builder.createReference(newParent))
    }
    (builder as WorkspaceEntityStorageBuilderImpl).assertConsistency()
    assertEquals("child", newChild.childProperty)
    assertEquals("data2", newChild.dataClass!!.stringProperty)
    assertEquals(setOf(oldParent, newParent, parent1), builder.entities(ParentEntity::class.java).toSet())
    assertEquals(newChild, builder.singleChild())
    assertEquals(newParent, newChild.dataClass!!.parent.resolve(builder))
    assertEquals(newParent, child.dataClass!!.parent.resolve(builder))
  }

  @Test
  fun `modify reference to parent via data class via entity ref`() {
    val builder = createEmptyBuilder()
    val parent1 = builder.addParentEntity("parent1")
    val oldParent = builder.addParentEntity("parent2")
    val child = builder.addChildEntity(parent1, "child", DataClass("data", oldParent.createReference()))
    val newParent = builder.addParentEntity("new")
    (builder as WorkspaceEntityStorageBuilderImpl).assertConsistency()
    val newChild = builder.modifyEntity(child) {
      dataClass = DataClass("data2", newParent.createReference())
    }
    (builder as WorkspaceEntityStorageBuilderImpl).assertConsistency()
    assertEquals("child", newChild.childProperty)
    assertEquals("data2", newChild.dataClass!!.stringProperty)
    assertEquals(setOf(oldParent, newParent, parent1), builder.entities(ParentEntity::class.java).toSet())
    assertEquals(newChild, builder.singleChild())
    assertEquals(newParent, newChild.dataClass!!.parent.resolve(builder))
    assertEquals(newParent, child.dataClass!!.parent.resolve(builder))
  }

  @Test
  fun `builder from storage`() {
    val storage = createEmptyBuilder().apply {
      addChildEntity(addParentEntity())
    }.toStorage()
    (storage as AbstractEntityStorage).assertConsistency()

    assertEquals("parent", storage.singleParent().parentProperty)

    val builder = createBuilderFrom(storage)
    (builder as AbstractEntityStorage).assertConsistency()

    val oldParent = builder.singleParent()
    assertEquals("parent", oldParent.parentProperty)
    val newParent = builder.modifyEntity(oldParent) {
      parentProperty = "changed"
    }
    (builder as AbstractEntityStorage).assertConsistency()
    assertEquals("changed", builder.singleParent().parentProperty)
    assertEquals("parent", storage.singleParent().parentProperty)
    assertEquals(newParent, builder.singleChild().parentEntity)
    assertEquals("changed", builder.singleChild().parentEntity.parentProperty)
    assertEquals("parent", storage.singleChild().parentEntity.parentProperty)

    val parent2 = builder.addParentEntity("parent2")
    builder.modifyEntity(builder.singleChild()) {
      dataClass = DataClass("data", builder.createReference(parent2))
    }
    (builder as WorkspaceEntityStorageBuilderImpl).assertConsistency()
    assertEquals("parent", storage.singleParent().parentProperty)
    assertEquals(null, storage.singleChild().dataClass)
    assertEquals("data", builder.singleChild().dataClass!!.stringProperty)
    assertEquals(parent2, builder.singleChild().dataClass!!.parent.resolve(builder))
    assertEquals(setOf(parent2, newParent), builder.entities(ParentEntity::class.java).toSet())
  }

  @Test
  fun `storage from builder`() {
    val builder = createEmptyBuilder()
    val child = builder.addChildEntity(builder.addParentEntity())

    val snapshot = builder.toStorage()
    (builder as WorkspaceEntityStorageBuilderImpl).assertConsistency()

    builder.modifyEntity(child.parentEntity) {
      parentProperty = "changed"
    }
    (builder as WorkspaceEntityStorageBuilderImpl).assertConsistency()
    assertEquals("changed", builder.singleParent().parentProperty)
    assertEquals("changed", builder.singleChild().parentEntity.parentProperty)
    assertEquals("parent", snapshot.singleParent().parentProperty)
    assertEquals("parent", snapshot.singleChild().parentEntity.parentProperty)

    val parent2 = builder.addParentEntity("new")
    builder.modifyEntity(child) {
      dataClass = DataClass("data", builder.createReference(parent2))
    }
    (builder as WorkspaceEntityStorageBuilderImpl).assertConsistency()
    assertEquals("parent", snapshot.singleParent().parentProperty)
    assertEquals(null, snapshot.singleChild().dataClass)
    assertEquals(parent2, builder.singleChild().dataClass!!.parent.resolve(builder))
  }

  @Test
  fun `modify optional parent property`() {
    val builder = createEmptyBuilder()
    val child = builder.addChildWithOptionalParentEntity(null)
    assertNull(child.optionalParent)
    val newParent = builder.addParentEntity()
    assertEquals(emptyList<ChildWithOptionalParentEntity>(), newParent.optionalChildren.toList())
    val newChild = builder.modifyEntity(child) {
      optionalParent = newParent
    }
    (builder as WorkspaceEntityStorageBuilderImpl).assertConsistency()
    assertEquals(newParent, newChild.optionalParent)
    assertEquals(newChild, newParent.optionalChildren.single())

    val veryNewChild = builder.modifyEntity(newChild) {
      optionalParent = null
    }
    assertNull(veryNewChild.optionalParent)
    assertEquals(emptyList<ChildWithOptionalParentEntity>(), newParent.optionalChildren.toList())
  }

  @Test
  fun `removing one to one parent`() {
    val builder = createEmptyBuilder()
    val parentEntity = builder.addOoParentEntity()
    builder.addOoChildEntity(parentEntity)

    builder.removeEntity(parentEntity)

    val parents = builder.entities(OoParentEntity::class.java).toList()
    val children = builder.entities(OoChildEntity::class.java).toList()

    assertTrue(parents.isEmpty())
    assertTrue(children.isEmpty())
  }

  @Test
  fun `add one to one entities`() {
    val builder = createEmptyBuilder()
    val parentEntity = builder.addOoParentEntity()
    builder.addOoParentEntity()
    builder.addOoChildEntity(parentEntity)

    (builder as WorkspaceEntityStorageBuilderImpl).assertConsistency()
  }

  @Test
  fun `test replace by source one to one nullable ref with parent persistent Id and child without it`() {
    val builder = createEmptyBuilder()
    var parentEntity = builder.addOoParentWithPidEntity("parent")
    builder.addOoChildForParentWithPidEntity(parentEntity, "child")
    builder.checkConsistency()

    val newBuilder = createEmptyBuilder()
    parentEntity = newBuilder.addOoParentWithPidEntity("parent", AnotherSource)
    newBuilder.addOoChildForParentWithPidEntity(parentEntity, "child", source = AnotherSource)
    newBuilder.checkConsistency()

    builder.replaceBySource({ it is AnotherSource }, newBuilder)
    builder.checkConsistency()

    val parent = builder.entities(OoParentWithPidEntity::class.java).single()
    val child = builder.entities(OoChildForParentWithPidEntity::class.java).single()
    assertEquals(AnotherSource, parent.entitySource)
    assertEquals(AnotherSource, child.entitySource)
    assertEquals(child, parent.childOne)
  }


  @Test
  fun `test replace by source with parent persistent Id and without children`() {
    val builder = createEmptyBuilder()
    val parentEntity = builder.addOoParentWithPidEntity("parent")
    builder.addOoChildForParentWithPidEntity(parentEntity, "child")
    builder.checkConsistency()

    val newBuilder = createEmptyBuilder()
    newBuilder.addOoParentWithPidEntity("parent", AnotherSource)
    newBuilder.checkConsistency()

    builder.replaceBySource({ it is AnotherSource }, newBuilder)
    builder.checkConsistency()
    val parent = builder.entities(OoParentWithPidEntity::class.java).single()
    val child = builder.entities(OoChildForParentWithPidEntity::class.java).single()
    assertEquals(AnotherSource, parent.entitySource)
    assertEquals(MySource, child.entitySource)
    assertEquals(child, parent.childOne)
  }

  @Test
  fun `test replace by source one to one ref with parent persistent Id and child without it and parent entity source intersection`() {
    val builder = createEmptyBuilder()
    var parentEntity = builder.addOoParentWithPidEntity("parent", AnotherSource)
    builder.addOoChildForParentWithPidEntity(parentEntity, "child")
    builder.checkConsistency()

    val newBuilder = createEmptyBuilder()
    parentEntity = newBuilder.addOoParentWithPidEntity("parent", AnotherSource)
    newBuilder.addOoChildForParentWithPidEntity(parentEntity, "child", source = AnotherSource)
    newBuilder.checkConsistency()

    builder.replaceBySource({ it is AnotherSource }, newBuilder)
    builder.checkConsistency()
    val parent = builder.entities(OoParentWithPidEntity::class.java).single()
    val child = builder.entities(OoChildForParentWithPidEntity::class.java).single()
    assertEquals(AnotherSource, parent.entitySource)
    assertEquals(AnotherSource, child.entitySource)
    assertEquals(child, parent.childOne)
  }

  @Test
  fun `test replace by source one to one ref with parent persistent Id and child without it and child entity source intersection`() {
    val builder = createEmptyBuilder()
    var parentEntity = builder.addOoParentWithPidEntity("parent")
    builder.addOoChildForParentWithPidEntity(parentEntity, "child", AnotherSource)
    builder.checkConsistency()

    val newBuilder = createEmptyBuilder()
    parentEntity = newBuilder.addOoParentWithPidEntity("parent", AnotherSource)
    newBuilder.addOoChildForParentWithPidEntity(parentEntity, "child", source = AnotherSource)
    newBuilder.checkConsistency()

    builder.replaceBySource({ it is AnotherSource }, newBuilder)
    builder.checkConsistency()
    val parent = builder.entities(OoParentWithPidEntity::class.java).single()
    val child = builder.entities(OoChildForParentWithPidEntity::class.java).single()
    assertEquals(AnotherSource, parent.entitySource)
    assertEquals(AnotherSource, child.entitySource)
    assertEquals(child, parent.childOne)
  }

  @Test
  fun `test replace by source one to one nullable ref with child persistent Id and parent without it`() {
    val builder = createEmptyBuilder()
    var parentEntity = builder.addOoParentWithoutPidEntity("parent")
    builder.addOoChildWithPidEntity(parentEntity, "child")
    builder.checkConsistency()

    val newBuilder = createEmptyBuilder()
    parentEntity = newBuilder.addOoParentWithoutPidEntity("parent", AnotherSource)
    newBuilder.addOoChildWithPidEntity(parentEntity, "child", source = AnotherSource)
    newBuilder.checkConsistency()

    builder.replaceBySource({ it is AnotherSource }, newBuilder)
    builder.checkConsistency()

    val listOfParent = builder.entities(OoParentWithoutPidEntity::class.java).toList()
    val child = builder.entities(OoChildWithPidEntity::class.java).single()
    assertEquals(2, listOfParent.size)
    assertEquals(MySource, listOfParent[0].entitySource)
    assertEquals(AnotherSource, listOfParent[1].entitySource)
    assertEquals(AnotherSource, child.entitySource)
    assertEquals("child", child.childProperty)
    assertEquals(listOfParent[1], child.parentEntity)
  }

  @Test
  fun `add child to a single parent`() {
    val builder = createEmptyBuilder()
    val parentEntity = builder.addOoParentEntity()
    builder.addOoChildEntity(parentEntity)
    builder.addOoChildEntity(parentEntity)

    (builder as WorkspaceEntityStorageBuilderImpl).assertConsistency()

    // Child has a not-null parent, so we remove it one field replace
    val children = builder.entities(OoChildEntity::class.java).toList()
    assertEquals(1, children.size)
  }

  @Test
  fun `double child adding`() {
    val builder = createEmptyBuilder()
    val parentEntity = builder.addOoParentEntity()
    builder.addOoChildWithNullableParentEntity(parentEntity)
    builder.addOoChildWithNullableParentEntity(parentEntity)

    (builder as WorkspaceEntityStorageBuilderImpl).assertConsistency()

    // Child has a nullable parent. So we just unlink a parent from one of the entities
    val children = builder.entities(OoChildWithNullableParentEntity::class.java).toList()
    assertEquals(2, children.size)

    if (children[0].parentEntity == null) {
      assertNotNull(children[1].parentEntity)
    }
    else {
      assertNull(children[1].parentEntity)
    }
  }

  @Test
  fun `remove children`() {
    val builder = createEmptyBuilder()
    val parentEntity = builder.addParentEntity()
    val childEntity = builder.addChildEntity(parentEntity)

    (builder as WorkspaceEntityStorageBuilderImpl).changeLog.clear()

    builder.modifyEntity(parentEntity) {
      this.children = emptyList<ChildEntity>()
    }

    (builder as WorkspaceEntityStorageBuilderImpl).assertConsistency()

    assertTrue(builder.entities(ChildEntity::class.java).toList().isEmpty())
  }

  @Test
  fun `remove multiple children`() {
    val builder = createEmptyBuilder()
    val parentEntity = builder.addParentEntity()
    val childEntity1 = builder.addChildEntity(parentEntity)
    val childEntity2 = builder.addChildEntity(parentEntity)

    (builder as WorkspaceEntityStorageBuilderImpl).changeLog.clear()

    builder.modifyEntity(parentEntity) {
      this.children = listOf(childEntity2)
    }

    (builder as WorkspaceEntityStorageBuilderImpl).assertConsistency()

    assertEquals(1, builder.entities(ChildEntity::class.java).toList().size)
  }

  @Test
  fun `replace one to one connection with adding already existing child`() {
    val builder = createEmptyBuilder()
    val parentEntity = builder.addOoParentWithPidEntity()
    builder.addOoChildForParentWithPidEntity(parentEntity)

    val anotherBuilder = createBuilderFrom(builder)
    anotherBuilder.addOoChildForParentWithPidEntity(parentEntity, childProperty = "MyProperty")

    // Modify initial builder
    builder.addOoChildForParentWithPidEntity(parentEntity)

    builder.addDiff(anotherBuilder)

    (builder as WorkspaceEntityStorageBuilderImpl).assertConsistency()

    assertEquals("MyProperty", builder.entities(OoParentWithPidEntity::class.java).single().childOne!!.childProperty)
  }

  @Test
  fun `pull one to one connection into another builder`() {
    val builder = createEmptyBuilder()

    val anotherBuilder = createBuilderFrom(builder)
    val parentEntity = anotherBuilder.addOoParentEntity()
    anotherBuilder.addOoChildWithNullableParentEntity(parentEntity)

    builder.addDiff(anotherBuilder)

    (builder as WorkspaceEntityStorageBuilderImpl).assertConsistency()

    assertTrue(builder.entities(OoParentEntity::class.java).toList().isNotEmpty())
    assertTrue(builder.entities(OoChildWithNullableParentEntity::class.java).toList().isNotEmpty())
  }
}
