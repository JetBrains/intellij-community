// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests

import com.intellij.platform.workspace.storage.impl.MutableEntityStorageImpl
import com.intellij.platform.workspace.storage.impl.assertConsistency
import com.intellij.platform.workspace.storage.impl.exceptions.SymbolicIdAlreadyExistsException
import com.intellij.platform.workspace.storage.testEntities.entities.*
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
}