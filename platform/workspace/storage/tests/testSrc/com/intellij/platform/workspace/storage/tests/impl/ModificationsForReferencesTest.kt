// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests.impl

import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.impl.MutableRefsTable
import com.intellij.platform.workspace.storage.impl.RefsTable
import com.intellij.platform.workspace.storage.impl.asChild
import com.intellij.platform.workspace.storage.impl.asParent
import com.intellij.platform.workspace.storage.impl.createEntityId
import com.intellij.platform.workspace.storage.instrumentation.Modification
import com.intellij.platform.workspace.storage.testEntities.entities.ChildEntity
import com.intellij.platform.workspace.storage.testEntities.entities.ParentEntity
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ModificationsForReferencesTest {
  @Test
  fun `replace one to one child to itself produces no events`() {
    val refsTable = MutableRefsTable.from(RefsTable())
    val connection = ConnectionId.create(ParentEntity::class.java, ChildEntity::class.java,
                        ConnectionId.ConnectionType.ONE_TO_ONE, false)
    val parentId = createEntityId(0, 0)
    val childId = createEntityId(0, 1)
    val modifications = refsTable.replaceOneToOneChildOfParent(connection, parentId, childId.asChild())

    assertEquals(1, modifications.size)
    assertIs<Modification.Add>(modifications.single())

    val modifications2 = refsTable.replaceOneToOneChildOfParent(connection, parentId, childId.asChild())
    assertEquals(0, modifications2.size)
  }

  @Test
  fun `replace one to one parent to itself produces no events`() {
    val refsTable = MutableRefsTable.from(RefsTable())
    val connection = ConnectionId.create(ParentEntity::class.java, ChildEntity::class.java,
                        ConnectionId.ConnectionType.ONE_TO_ONE, false)
    val parentId = createEntityId(0, 0)
    val childId = createEntityId(0, 1)
    val modifications = refsTable.replaceOneToOneParentOfChild(connection, parentId, childId)

    assertEquals(1, modifications.size)
    assertIs<Modification.Add>(modifications.single())

    val modifications2 = refsTable.replaceOneToOneParentOfChild(connection, parentId, childId)
    assertEquals(0, modifications2.size)
  }

  @Test
  fun `replace one to abstract one child to itself produces no events`() {
    val refsTable = MutableRefsTable.from(RefsTable())
    val connection = ConnectionId.create(ParentEntity::class.java, ChildEntity::class.java,
                        ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE, false)
    val parentId = createEntityId(0, 0)
    val childId = createEntityId(0, 1)
    val modifications = refsTable.replaceOneToAbstractOneChildOfParent(connection, parentId.asParent(), childId.asChild())

    assertEquals(1, modifications.size)
    assertIs<Modification.Add>(modifications.single())

    val modifications2 = refsTable.replaceOneToAbstractOneChildOfParent(connection, parentId.asParent(), childId.asChild())
    assertEquals(0, modifications2.size)
  }

  @Test
  fun `replace one to abstract one parent to itself produces no events`() {
    val refsTable = MutableRefsTable.from(RefsTable())
    val connection = ConnectionId.create(ParentEntity::class.java, ChildEntity::class.java,
                        ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE, false)
    val parentId = createEntityId(0, 0)
    val childId = createEntityId(0, 1)
    val modifications = refsTable.replaceOneToAbstractOneParentOfChild(connection, childId.asChild(), parentId.asParent())

    assertEquals(1, modifications.size)
    assertIs<Modification.Add>(modifications.single())

    val modifications2 = refsTable.replaceOneToAbstractOneParentOfChild(connection, childId.asChild(), parentId.asParent())
    assertEquals(0, modifications2.size)
  }

  @Test
  fun `replace one to many children to themselves produces no events`() {
    val refsTable = MutableRefsTable.from(RefsTable())
    val connection = ConnectionId.create(ParentEntity::class.java, ChildEntity::class.java,
                                         ConnectionId.ConnectionType.ONE_TO_MANY, false)
    val parentId = createEntityId(0, 0)
    val childId = createEntityId(0, 1)
    val modifications = refsTable.replaceOneToManyChildrenOfParent(connection, parentId, listOf(childId.asChild()))

    assertEquals(1, modifications.size)
    assertIs<Modification.Add>(modifications.single())

    val modifications2 = refsTable.replaceOneToManyChildrenOfParent(connection, parentId, listOf(childId.asChild()))
    assertEquals(0, modifications2.size)
  }

  @Test
  fun `replace one to many parent to itself produces no events`() {
    val refsTable = MutableRefsTable.from(RefsTable())
    val connection = ConnectionId.create(ParentEntity::class.java, ChildEntity::class.java,
                                         ConnectionId.ConnectionType.ONE_TO_MANY, false)
    val parentId = createEntityId(0, 0)
    val childId = createEntityId(0, 1)
    val modifications = refsTable.replaceOneToManyParentOfChild(connection, childId, parentId.asParent())

    assertEquals(1, modifications.size)
    assertIs<Modification.Add>(modifications.single())

    val modifications2 = refsTable.replaceOneToManyParentOfChild(connection, childId, parentId.asParent())
    assertEquals(0, modifications2.size)
  }

  @Test
  fun `replace one to abstract many children to themselves produces no events`() {
    val refsTable = MutableRefsTable.from(RefsTable())
    val connection = ConnectionId.create(ParentEntity::class.java, ChildEntity::class.java,
                                         ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY, false)
    val parentId = createEntityId(0, 0)
    val childId = createEntityId(0, 1)
    val modifications = refsTable.replaceOneToAbstractManyChildrenOfParent(connection, parentId.asParent(), listOf(childId.asChild()))

    assertEquals(1, modifications.size)
    assertIs<Modification.Add>(modifications.single())

    val modifications2 = refsTable.replaceOneToAbstractManyChildrenOfParent(connection, parentId.asParent(), listOf(childId.asChild()))
    assertEquals(0, modifications2.size)
  }

  @Test
  fun `replace one to abstract many parent to itself produces no events`() {
    val refsTable = MutableRefsTable.from(RefsTable())
    val connection = ConnectionId.create(ParentEntity::class.java, ChildEntity::class.java,
                                         ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY, false)
    val parentId = createEntityId(0, 0)
    val childId = createEntityId(0, 1)
    val modifications = refsTable.replaceOneToAbstractManyParentOfChild(connection, childId.asChild(), parentId.asParent())

    assertEquals(1, modifications.size)
    assertIs<Modification.Add>(modifications.single())

    val modifications2 = refsTable.replaceOneToAbstractManyParentOfChild(connection, childId.asChild(), parentId.asParent())
    assertEquals(0, modifications2.size)
  }
}