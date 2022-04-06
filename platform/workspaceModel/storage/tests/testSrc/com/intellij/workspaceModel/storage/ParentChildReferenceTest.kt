// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage

import com.intellij.workspaceModel.storage.entities.api.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class ParentChildReferenceTest {
  @Test
  fun `check parent and child both set at field while they are not in the store case one`() {
    val parentEntity = ParentEntity {
      entitySource = MySource
      parentData = "ParentData"
      child = ChildEntity {
        entitySource = MySource
        childData = "ChildData"
      }
    }
    parentEntity as ParentEntityImpl.Builder
    assertNotNull(parentEntity._child)

    val childEntity = parentEntity._child
    childEntity as ChildEntityImpl.Builder
    assertNotNull(childEntity._parentEntity)

    assertSame(childEntity._parentEntity, parentEntity)
    assertSame(childEntity.parentEntity, parentEntity)

    val builder = WorkspaceEntityStorageBuilder.create()
    builder.addEntity(parentEntity)

    assertNull(parentEntity._child)
    assertNull(childEntity._parentEntity)

    val childEntityFromStore = builder.entities(ChildEntity::class.java).single()
    assertTrue(parentEntity.child is ChildEntityImpl)
    assertTrue(childEntityFromStore is ChildEntityImpl.Builder)

    val parentEntityFromStore = builder.entities(ParentEntity::class.java).single()
    assertTrue(childEntity.parentEntity is ParentEntityImpl)
    assertTrue(parentEntityFromStore is ParentEntityImpl.Builder)
  }

  @Test
  fun `check parent and child both set at field while they are not in the store case two`() {
    val childEntity = ChildEntity {
      entitySource = MySource
      childData = "ChildData"
      parentEntity = ParentEntity {
        entitySource = MySource
        parentData = "ParentData"
      }
    }
    childEntity as ChildEntityImpl.Builder
    assertNotNull(childEntity._parentEntity)
    val parentEntity = childEntity._parentEntity

    parentEntity as ParentEntityImpl.Builder
    assertNotNull(parentEntity._child)

    assertSame(parentEntity._child, childEntity)
    assertSame(parentEntity.child, childEntity)

    val builder = WorkspaceEntityStorageBuilder.create()
    builder.addEntity(childEntity)

    assertNull(parentEntity._child)
    assertNull(childEntity._parentEntity)

    val childEntityFromStore = builder.entities(ChildEntity::class.java).single()
    assertTrue(parentEntity.child is ChildEntityImpl)
    assertTrue(childEntityFromStore is ChildEntityImpl.Builder)

    val parentEntityFromStore = builder.entities(ParentEntity::class.java).single()
    assertTrue(childEntity.parentEntity is ParentEntityImpl)
    assertTrue(parentEntityFromStore is ParentEntityImpl.Builder)
  }

  @Test
  fun `check parent and child both set at field while they are not in the store case three`() {
    val childEntity = ChildEntity {
      entitySource = MySource
      childData = "ChildData"
    }

    val parentEntity = ParentEntity {
      entitySource = MySource
      parentData = "ParentData"
      child = childEntity
    }

    childEntity as ChildEntityImpl.Builder
    assertNotNull(childEntity._parentEntity)

    parentEntity as ParentEntityImpl.Builder
    assertNotNull(parentEntity._child)

    assertSame(parentEntity._child, childEntity)
    assertSame(childEntity._parentEntity, parentEntity)
    assertSame(parentEntity.child, childEntity)
    assertSame(childEntity.parentEntity, parentEntity)

    val builder = WorkspaceEntityStorageBuilder.create()
    builder.addEntity(childEntity)

    assertNull(parentEntity._child)
    assertNull(childEntity._parentEntity)

    val childEntityFromStore = builder.entities(ChildEntity::class.java).single()
    assertTrue(parentEntity.child is ChildEntityImpl)
    assertTrue(childEntityFromStore is ChildEntityImpl.Builder)

    val parentEntityFromStore = builder.entities(ParentEntity::class.java).single()
    assertTrue(childEntity.parentEntity is ParentEntityImpl)
    assertTrue(parentEntityFromStore is ParentEntityImpl.Builder)
  }

  @Test
  fun `check parent and children both set at field while they are not in the store case one`() {
    val parentEntity = ParentMultipleEntity {
      entitySource = MySource
      parentData = "ParentData"
      children = listOf(
        ChildMultipleEntity {
          entitySource = MySource
          childData = "ChildOneData"
        },
        ChildMultipleEntity {
          entitySource = MySource
          childData = "ChildTwoData"
        }
      )
    }
    parentEntity as ParentMultipleEntityImpl.Builder
    val children = parentEntity._children
    assertNotNull(children)
    assertEquals(2, children.size)

    children.forEach { child ->
      child as ChildMultipleEntityImpl.Builder
      assertEquals(child.parentEntity, child._parentEntity)
      assertEquals(parentEntity, child._parentEntity)
    }

    val builder = WorkspaceEntityStorageBuilder.create()
    builder.addEntity(parentEntity)

    assertNull(parentEntity._children)
    children.forEach { child ->
      child as ChildMultipleEntityImpl.Builder
      assertNull(child._parentEntity)
    }

    val childrenFromStore = builder.entities(ChildMultipleEntity::class.java).toList()
    assertEquals(2, childrenFromStore.size)
    childrenFromStore.forEach { child ->
      child as ChildMultipleEntityImpl.Builder
      assertNotNull(child.parentEntity)
      assertEquals(parentEntity, child.parentEntity)
    }

    val parentEntityFromStore = builder.entities(ParentMultipleEntity::class.java).single()
    assertEquals(parentEntityFromStore.children.map { it.childData }.toSet(),
                 childrenFromStore.map { it.childData }.toSet())
  }

  @Test
  fun `check parent and children both set at field while they are not in the store case two`() {
    val childEntity = ChildMultipleEntity {
      entitySource = MySource
      childData = "ChildOneData"
      parentEntity = ParentMultipleEntity {
        entitySource = MySource
        parentData = "ParentData"
      }
    }

    childEntity as ChildMultipleEntityImpl.Builder
    assertNotNull(childEntity._parentEntity)
    assertSame(childEntity.parentEntity, childEntity._parentEntity)

    val parentEntity = childEntity._parentEntity
    parentEntity as ParentMultipleEntityImpl.Builder
    val children = parentEntity._children
    assertNotNull(children)
    assertEquals(1, children.size)
    assertSame(childEntity, children[0])

    val builder = WorkspaceEntityStorageBuilder.create()
    builder.addEntity(childEntity)

    assertNull(parentEntity._children)
    assertNull(childEntity._parentEntity)

    val childEntityFromStore = builder.entities(ChildMultipleEntity::class.java).single()
    childEntityFromStore as ChildMultipleEntityImpl.Builder
    assertNotNull(childEntityFromStore.parentEntity)

    val parentEntityFromStore = builder.entities(ParentMultipleEntity::class.java).single()
    parentEntityFromStore as ParentMultipleEntityImpl.Builder
    assertNotNull(parentEntityFromStore.children)
    assertEquals(1, parentEntityFromStore.children.size)
    assertEquals(parentEntityFromStore.children.map { it.childData }.single(), childEntityFromStore.childData)
  }

  @Test
  fun `check parent and children both set at field while they are not in the store case three`() {
    val parentEntity = ParentMultipleEntity {
      entitySource = MySource
      parentData = "ParentData"
    }
    parentEntity as ParentMultipleEntityImpl.Builder

    val firstChild = ChildMultipleEntity {
      this.entitySource = MySource
      this.childData = "ChildOneData"
      this.parentEntity = parentEntity
    }

    var children = parentEntity._children
    assertNotNull(children)
    assertEquals(1, children.size)
    assertSame(firstChild, children[0])

    val secondChild = ChildMultipleEntity {
      this.entitySource = MySource
      this.childData = "ChildTwoData"
      this.parentEntity = parentEntity
    }

    children = parentEntity._children
    assertNotNull(children)
    assertSame(parentEntity.children, parentEntity._children)
    assertEquals(2, children.size)

    children.forEach { child ->
      child as ChildMultipleEntityImpl.Builder
      assertEquals(child.parentEntity, child._parentEntity)
      assertEquals(parentEntity, child._parentEntity)
      assertTrue(firstChild === child || secondChild === child)
    }
  }

  @Test
  fun `check parent and children saved to the store`() {
    val builder = WorkspaceEntityStorageBuilder.create()
    val parentEntity = ParentMultipleEntity {
      entitySource = MySource
      parentData = "ParentData"
    }
    parentEntity as ParentMultipleEntityImpl.Builder

    val firstChild = ChildMultipleEntity {
      this.entitySource = MySource
      this.childData = "ChildOneData"
      this.parentEntity = parentEntity
    }
    firstChild as ChildMultipleEntityImpl.Builder

    builder.addEntity(parentEntity)

    assertNull(parentEntity._children)
    assertNull(firstChild._parentEntity)

    val childEntityFromStore = builder.entities(ChildMultipleEntity::class.java).single()
    childEntityFromStore as ChildMultipleEntityImpl.Builder
    assertNotNull(childEntityFromStore.parentEntity)

    val parentEntityFromStore = builder.entities(ParentMultipleEntity::class.java).single()
    parentEntityFromStore as ParentMultipleEntityImpl.Builder
    assertNotNull(parentEntityFromStore.children)
    assertEquals(1, parentEntityFromStore.children.size)
    assertEquals(parentEntityFromStore.children.map { it.childData }.single(), childEntityFromStore.childData)

    // Set parent from store
    val secondChild = ChildMultipleEntity {
      this.entitySource = MySource
      this.childData = "ChildTwoData"
      this.parentEntity = parentEntityFromStore
    }

    // Check that mutable parent has two children
    builder.addEntity(secondChild)
    var childrenFromStore = builder.entities(ChildMultipleEntity::class.java).toList()
    assertEquals(2, childrenFromStore.size)
    childrenFromStore.forEach { child ->
      child as ChildMultipleEntityImpl.Builder
      child.parentEntity as ParentMultipleEntityImpl
      assertTrue { child.childData in parentEntity.children.map { it.childData } }
    }
    assertEquals(2, parentEntity.children.size)


    // Set modifiable parent
    val thirdChild = ChildMultipleEntity {
      this.entitySource = MySource
      this.childData = "ChildThreeData"
      this.parentEntity = parentEntity
    }
    builder.addEntity(thirdChild)
    childrenFromStore = builder.entities(ChildMultipleEntity::class.java).toList()
    assertEquals(3, childrenFromStore.size)
    childrenFromStore.forEach { child ->
      child as ChildMultipleEntityImpl.Builder
      child.parentEntity as ParentMultipleEntityImpl
      assertTrue { child.childData in parentEntity.children.map { it.childData } }
    }
    assertEquals(3, parentEntity.children.size)
  }
}