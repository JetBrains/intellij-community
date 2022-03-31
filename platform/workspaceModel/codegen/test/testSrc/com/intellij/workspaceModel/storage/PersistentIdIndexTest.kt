// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage

import org.jetbrains.deft.IntellijWsTestIj.modifyEntity
import com.intellij.workspaceModel.storage.entities.PersistentIdEntityImpl
import com.intellij.workspaceModel.storage.entities.addPersistentIdEntity
import com.intellij.workspaceModel.codegen.storage.impl.WorkspaceEntityStorageBuilderImpl
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PersistentIdIndexTest {
  @Test
  fun `base index test`() {
    val oldName = "oldName"
    val newName = "newName"
    val builder = createEmptyBuilder()
    val entity = builder.addPersistentIdEntity(oldName) as PersistentIdEntityImpl.Builder
    val persistentId = (builder as WorkspaceEntityStorageBuilderImpl).indexes.persistentIdIndex.getEntryById(entity.id)
    assertEquals(oldName, persistentId!!.presentableName)
    assertEquals(entity.id, (builder as WorkspaceEntityStorageBuilderImpl).indexes.persistentIdIndex.getIdsByEntry(persistentId))

    val newEntity = builder.modifyEntity(entity) {
      data = newName
    } as PersistentIdEntityImpl
    val newPersistentId = (builder as WorkspaceEntityStorageBuilderImpl).indexes.persistentIdIndex.getEntryById(newEntity.id)
    assertEquals(newName, newPersistentId!!.presentableName)
    assertEquals(entity.id, (builder as WorkspaceEntityStorageBuilderImpl).indexes.persistentIdIndex.getIdsByEntry(newPersistentId))
    assertNull((builder as WorkspaceEntityStorageBuilderImpl).indexes.persistentIdIndex.getIdsByEntry(persistentId))

    builder.removeEntity(entity)
    assertNull((builder as WorkspaceEntityStorageBuilderImpl).indexes.persistentIdIndex.getIdsByEntry(persistentId))
    assertNull((builder as WorkspaceEntityStorageBuilderImpl).indexes.persistentIdIndex.getIdsByEntry(newPersistentId))
  }

  @Test
  fun `base index with diff test`() {
    val oldName = "oldName"
    val newName = "newName"
    val builder = createEmptyBuilder()
    val entity = builder.addPersistentIdEntity(oldName) as PersistentIdEntityImpl.Builder
    val persistentId = (builder as WorkspaceEntityStorageBuilderImpl).indexes.persistentIdIndex.getEntryById(entity.id)
    assertEquals(oldName, persistentId!!.presentableName)
    assertEquals(entity.id, (builder as WorkspaceEntityStorageBuilderImpl).indexes.persistentIdIndex.getIdsByEntry(persistentId))

    val diff = createBuilderFrom(builder.toStorage()) as WorkspaceEntityStorageBuilderImpl
    assertEquals(entity.id, diff.indexes.persistentIdIndex.getIdsByEntry(persistentId))

    val secondEntity = diff.addPersistentIdEntity(newName) as PersistentIdEntityImpl.Builder
    val secondPersistentId = diff.indexes.persistentIdIndex.getEntryById(secondEntity.id)
    assertEquals(newName, secondPersistentId!!.presentableName)
    assertNull((builder as WorkspaceEntityStorageBuilderImpl).indexes.persistentIdIndex.getIdsByEntry(secondPersistentId))
    assertEquals(secondEntity.id, diff.indexes.persistentIdIndex.getIdsByEntry(secondPersistentId))

    builder.addDiff(diff)
    assertEquals(entity.id, (builder as WorkspaceEntityStorageBuilderImpl).indexes.persistentIdIndex.getIdsByEntry(persistentId))
    assertEquals(secondEntity.id, (builder as WorkspaceEntityStorageBuilderImpl).indexes.persistentIdIndex.getIdsByEntry(secondPersistentId))
  }

  @Test
  fun `remove from diff test`() {
    val oldName = "oldName"
    val builder = createEmptyBuilder()
    val entity = builder.addPersistentIdEntity(oldName) as PersistentIdEntityImpl.Builder
    val persistentId = (builder as WorkspaceEntityStorageBuilderImpl).indexes.persistentIdIndex.getEntryById(entity.id)
    assertEquals(oldName, persistentId!!.presentableName)
    assertEquals(entity.id, (builder as WorkspaceEntityStorageBuilderImpl).indexes.persistentIdIndex.getIdsByEntry(persistentId))

    val diff = createBuilderFrom(builder.toStorage()) as WorkspaceEntityStorageBuilderImpl
    assertEquals(entity.id, diff.indexes.persistentIdIndex.getIdsByEntry(persistentId))

    diff.removeEntity(entity)
    assertEquals(entity.id, (builder as WorkspaceEntityStorageBuilderImpl).indexes.persistentIdIndex.getIdsByEntry(persistentId))
    assertNull(diff.indexes.persistentIdIndex.getIdsByEntry(persistentId))

    builder.addDiff(diff)
    assertNull((builder as WorkspaceEntityStorageBuilderImpl).indexes.persistentIdIndex.getIdsByEntry(persistentId))
  }

  @Test
  fun `change source in diff test`() {
    val oldName = "oldName"
    val newName = "newName"
    val builder = createEmptyBuilder()
    val entity = builder.addPersistentIdEntity(oldName) as PersistentIdEntityImpl.Builder
    val persistentId = (builder as WorkspaceEntityStorageBuilderImpl).indexes.persistentIdIndex.getEntryById(entity.id)
    assertEquals(oldName, persistentId!!.presentableName)
    assertEquals(entity.id, (builder as WorkspaceEntityStorageBuilderImpl).indexes.persistentIdIndex.getIdsByEntry(persistentId))

    val diff = createBuilderFrom(builder.toStorage()) as WorkspaceEntityStorageBuilderImpl
    assertEquals(entity.id, diff.indexes.persistentIdIndex.getIdsByEntry(persistentId))

    val newEntity = diff.modifyEntity(entity) {
      data = newName
    } as PersistentIdEntityImpl
    val newPersistentId = diff.indexes.persistentIdIndex.getEntryById(newEntity.id)
    assertEquals(newName, newPersistentId!!.presentableName)
    assertEquals(newEntity.id, diff.indexes.persistentIdIndex.getIdsByEntry(newPersistentId))
    assertEquals(entity.id, (builder as WorkspaceEntityStorageBuilderImpl).indexes.persistentIdIndex.getIdsByEntry(persistentId))

    builder.addDiff(diff)
    assertNull((builder as WorkspaceEntityStorageBuilderImpl).indexes.persistentIdIndex.getIdsByEntry(persistentId))
    assertEquals(newEntity.id, (builder as WorkspaceEntityStorageBuilderImpl).indexes.persistentIdIndex.getIdsByEntry(newPersistentId))
  }
}