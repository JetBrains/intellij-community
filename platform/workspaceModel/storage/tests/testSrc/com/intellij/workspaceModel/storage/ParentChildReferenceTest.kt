// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage

import com.intellij.testFramework.UsefulTestCase.assertEmpty
import com.intellij.workspaceModel.storage.entities.test.api.*
import com.intellij.workspaceModel.storage.impl.ConnectionId
import org.junit.jupiter.api.Test
import kotlin.test.*

class ParentChildReferenceTest {
  
  companion object {
    private val CHILD_CONNECTION_ID: ConnectionId = ConnectionId.create(ParentEntity::class.java, ChildEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false)
    private val CHILDREN_CONNECTION_ID: ConnectionId = ConnectionId.create(ParentMultipleEntity::class.java, ChildMultipleEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, false)
  }
  
  @Test
  fun `check parent and child both set at field while they are not in the store case one`() {
    val parentEntity = ParentEntity("ParentData", MySource) {
      child = ChildEntity("ChildData", MySource)
    }
    parentEntity as ParentEntityImpl.Builder
    assertNotNull(parentEntity.entityLinks[CHILD_CONNECTION_ID]?.entity)

    val childEntity = parentEntity.entityLinks[CHILD_CONNECTION_ID]?.entity
    childEntity as ChildEntityImpl.Builder
    assertNotNull(childEntity.entityLinks[CHILD_CONNECTION_ID]?.entity)

    assertSame(childEntity.entityLinks[CHILD_CONNECTION_ID]?.entity, parentEntity)
    assertSame(childEntity.parentEntity, parentEntity)

    val builder = MutableEntityStorage.create()
    builder.addEntity(parentEntity)

    assertNull(parentEntity.entityLinks[CHILD_CONNECTION_ID]?.entity)
    assertNull(childEntity.entityLinks[CHILD_CONNECTION_ID]?.entity)

    val childEntityFromStore = builder.entities(ChildEntity::class.java).single()
    assertTrue(parentEntity.child is ChildEntityImpl)
    assertTrue(childEntityFromStore is ChildEntityImpl.Builder)

    val parentEntityFromStore = builder.entities(ParentEntity::class.java).single()
    assertTrue(childEntity.parentEntity is ParentEntityImpl)
    assertTrue(parentEntityFromStore is ParentEntityImpl.Builder)
  }

  @Test
  fun `check parent and child both set at field while they are not in the store case two`() {
    val childEntity = ChildEntity("ChildData", MySource) {
      parentEntity = ParentEntity("ParentData", MySource)
    }
    childEntity as ChildEntityImpl.Builder
    assertNotNull(childEntity.entityLinks[CHILD_CONNECTION_ID]?.entity)
    val parentEntity = childEntity.entityLinks[CHILD_CONNECTION_ID]?.entity

    parentEntity as ParentEntityImpl.Builder
    assertNotNull(parentEntity.entityLinks[CHILD_CONNECTION_ID]?.entity)

    assertSame(parentEntity.entityLinks[CHILD_CONNECTION_ID]?.entity, childEntity)
    assertSame(parentEntity.child, childEntity)

    val builder = MutableEntityStorage.create()
    builder.addEntity(childEntity)

    assertNull(parentEntity.entityLinks[CHILD_CONNECTION_ID]?.entity)
    assertNull(childEntity.entityLinks[CHILD_CONNECTION_ID]?.entity)

    val childEntityFromStore = builder.entities(ChildEntity::class.java).single()
    assertTrue(parentEntity.child is ChildEntityImpl)
    assertTrue(childEntityFromStore is ChildEntityImpl.Builder)

    val parentEntityFromStore = builder.entities(ParentEntity::class.java).single()
    assertTrue(childEntity.parentEntity is ParentEntityImpl)
    assertTrue(parentEntityFromStore is ParentEntityImpl.Builder)
  }

  @Test
  fun `check parent and child both set at field while they are not in the store case three`() {
    val childEntity = ChildEntity("ChildData", MySource)

    val parentEntity = ParentEntity("ParentData", MySource) {
      child = childEntity
    }

    childEntity as ChildEntityImpl.Builder
    assertNotNull(childEntity.entityLinks[CHILD_CONNECTION_ID]?.entity)

    parentEntity as ParentEntityImpl.Builder
    assertNotNull(parentEntity.entityLinks[CHILD_CONNECTION_ID]?.entity)

    assertSame(parentEntity.entityLinks[CHILD_CONNECTION_ID]?.entity, childEntity)
    assertSame(childEntity.entityLinks[CHILD_CONNECTION_ID]?.entity, parentEntity)
    assertSame(parentEntity.child, childEntity)
    assertSame(childEntity.parentEntity, parentEntity)

    val builder = MutableEntityStorage.create()
    builder.addEntity(childEntity)

    assertNull(parentEntity.entityLinks[CHILD_CONNECTION_ID]?.entity)
    assertNull(childEntity.entityLinks[CHILD_CONNECTION_ID]?.entity)

    val childEntityFromStore = builder.entities(ChildEntity::class.java).single()
    assertTrue(parentEntity.child is ChildEntityImpl)
    assertTrue(childEntityFromStore is ChildEntityImpl.Builder)

    val parentEntityFromStore = builder.entities(ParentEntity::class.java).single()
    assertTrue(childEntity.parentEntity is ParentEntityImpl)
    assertTrue(parentEntityFromStore is ParentEntityImpl.Builder)
  }

