// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests

import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.impl.EntityLink
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.asBase
import com.intellij.platform.workspace.storage.testEntities.entities.*
import com.intellij.testFramework.UsefulTestCase.assertEmpty
import org.junit.jupiter.api.Test
import kotlin.test.*

class ParentChildReferenceTest {

  companion object {
    private val CHILD_CONNECTION_ID: ConnectionId = ConnectionId.create(ParentEntity::class.java, ChildEntity::class.java,
                                                                        ConnectionId.ConnectionType.ONE_TO_ONE, false)
    private val CHILDREN_CONNECTION_ID: ConnectionId = ConnectionId.create(ParentMultipleEntity::class.java,
                                                                           ChildMultipleEntity::class.java,
                                                                           ConnectionId.ConnectionType.ONE_TO_MANY, false)
  }

  @Test
  fun `check parent and child both set at field while they are not in the store case one`() {
    val parentEntity = ParentEntity("ParentData", MySource) {
      child = ChildEntity("ChildData", MySource)
    }
    parentEntity as ModifiableWorkspaceEntityBase<ParentEntity, *>
    assertNotNull(parentEntity.entityLinks[EntityLink(true, CHILD_CONNECTION_ID)])

    val childEntity = parentEntity.entityLinks[EntityLink(true, CHILD_CONNECTION_ID)]
    childEntity as ModifiableWorkspaceEntityBase<*, *>
    assertNotNull(childEntity.entityLinks[EntityLink(false, CHILD_CONNECTION_ID)])

    assertSame(childEntity.entityLinks[EntityLink(false, CHILD_CONNECTION_ID)], parentEntity)
    childEntity as ChildEntityBuilder
    assertSame(childEntity.parentEntity, parentEntity)

    val builder = MutableEntityStorage.create()
    builder.addEntity(parentEntity)

    assertNull(parentEntity.entityLinks[EntityLink(true, CHILD_CONNECTION_ID)])
    assertNull(childEntity.entityLinks[EntityLink(false, CHILD_CONNECTION_ID)])
  }

  @Test
  fun `check parent and child both set at field while they are not in the store case two`() {
    val childEntity = ChildEntity("ChildData", MySource) {
      parentEntity = ParentEntity("ParentData", MySource)
    }
    childEntity as ModifiableWorkspaceEntityBase<ChildEntity, *>
    assertNotNull(childEntity.entityLinks[EntityLink(false, CHILD_CONNECTION_ID)])
    val parentEntity = childEntity.entityLinks[EntityLink(false, CHILD_CONNECTION_ID)]

    parentEntity as ModifiableWorkspaceEntityBase<*, *>
    assertNotNull(parentEntity.entityLinks[EntityLink(true, CHILD_CONNECTION_ID)])

    assertSame(parentEntity.entityLinks[EntityLink(true, CHILD_CONNECTION_ID)], childEntity)
    parentEntity as ParentEntityBuilder
    assertSame(parentEntity.child, childEntity)

    val builder = MutableEntityStorage.create()
    builder.addEntity(childEntity)

    assertNull(parentEntity.entityLinks[EntityLink(true, CHILD_CONNECTION_ID)])
    assertNull(childEntity.entityLinks[EntityLink(false, CHILD_CONNECTION_ID)])
  }

  @Test
  fun `check parent and child both set at field while they are not in the store case three`() {
    val childEntity = ChildEntity("ChildData", MySource)

    val parentEntity = ParentEntity("ParentData", MySource) {
      child = childEntity
    }

    childEntity as ModifiableWorkspaceEntityBase<ChildEntity, *>
    assertNotNull(childEntity.entityLinks[EntityLink(false, CHILD_CONNECTION_ID)])

    parentEntity as ModifiableWorkspaceEntityBase<*, *>
    assertNotNull(parentEntity.entityLinks[EntityLink(true, CHILD_CONNECTION_ID)])

    assertSame(parentEntity.entityLinks[EntityLink(true, CHILD_CONNECTION_ID)], childEntity)
    assertSame(childEntity.entityLinks[EntityLink(false, CHILD_CONNECTION_ID)], parentEntity)
    assertSame(parentEntity.child, childEntity)
    assertSame(childEntity.parentEntity, parentEntity)

    val builder = MutableEntityStorage.create()
    builder.addEntity(childEntity)

    assertNull(parentEntity.entityLinks[EntityLink(true, CHILD_CONNECTION_ID)])
    assertNull(childEntity.entityLinks[EntityLink(false, CHILD_CONNECTION_ID)])
  }

  @Test
  fun `check parent and children both set at field while they are not in the store case one`() {
    val parentEntity = ParentMultipleEntity("ParentData", MySource) {
      children = listOf(
        ChildMultipleEntity("ChildOneData", MySource),
        ChildMultipleEntity("ChildTwoData", MySource)
      )
    }
    parentEntity as ModifiableWorkspaceEntityBase<ParentMultipleEntity, *>
    val children = parentEntity.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] as List<ChildMultipleEntityBuilder>?
    assertNotNull(children)
    assertEquals(2, children.size)

    children.forEach { child ->
      child as ModifiableWorkspaceEntityBase<*, *>
      assertEquals(child.parentEntity, child.entityLinks[EntityLink(false, CHILDREN_CONNECTION_ID)])
      assertEquals(parentEntity, child.entityLinks[EntityLink(false, CHILDREN_CONNECTION_ID)])
    }

