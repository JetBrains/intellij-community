// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage

import com.intellij.testFramework.UsefulTestCase.assertEmpty
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityStorageBuilderImpl
import com.intellij.workspaceModel.storage.entities.*
import com.intellij.workspaceModel.storage.entities.LinkedListEntityId
import com.intellij.workspaceModel.storage.entities.ModifiableLinkedListEntity
import com.intellij.workspaceModel.storage.entities.ChildEntity
import com.intellij.workspaceModel.storage.entities.addLinkedListEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EntityWithPersistentIdInPStorageTest {
  @Test
  fun `add remove entity`() {
    val builder = WorkspaceEntityStorageBuilderImpl.create()
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
    val builder = WorkspaceEntityStorageBuilderImpl.create()
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
    val builder = WorkspaceEntityStorageBuilderImpl.create()
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
    val builder = WorkspaceEntityStorageBuilderImpl.create()
    val parent = builder.addParentEntity("parent")
    builder.addChildEntity(parent)
    builder.assertConsistency()
    builder.removeEntity(parent)
    builder.assertConsistency()
    assertEmpty(builder.entities(ChildEntity::class.java).toList())
  }
}