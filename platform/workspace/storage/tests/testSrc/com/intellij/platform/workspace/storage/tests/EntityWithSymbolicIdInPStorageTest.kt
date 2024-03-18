// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.workspace.storage.tests

import com.intellij.platform.workspace.storage.impl.MutableEntityStorageImpl
import com.intellij.platform.workspace.storage.impl.assertConsistency
import com.intellij.platform.workspace.storage.impl.exceptions.SymbolicIdAlreadyExistsException
import com.intellij.platform.workspace.storage.testEntities.entities.*
import com.intellij.testFramework.UsefulTestCase.assertEmpty
import com.intellij.testFramework.UsefulTestCase.assertOneElement
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
    val foo = builder.addLinkedListEntity("foo", LinkedListEntityId("bar"))
    builder.assertConsistency()
    Assertions.assertNull(foo.next.resolve(builder))
    Assertions.assertNull(foo.next.resolve(builder.toSnapshot()))
    val bar = builder.addLinkedListEntity("bar", LinkedListEntityId("baz"))
    builder.assertConsistency()
    assertEquals(bar, foo.next.resolve(builder))
    assertEquals(bar, foo.next.resolve(builder.toSnapshot()))
    builder.removeEntity(bar)
    builder.assertConsistency()
    Assertions.assertNull(foo.next.resolve(builder))
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
    val parent = builder addEntity XParentEntity("parent", MySource)
    builder addEntity XChildEntity("child", MySource) {
      parentEntity = parent
    }
    builder.assertConsistency()
    builder.removeEntity(parent)
    builder.assertConsistency()
    assertEmpty(builder.entities(XChildEntity::class.java).toList())
  }

  @Test
  fun `add entity with existing persistent id`() {
    builder = createEmptyBuilder()
    assertThrowsLogError<SymbolicIdAlreadyExistsException> {
      builder.addNamedEntity("MyName")
      builder.addNamedEntity("MyName")
    }
  }

  @Test
  @Disabled("Incorrect test")
  fun `add entity with existing persistent id - restoring after exception`() {
    builder = createEmptyBuilder()
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
    builder = createEmptyBuilder()
    assertThrowsLogError<SymbolicIdAlreadyExistsException> {
      builder.addNamedEntity("MyName")
      val namedEntity = builder.addNamedEntity("AnotherId")
      builder.modifyEntity(namedEntity) {
        this.myName = "MyName"
      }
    }
  }

  @Test
  fun `modify entity to repeat persistent id - restoring after exception`() {
    builder = createEmptyBuilder()
    assertThrowsLogError<SymbolicIdAlreadyExistsException> {
      builder.addNamedEntity("MyName")
      val namedEntity = builder.addNamedEntity("AnotherId")
      builder.modifyEntity(namedEntity) {
        this.myName = "MyName"
      }
    }
    assertOneElement(builder.entities(NamedEntity::class.java).toList().filter { it.myName == "MyName" })
  }
}