    val builder = MutableEntityStorage.create()
    builder.addEntity(parentEntity)

    assertEmpty((parentEntity.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] as List<ChildMultipleEntityBuilder>?)!!)
    children.forEach { child ->
      child as ModifiableWorkspaceEntityBase<*, *>
      assertNull(child.entityLinks[EntityLink(false, CHILDREN_CONNECTION_ID)])
    }

    val childrenFromStore = builder.entities(ChildMultipleEntity::class.java).toList()
    assertEquals(2, childrenFromStore.size)
    childrenFromStore.forEach { child ->
      assertNotNull(child.parentEntity)
      assertEquals(parentEntity.id, child.parentEntity.asBase().id)
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

    childEntity as ModifiableWorkspaceEntityBase<ChildMultipleEntity, *>
    assertNotNull(childEntity.entityLinks[EntityLink(false, CHILDREN_CONNECTION_ID)])
    assertSame(childEntity.parentEntity, childEntity.entityLinks[EntityLink(false, CHILDREN_CONNECTION_ID)])

    val parentEntity = childEntity.entityLinks[EntityLink(false, CHILDREN_CONNECTION_ID)]
    parentEntity as ModifiableWorkspaceEntityBase<*, *>
    val children = parentEntity.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] as List<ChildMultipleEntityBuilder>?
    assertNotNull(children)
    assertEquals(1, children.size)
    assertSame(childEntity, children[0])

    val builder = MutableEntityStorage.create()
    builder.addEntity(childEntity)

    assertEmpty((parentEntity.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] as List<ChildMultipleEntityBuilder>?)!!)
    assertNull(childEntity.entityLinks[EntityLink(false, CHILDREN_CONNECTION_ID)])

    val childEntityFromStore = builder.entities(ChildMultipleEntity::class.java).single()
    assertNotNull(childEntityFromStore.parentEntity)

    val parentEntityFromStore = builder.entities(ParentMultipleEntity::class.java).single()
    assertNotNull(parentEntityFromStore.children)
    assertEquals(1, parentEntityFromStore.children.size)
    assertEquals(parentEntityFromStore.children.map { it.childData }.single(), childEntityFromStore.childData)
  }

  @Test
  fun `check parent and children both set at field while they are not in the store case three`() {
    val parentEntity = ParentMultipleEntity("ParentData", MySource)
    parentEntity as ModifiableWorkspaceEntityBase<*, *>

    val firstChild = ChildMultipleEntity("ChildOneData", MySource) {
      this.parentEntity = parentEntity
    }

    var children = parentEntity.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] as List<ChildMultipleEntityBuilder>?
    assertNotNull(children)
    assertEquals(1, children.size)
    assertSame(firstChild, children[0])

    val secondChild = ChildMultipleEntity("ChildTwoData", MySource) {
      this.parentEntity = parentEntity
    }

    children = parentEntity.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] as List<ChildMultipleEntityBuilder>?
    assertNotNull(children)
    assertSame(parentEntity.children, parentEntity.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] as List<ChildMultipleEntityBuilder>?)
    assertEquals(2, children.size)

    children.forEach { child ->
      child as ModifiableWorkspaceEntityBase<*, *>
      assertEquals(child.parentEntity, child.entityLinks[EntityLink(false, CHILDREN_CONNECTION_ID)])
      assertEquals(parentEntity, child.entityLinks[EntityLink(false, CHILDREN_CONNECTION_ID)])
      assertTrue(firstChild === child || secondChild === child)
    }
  }

  @Test
  fun `check parent and children saved to the store`() {
    val builder = MutableEntityStorage.create()
    val parentEntity = ParentMultipleEntity("ParentData", MySource)
    parentEntity as ModifiableWorkspaceEntityBase<ParentMultipleEntity, *>

    val firstChild = ChildMultipleEntity("ChildOneData", MySource) {
      this.parentEntity = parentEntity
    }
    firstChild as ModifiableWorkspaceEntityBase<*, *>

    builder.addEntity(parentEntity)

    assertEmpty((parentEntity.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] as List<ChildMultipleEntityBuilder>?)!!)
    assertNull(firstChild.entityLinks[EntityLink(false, CHILD_CONNECTION_ID)])

    val childEntityFromStore = builder.entities(ChildMultipleEntity::class.java).single()
    assertNotNull(childEntityFromStore.parentEntity)

    val parentEntityFromStore = builder.entities(ParentMultipleEntity::class.java).single()
    assertNotNull(parentEntityFromStore.children)
    assertEquals(1, parentEntityFromStore.children.size)
    assertEquals(parentEntityFromStore.children.map { it.childData }.single(), childEntityFromStore.childData)

    // Set parent from store
    val secondChild = ChildMultipleEntity("ChildTwoData", MySource) child@{
      builder.modifyParentMultipleEntity(parentEntityFromStore) parent@{
        this@child.parentEntity = this@parent
      }
    }

    // Check that mutable parent has two children
    builder.addEntity(secondChild)
    var childrenFromStore = builder.entities(ChildMultipleEntity::class.java).toList()
    assertEquals(2, childrenFromStore.size)
    childrenFromStore.forEach { child ->
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
      child.parentEntity
      assertTrue { child.childData in parentEntity.children.map { it.childData } }
    }
    assertEquals(3, parentEntity.children.size)
  }
}