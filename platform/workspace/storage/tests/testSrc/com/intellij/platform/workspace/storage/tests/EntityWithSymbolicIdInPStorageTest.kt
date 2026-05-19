// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests

import com.intellij.platform.workspace.storage.impl.MutableEntityStorageImpl
import com.intellij.platform.workspace.storage.impl.assertConsistency
import com.intellij.platform.workspace.storage.impl.exceptions.SymbolicIdAlreadyExistsException
import com.intellij.platform.workspace.storage.testEntities.entities.ChildEntityWithSymbolicId
import com.intellij.platform.workspace.storage.testEntities.entities.ChildNameIdWithParentId
import com.intellij.platform.workspace.storage.testEntities.entities.LinkedListEntity
import com.intellij.platform.workspace.storage.testEntities.entities.LinkedListEntityId
import com.intellij.platform.workspace.storage.testEntities.entities.MySource
import com.intellij.platform.workspace.storage.testEntities.entities.NamedEntity
import com.intellij.platform.workspace.storage.testEntities.entities.ParentEntityWithSymbolicId
import com.intellij.platform.workspace.storage.testEntities.entities.ParentNameId
import com.intellij.platform.workspace.storage.testEntities.entities.XChildEntity
import com.intellij.platform.workspace.storage.testEntities.entities.XParentEntity
import com.intellij.platform.workspace.storage.testEntities.entities.modifyChildEntityWithSymbolicId
import com.intellij.platform.workspace.storage.testEntities.entities.modifyLinkedListEntity
import com.intellij.platform.workspace.storage.testEntities.entities.modifyNamedEntity
import com.intellij.platform.workspace.storage.testEntities.entities.modifyParentEntityWithSymbolicId
import com.intellij.platform.workspace.storage.testEntities.entities.modifyXParentEntity
import com.intellij.testFramework.UsefulTestCase.assertEmpty
import com.intellij.testFramework.UsefulTestCase.assertOneElement
import com.intellij.testFramework.assertErrorLogged
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class EntityWithSymbolicIdInPStorageTest {

  private lateinit var builder: MutableEntityStorageImpl

  @BeforeEach
  fun setUp() {
    builder = createEmptyBuilder()
  }

  @Test
  fun `add remove entity`() {
    val foo = builder addEntity LinkedListEntity("foo", LinkedListEntityId("bar"), MySource)
    builder.assertConsistency()
    Assertions.assertNull(foo.next.resolve(builder))
    Assertions.assertNull(foo.next.resolve(builder.toSnapshot()))
    val bar = builder addEntity LinkedListEntity("bar", LinkedListEntityId("baz"), MySource)
    builder.assertConsistency()
    assertEquals(bar, foo.next.resolve(builder))
    assertEquals(bar, foo.next.resolve(builder.toSnapshot()))
    builder.removeEntity(bar)
    builder.assertConsistency()
    Assertions.assertNull(foo.next.resolve(builder))
  }

  @Test
  fun `change target entity name`() {
    builder addEntity LinkedListEntity("foo", LinkedListEntityId("bar"), MySource)
    val foo = builder.entities(LinkedListEntity::class.java).single { it.myName == "foo" }
    val bar = builder addEntity LinkedListEntity("bar", LinkedListEntityId("baz"), MySource)
    builder.assertConsistency()
    assertEquals(bar, foo.next.resolve(builder))
    builder.modifyLinkedListEntity(bar) {
      myName = "baz"
    }
    builder.assertConsistency()
    assertEquals("baz", bar.myName)
    assertEquals(bar, foo.next.resolve(builder))
  }

  @Test
  fun `change name in reference`() {
    val foo = builder addEntity LinkedListEntity("foo", LinkedListEntityId("bar"), MySource)
    val bar = builder addEntity LinkedListEntity("bar", LinkedListEntityId("baz"), MySource)
    val baz = builder addEntity LinkedListEntity("baz", LinkedListEntityId("foo"), MySource)
    builder.assertConsistency()
    assertEquals(bar, foo.next.resolve(builder))
    val newFoo = builder.modifyLinkedListEntity(foo) {
      next = LinkedListEntityId("baz")
    }
    builder.assertConsistency()
    assertEquals(baz, newFoo.next.resolve(builder))
  }

  @Test
  fun `remove child entity with parent entity`() {
    val parent = builder addEntity XParentEntity("parent", MySource)
    builder addEntity XChildEntity("child", MySource) child@{
      builder.modifyXParentEntity(parent) parent@{
        this@child.parentEntity = this@parent
      }
    }
    builder.assertConsistency()
    builder.removeEntity(parent)
    builder.assertConsistency()
    assertEmpty(builder.entities(XChildEntity::class.java).toList())
  }

  @Test
  fun `add entity with existing persistent id`() {
    builder = createEmptyBuilder()
    assertErrorLogged<SymbolicIdAlreadyExistsException> {
      builder addEntity NamedEntity("MyName", MySource) {
        this.additionalProperty = null
        children = emptyList()
      }
      builder addEntity NamedEntity("MyName", MySource) {
        this.additionalProperty = null
        children = emptyList()
      }
    }
  }

  @Test
  @Disabled("Incorrect test")
  fun `add entity with existing persistent id - restoring after exception`() {
    builder = createEmptyBuilder()
    try {
      builder addEntity NamedEntity("MyName", MySource) {
        this.additionalProperty = null
        children = emptyList()
      }
      builder addEntity NamedEntity("MyName", MySource) {
        this.additionalProperty = null
        children = emptyList()
      }
    }
    catch (e: AssertionError) {
      assert(e.cause is SymbolicIdAlreadyExistsException)
      assertOneElement(builder.entities(NamedEntity::class.java).toList())
    }
  }

  @Test
  fun `modify entity to repeat persistent id`() {
    builder = createEmptyBuilder()
    assertErrorLogged<SymbolicIdAlreadyExistsException> {
      builder addEntity NamedEntity("MyName", MySource) {
        this.additionalProperty = null
        children = emptyList()
      }
      val namedEntity = builder addEntity NamedEntity("AnotherId", MySource) {
        this.additionalProperty = null
        children = emptyList()
      }
      builder.modifyNamedEntity(namedEntity) {
        this.myName = "MyName"
      }
    }
  }

  @Test
  fun `modify entity to repeat persistent id - restoring after exception`() {
    builder = createEmptyBuilder()
    assertErrorLogged<SymbolicIdAlreadyExistsException> {
      builder addEntity NamedEntity("MyName", MySource) {
        this.additionalProperty = null
        children = emptyList()
      }
      val namedEntity = builder addEntity NamedEntity("AnotherId", MySource)
      builder.modifyNamedEntity(namedEntity) {
        this.myName = "MyName"
      }
    }
    assertOneElement(builder.entities(NamedEntity::class.java).toList().filter { it.myName == "MyName" })
  }

  @Test
  fun `add parent and child that uses its id`() {
    val parentName = "parentName"
    val childName = "childName"
    val parentId = ParentNameId(parentName)
    val childId = ChildNameIdWithParentId(childName, parentId)
    
    val parentEntity = builder addEntity ParentEntityWithSymbolicId(parentName, MySource)
    builder.assertConsistency()
    val childEntity = builder addEntity ChildEntityWithSymbolicId(childName, MySource) addChild@{
      builder.modifyParentEntityWithSymbolicId(parentEntity) modifyParent@{
        this@addChild.parent = this@modifyParent
      }
    }
    builder.assertConsistency()
    assertEquals(parentEntity, parentId.resolve(builder))
    assertEquals(childEntity, childId.resolve(builder.toSnapshot()))
    builder.removeEntity(parentEntity)
    builder.assertConsistency()
    Assertions.assertNull(parentId.resolve(builder))
    Assertions.assertNull(childId.resolve(builder))
  }

  @Test
  fun `add parent and child that uses its id then change parent`() {
    val parentName1 = "parentName1"
    val parentId1 = ParentNameId(parentName1)
    val childName = "childName"
    val childId1 = ChildNameIdWithParentId(childName, parentId1)

    val parentEntity1 = builder addEntity ParentEntityWithSymbolicId(parentName1, MySource)
    builder.assertConsistency()
    val childEntity = builder addEntity ChildEntityWithSymbolicId(childName, MySource) addChild@{
      builder.modifyParentEntityWithSymbolicId(parentEntity1) modifyParent@{
        this@addChild.parent = this@modifyParent
      }
    }
    builder.assertConsistency()
    assertEquals(parentEntity1, parentId1.resolve(builder))
    assertEquals(childEntity, childId1.resolve(builder.toSnapshot()))

    val parentName2 = "parentName2"
    val parentId2 = ParentNameId(parentName2)
    val childId2 = ChildNameIdWithParentId(childName, parentId2)
    val parentEntity2 = builder.modifyParentEntityWithSymbolicId(parentEntity1) {
      myName = parentName2
    }
    val newChildEntity = parentEntity2.children.first()
    builder.assertConsistency()
    Assertions.assertNull(parentId1.resolve(builder))
    assertEquals(parentEntity2, parentId2.resolve(builder))
    Assertions.assertNull(childId1.resolve(builder))
    assertEquals(newChildEntity, childId2.resolve(builder.toSnapshot()))
  }

  @Test
  fun `add parent and try to add same name children`() {
    val childName = "childName"

    val parentEntity = builder addEntity ParentEntityWithSymbolicId("parentName", MySource)
    builder.assertConsistency()
    builder addEntity ChildEntityWithSymbolicId(childName, MySource) addChild@{
      builder.modifyParentEntityWithSymbolicId(parentEntity) modifyParent@{
        this@addChild.parent = this@modifyParent
      }
    }
    assertErrorLogged<SymbolicIdAlreadyExistsException> {
      builder addEntity ChildEntityWithSymbolicId(childName, MySource) addChild@{
        builder.modifyParentEntityWithSymbolicId(parentEntity) modifyParent@{
          this@addChild.parent = this@modifyParent
        }
      }
    }
    assertEquals(1, parentEntity.children.size)
    val anotherChild = builder addEntity ChildEntityWithSymbolicId("another name", MySource) addChild@{
      builder.modifyParentEntityWithSymbolicId(parentEntity) modifyParent@{
        this@addChild.parent = this@modifyParent
      }
    }
    builder.assertConsistency()
    assertEquals(2, parentEntity.children.size)
    assertErrorLogged<SymbolicIdAlreadyExistsException> {
      builder.modifyChildEntityWithSymbolicId(anotherChild) {
        this.myName = childName
      }
    }
    assertEquals(1, parentEntity.children.size)
  }
}