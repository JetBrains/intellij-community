// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage

import com.intellij.testFramework.UsefulTestCase.assertEmpty
import com.intellij.testFramework.UsefulTestCase.assertOneElement
import com.intellij.workspaceModel.storage.entities.test.addChildEntity
import com.intellij.workspaceModel.storage.entities.test.addParentEntity
import com.intellij.workspaceModel.storage.entities.test.api.*
import com.intellij.workspaceModel.storage.impl.MutableEntityStorageImpl
import com.intellij.workspaceModel.storage.impl.assertConsistency
import com.intellij.workspaceModel.storage.impl.exceptions.SymbolicIdAlreadyExistsException
import org.hamcrest.CoreMatchers
import com.intellij.workspaceModel.storage.entities.test.api.modifyEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException

class EntityWithSymbolicIdInPStorageTest {

  @JvmField
  @Rule
  val expectedException = ExpectedException.none()

  private lateinit var builder: MutableEntityStorageImpl

  @Before
  fun setUp() {
    builder = createEmptyBuilder()
  }

  @Test
  fun `add remove entity`() {
    val foo = builder.addLinkedListEntity("foo", LinkedListEntityId("bar"))
    builder.assertConsistency()
    assertNull(foo.next.resolve(builder))
    assertNull(foo.next.resolve(builder.toSnapshot()))
    val bar = builder.addLinkedListEntity("bar", LinkedListEntityId("baz"))
    builder.assertConsistency()
    assertEquals(bar, foo.next.resolve(builder))
    assertEquals(bar, foo.next.resolve(builder.toSnapshot()))
    builder.removeEntity(bar)
    builder.assertConsistency()
    assertNull(foo.next.resolve(builder))
  }

  @Test
  fun `change target entity name`() {
    builder.addLinkedListEntity("foo", LinkedListEntityId("bar"))
    val foo = builder.entities(LinkedListEntity::class.java).single { it.myName == "foo" }
    val bar = builder.addLinkedListEntity("bar", LinkedListEntityId("baz"))
    builder.assertConsistency()
    assertEquals(bar, foo.next.resolve(builder))
    builder.modifyEntity(bar) {
      myName = "baz"
    }
    builder.assertConsistency()
    assertEquals("baz", bar.myName)
    assertEquals(bar, foo.next.resolve(builder))
  }

  @Test
  fun `change name in reference`() {
    val foo = builder.addLinkedListEntity("foo", LinkedListEntityId("bar"))
    val bar = builder.addLinkedListEntity("bar", LinkedListEntityId("baz"))
    val baz = builder.addLinkedListEntity("baz", LinkedListEntityId("foo"))
    builder.assertConsistency()
    assertEquals(bar, foo.next.resolve(builder))
    val newFoo = builder.modifyEntity(foo) {
      next = LinkedListEntityId("baz")
    }
    builder.assertConsistency()
    assertEquals(baz, newFoo.next.resolve(builder))
  }

  @Test
  fun `remove child entity with parent entity`() {
    val parent = builder.addParentEntity("parent")
    builder.addChildEntity(parent)
    builder.assertConsistency()
    builder.removeEntity(parent)
    builder.assertConsistency()
    assertEmpty(builder.entities(XChildEntity::class.java).toList())
  }

  @Test
  fun `add entity with existing persistent id`() {
    builder = MutableEntityStorageImpl.create()
    expectedException.expectCause(CoreMatchers.isA(SymbolicIdAlreadyExistsException::class.java))
    builder.addNamedEntity("MyName")
    builder.addNamedEntity("MyName")
  }

  @Test
  @Ignore("Incorrect test")
  fun `add entity with existing persistent id - restoring after exception`() {
    builder = MutableEntityStorageImpl.create()
    try {
      builder.addNamedEntity("MyName")
      builder.addNamedEntity("MyName")
    }
    catch (e: AssertionError) {
      assert(e.cause is SymbolicIdAlreadyExistsException)
      assertOneElement(builder.entities(NamedEntity::class.java).toList())
    }
  }

  @Test
  fun `modify entity to repeat persistent id`() {
    builder = MutableEntityStorageImpl.create()
    expectedException.expectCause(CoreMatchers.isA(SymbolicIdAlreadyExistsException::class.java))
    builder.addNamedEntity("MyName")
    val namedEntity = builder.addNamedEntity("AnotherId")
    builder.modifyEntity(namedEntity) {
      this.myName = "MyName"
    }
  }

  @Test
  fun `modify entity to repeat persistent id - restoring after exception`() {
    builder = MutableEntityStorageImpl.create()
    try {
      builder.addNamedEntity("MyName")
      val namedEntity = builder.addNamedEntity("AnotherId")
      builder.modifyEntity(namedEntity) {
        this.myName = "MyName"
      }
    }
    catch (e: AssertionError) {
      assert(e.cause is SymbolicIdAlreadyExistsException)
      assertOneElement(builder.entities(NamedEntity::class.java).toList().filter { it.myName == "MyName" })
    }
  }
}