// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests.impl

import com.intellij.platform.workspace.storage.entities
import com.intellij.platform.workspace.storage.impl.ChangeEntry
import com.intellij.platform.workspace.storage.impl.EntityId
import com.intellij.platform.workspace.storage.impl.MutableEntityStorageImpl
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.testEntities.entities.ChildSubEntity
import com.intellij.platform.workspace.storage.testEntities.entities.ChildSubSubEntity
import com.intellij.platform.workspace.storage.testEntities.entities.MySource
import com.intellij.platform.workspace.storage.testEntities.entities.ParentSubEntity
import com.intellij.platform.workspace.storage.tests.createEmptyBuilder
import com.intellij.platform.workspace.storage.toBuilder
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RemovingSingleEntityImplTest {
  @Test
  fun `remove single entity and update both references`() {
    val (childSubEntity, builder) = setupBuilder()

    builder.removeSingleEntity(childSubEntity, true, true)

    val parentSubEntities = builder.entities<ParentSubEntity>().toList()
    val childSubSubEntities = builder.entities<ChildSubSubEntity>().toList()

    assertTrue(parentSubEntities.isNotEmpty())
    assertTrue(childSubSubEntities.isNotEmpty())

    assertEquals(3, builder.changeLog.changeLog.size)
    assertIs<ChangeEntry.RemoveEntity>(builder.changeLog.changeLog[childSubEntity])
    assertIs<ChangeEntry.ReplaceEntity>(builder.changeLog.changeLog[(parentSubEntities.single() as WorkspaceEntityBase).id])
    assertIs<ChangeEntry.ReplaceEntity>(builder.changeLog.changeLog[(childSubSubEntities.single() as WorkspaceEntityBase).id])
  }

  @Test
  fun `remove single entity and update only parent references`() {
    val (childSubEntity, builder) = setupBuilder()

    builder.removeSingleEntity(childSubEntity, false, true)

    val parentSubEntities = builder.entities<ParentSubEntity>().toList()
    val childSubSubEntities = builder.entities<ChildSubSubEntity>().toList()

    assertTrue(parentSubEntities.isNotEmpty())
    assertTrue(childSubSubEntities.isNotEmpty())

    assertEquals(2, builder.changeLog.changeLog.size)
    assertIs<ChangeEntry.RemoveEntity>(builder.changeLog.changeLog[childSubEntity])
    assertIs<ChangeEntry.ReplaceEntity>(builder.changeLog.changeLog[(parentSubEntities.single() as WorkspaceEntityBase).id])
  }

  @Test
  fun `remove single entity and update only child references`() {
    val (childSubEntity, builder) = setupBuilder()

    builder.removeSingleEntity(childSubEntity, true, false)

    val parentSubEntities = builder.entities<ParentSubEntity>().toList()
    val childSubSubEntities = builder.entities<ChildSubSubEntity>().toList()

    assertTrue(parentSubEntities.isNotEmpty())
    assertTrue(childSubSubEntities.isNotEmpty())

    assertEquals(2, builder.changeLog.changeLog.size)
    assertIs<ChangeEntry.RemoveEntity>(builder.changeLog.changeLog[childSubEntity])
    assertIs<ChangeEntry.ReplaceEntity>(builder.changeLog.changeLog[(childSubSubEntities.single() as WorkspaceEntityBase).id])
  }

  private fun setupBuilder(): Pair<EntityId, MutableEntityStorageImpl> {
    val builder = createEmptyBuilder()

    builder addEntity ParentSubEntity("Data", MySource) {
      this.child = ChildSubEntity(MySource) {
        this.child = ChildSubSubEntity("Data", MySource)
      }
    }
    val builder2 = builder.toSnapshot().toBuilder() as MutableEntityStorageImpl

    val childSubEntity = builder2.entities<ChildSubEntity>().single() as WorkspaceEntityBase
    return childSubEntity.id to builder2
  }
}