  @Test
  fun `check parent and children both set at field while they are not in the store case one`() {
    val parentEntity = ParentMultipleEntity("ParentData", MySource) {
      children = listOf(
        ChildMultipleEntity("ChildOneData", MySource),
        ChildMultipleEntity("ChildTwoData", MySource)
      )
    }
    parentEntity as ParentMultipleEntityImpl.Builder
    val children = parentEntity.entityLinks[CHILDREN_CONNECTION_ID]?.entity as List<ChildMultipleEntity>?
    assertNotNull(children)
    assertEquals(2, children.size)

    children.forEach { child ->
      child as ChildMultipleEntityImpl.Builder
      assertEquals(child.parentEntity, child.entityLinks[CHILDREN_CONNECTION_ID]?.entity)
      assertEquals(parentEntity, child.entityLinks[CHILDREN_CONNECTION_ID]?.entity)
    }

    val builder = MutableEntityStorage.create()
    builder.addEntity(parentEntity)

    assertEmpty((parentEntity.entityLinks[CHILDREN_CONNECTION_ID]?.entity as List<ChildMultipleEntity>?)!!)
    children.forEach { child ->
      child as ChildMultipleEntityImpl.Builder
      assertNull(child.entityLinks[CHILDREN_CONNECTION_ID]?.entity)
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
    val childEntity = ChildMultipleEntity("ChildOneData", MySource) {
      parentEntity = ParentMultipleEntity("ParentData", MySource)
    }

    childEntity as ChildMultipleEntityImpl.Builder
    assertNotNull(childEntity.entityLinks[CHILDREN_CONNECTION_ID]?.entity)
    assertSame(childEntity.parentEntity, childEntity.entityLinks[CHILDREN_CONNECTION_ID]?.entity)

    val parentEntity = childEntity.entityLinks[CHILDREN_CONNECTION_ID]?.entity
    parentEntity as ParentMultipleEntityImpl.Builder
    val children = parentEntity.entityLinks[CHILDREN_CONNECTION_ID]?.entity as List<ChildMultipleEntity>?
    assertNotNull(children)
    assertEquals(1, children.size)
    assertSame(childEntity, children[0])

    val builder = MutableEntityStorage.create()
    builder.addEntity(childEntity)

    assertEmpty((parentEntity.entityLinks[CHILDREN_CONNECTION_ID]?.entity as List<ChildMultipleEntity>?)!!)
    assertNull(childEntity.entityLinks[CHILDREN_CONNECTION_ID]?.entity)

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
    val parentEntity = ParentMultipleEntity("ParentData", MySource)
    parentEntity as ParentMultipleEntityImpl.Builder

    val firstChild = ChildMultipleEntity("ChildOneData", MySource) {
      this.parentEntity = parentEntity
    }

    var children = parentEntity.entityLinks[CHILDREN_CONNECTION_ID]?.entity as List<ChildMultipleEntity>?
    assertNotNull(children)
    assertEquals(1, children.size)
    assertSame(firstChild, children[0])

    val secondChild = ChildMultipleEntity("ChildTwoData", MySource) {
      this.parentEntity = parentEntity
    }

    children = parentEntity.entityLinks[CHILDREN_CONNECTION_ID]?.entity as List<ChildMultipleEntity>?
    assertNotNull(children)
    assertSame(parentEntity.children, parentEntity.entityLinks[CHILDREN_CONNECTION_ID]?.entity as List<ChildMultipleEntity>?)
    assertEquals(2, children.size)

    children.forEach { child ->
      child as ChildMultipleEntityImpl.Builder
      assertEquals(child.parentEntity, child.entityLinks[CHILDREN_CONNECTION_ID]?.entity)
      assertEquals(parentEntity, child.entityLinks[CHILDREN_CONNECTION_ID]?.entity)
      assertTrue(firstChild === child || secondChild === child)
    }
  }

  @Test
  fun `check parent and children saved to the store`() {
    val builder = MutableEntityStorage.create()
    val parentEntity = ParentMultipleEntity("ParentData", MySource)
    parentEntity as ParentMultipleEntityImpl.Builder

    val firstChild = ChildMultipleEntity("ChildOneData", MySource) {
      this.parentEntity = parentEntity
    }
    firstChild as ChildMultipleEntityImpl.Builder

    builder.addEntity(parentEntity)

    assertEmpty((parentEntity.entityLinks[CHILDREN_CONNECTION_ID]?.entity as List<ChildMultipleEntity>?)!!)
    assertNull(firstChild.entityLinks[CHILD_CONNECTION_ID]?.entity)

    val childEntityFromStore = builder.entities(ChildMultipleEntity::class.java).single()
    childEntityFromStore as ChildMultipleEntityImpl.Builder
    assertNotNull(childEntityFromStore.parentEntity)

    val parentEntityFromStore = builder.entities(ParentMultipleEntity::class.java).single()
    parentEntityFromStore as ParentMultipleEntityImpl.Builder
    assertNotNull(parentEntityFromStore.children)
    assertEquals(1, parentEntityFromStore.children.size)
    assertEquals(parentEntityFromStore.children.map { it.childData }.single(), childEntityFromStore.childData)

    // Set parent from store
    val secondChild = ChildMultipleEntity("ChildTwoData", MySource) {
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
    val thirdChild = ChildMultipleEntity("ChildThreeData", MySource) {
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