// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage

import com.intellij.testFramework.UsefulTestCase.assertEmpty
import com.intellij.testFramework.UsefulTestCase.assertOneElement
import com.intellij.workspaceModel.storage.entities.*
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityStorageBuilderImpl
import com.intellij.workspaceModel.storage.impl.exceptions.PersistentIdAlreadyExistsException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class EntityWithPersistentIdInPStorageTest {

  private lateinit var builder: WorkspaceEntityStorageBuilderImpl

  @Before
  fun setUp() {
    builder = WorkspaceEntityStorageBuilderImpl.create()
  }

  @After
  fun tearDown() {
    builder.assertConsistency()
  }

  @Test
  fun `add remove entity`() {
    val foo = builder.addLinkedListEntity("foo", LinkedListEntityId("bar"))
    builder.assertConsistency()
    assertNull(foo.next.resolve(builder))
    val bar = builder.addLinkedListEntity("bar", LinkedListEntityId("baz"))
    builder.assertConsistency()
    assertEquals(bar, foo.next.resolve(builder))
    builder.removeEntity(bar)
    builder.assertConsistency()
    assertNull(foo.next.resolve(builder))
  }

  @Test
  fun `change target entity name`() {
    val foo = builder.addLinkedListEntity("foo", LinkedListEntityId("bar"))
    val bar = builder.addLinkedListEntity("bar", LinkedListEntityId("baz"))
    builder.assertConsistency()
    assertEquals(bar, foo.next.resolve(builder))
    builder.modifyEntity(ModifiableLinkedListEntity::class.java, bar) {
      name = "baz"
    }
    builder.assertConsistency()
    assertNull(foo.next.resolve(builder))
  }

  @Test
  fun `change name in reference`() {
    val foo = builder.addLinkedListEntity("foo", LinkedListEntityId("bar"))
    val bar = builder.addLinkedListEntity("bar", LinkedListEntityId("baz"))
    val baz = builder.addLinkedListEntity("baz", LinkedListEntityId("foo"))
    builder.assertConsistency()
    assertEquals(bar, foo.next.resolve(builder))
    val newFoo = builder.modifyEntity(ModifiableLinkedListEntity::class.java, foo) {
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
    assertEmpty(builder.entities(ChildEntity::class.java).toList())
  }

  @Test(expected = PersistentIdAlreadyExistsException::class)
  fun `add entity with existing persistent id`() {
    builder.addNamedEntity("MyName")
    builder.addNamedEntity("MyName")
  }

  @Test
  fun `add entity with existing persistent id - restoring after exception`() {
    try {
      builder.addNamedEntity("MyName")
      builder.addNamedEntity("MyName")
    }
    catch (e: PersistentIdAlreadyExistsException) {
      assertOneElement(builder.entities(NamedEntity::class.java).toList())
    }
  }

  @Test(expected = PersistentIdAlreadyExistsException::class)
  fun `modify entity to repeat persistent id`() {
    builder.addNamedEntity("MyName")
    val namedEntity = builder.addNamedEntity("AnotherId")
    builder.modifyEntity(ModifiableNamedEntity::class.java, namedEntity) {
      this.name = "MyName"
    }
  }

  @Test
  fun `modify entity to repeat persistent id - restoring after exception`() {
    try {
      builder.addNamedEntity("MyName")
      val namedEntity = builder.addNamedEntity("AnotherId")
      builder.modifyEntity(ModifiableNamedEntity::class.java, namedEntity) {
        this.name = "MyName"
      }
    }
    catch (e: PersistentIdAlreadyExistsException) {
      assertOneElement(builder.entities(NamedEntity::class.java).toList().filter { it.name == "MyName" })
      assertOneElement(builder.entities(NamedEntity::class.java).toList().filter { it.name == "AnotherId" })
    }
  }
}