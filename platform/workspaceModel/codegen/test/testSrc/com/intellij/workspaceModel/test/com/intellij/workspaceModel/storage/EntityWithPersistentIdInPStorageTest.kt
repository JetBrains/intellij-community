// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage

import com.intellij.workspaceModel.codegen.storage.impl.WorkspaceEntityStorageBuilderImpl
import com.intellij.workspaceModel.storage.impl.assertConsistency
import com.intellij.workspaceModel.storage.impl.exceptions.PersistentIdAlreadyExistsException
import org.jetbrains.deft.IntellijWsTestIj.modifyEntity
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EntityWithPersistentIdInPStorageTest {

  private lateinit var builder: WorkspaceEntityStorageBuilder
  val builderI: WorkspaceEntityStorageBuilder
    get() = builder

  @BeforeEach
  fun setUp() {
    builder = createEmptyBuilder()
  }

  @Test
  fun `add remove entity`() {
    val foo = builder.addLinkedListEntity("foo", LinkedListEntityId("bar"))
    (builder as WorkspaceEntityStorageBuilderImpl).assertConsistency()
    assertNull(foo.next.resolve(builder))
    assertNull(foo.next.resolve(builder.toStorage()))
    val bar = builder.addLinkedListEntity("bar", LinkedListEntityId("baz"))
    (builder as WorkspaceEntityStorageBuilderImpl).assertConsistency()
    assertEquals(bar, foo.next.resolve(builder))
    assertEquals(bar, foo.next.resolve(builder.toStorage()))
    builder.removeEntity(bar)
    (builder as WorkspaceEntityStorageBuilderImpl).assertConsistency()
    assertNull(foo.next.resolve(builder))
  }

  @Test
  fun `change target entity name`() {
    builder.addLinkedListEntity("foo", LinkedListEntityId("bar"))
    val foo = builder.entities(LinkedListEntity::class.java).single { it.myName == "foo" }
    val bar = builder.addLinkedListEntity("bar", LinkedListEntityId("baz"))
    (builder as WorkspaceEntityStorageBuilderImpl).assertConsistency()
    assertEquals(bar, foo.next.resolve(builder))
    builderI.modifyEntity(bar) {
      myName = "baz"
    }
    (builder as WorkspaceEntityStorageBuilderImpl).assertConsistency()
    assertEquals("baz", bar.myName)
    assertEquals(bar, foo.next.resolve(builder))
  }

  @Test
  fun `change name in reference`() {
    val foo = builder.addLinkedListEntity("foo", LinkedListEntityId("bar"))
    val bar = builder.addLinkedListEntity("bar", LinkedListEntityId("baz"))
    val baz = builder.addLinkedListEntity("baz", LinkedListEntityId("foo"))
    (builder as WorkspaceEntityStorageBuilderImpl).assertConsistency()
    assertEquals(bar, foo.next.resolve(builder))
    val newFoo = builderI.modifyEntity(foo) {
      next = LinkedListEntityId("baz")
    }
    (builder as WorkspaceEntityStorageBuilderImpl).assertConsistency()
    assertEquals(baz, newFoo.next.resolve(builder))
  }

  @Test
  fun `remove child entity with parent entity`() {
    val parent = builder.addParentEntity("parent")
    builder.addChildEntity(parent)
    (builder as WorkspaceEntityStorageBuilderImpl).assertConsistency()
    builder.removeEntity(parent)
    (builder as WorkspaceEntityStorageBuilderImpl).assertConsistency()
    assertTrue(builder.entities(ChildEntity::class.java).toList().isEmpty())
  }

  @Test
  fun `add entity with existing persistent id`() {
    builder = WorkspaceEntityStorageBuilderImpl.create().also { it.throwExceptionOnError = true }
    assertThrows<PersistentIdAlreadyExistsException> {
      builder.addNamedEntity("MyName")
      builder.addNamedEntity("MyName")
    }
  }

  @Test
  fun `add entity with existing persistent id - restoring after exception`() {
    builder = WorkspaceEntityStorageBuilderImpl.create()
    try {
      builder.addNamedEntity("MyName")
      builder.addNamedEntity("MyName")
    }
    catch (e: Throwable) {
      assert(e.cause is PersistentIdAlreadyExistsException)
      assertOneElement(builder.entities(NamedEntity::class.java).toList())
    }
  }

  @Test
  fun `modify entity to repeat persistent id`() {
    builder = WorkspaceEntityStorageBuilderImpl.create().also { it.throwExceptionOnError = true }
    assertThrows<PersistentIdAlreadyExistsException> {
      builder.addNamedEntity("MyName")
      val namedEntity = builder.addNamedEntity("AnotherId")
      builderI.modifyEntity(namedEntity) {
        this.myName = "MyName"
      }
    }
  }

  @Test
  fun `modify entity to repeat persistent id - restoring after exception`() {
    builder = WorkspaceEntityStorageBuilderImpl.create()
    try {
      builder.addNamedEntity("MyName")
      val namedEntity = builder.addNamedEntity("AnotherId")
      builderI.modifyEntity(namedEntity) {
        this.myName = "MyName"
      }
    }
    catch (e: AssertionError) {
      assert(e.cause is PersistentIdAlreadyExistsException)
      assertOneElement(builder.entities(NamedEntity::class.java).toList().filter { it.myName == "MyName" })
    }
  }
}