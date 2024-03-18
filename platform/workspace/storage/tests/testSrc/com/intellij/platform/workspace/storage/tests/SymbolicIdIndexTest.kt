// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests

import com.intellij.platform.workspace.storage.testEntities.entities.SampleEntitySource
import com.intellij.platform.workspace.storage.testEntities.entities.SymbolicIdEntity
import com.intellij.platform.workspace.storage.testEntities.entities.SymbolicIdEntityImpl
import com.intellij.platform.workspace.storage.testEntities.entities.modifyEntity
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SymbolicIdIndexTest {
  @Test
  fun `base index test`() {
    val oldName = "oldName"
    val newName = "newName"
    val builder = createEmptyBuilder()
    val entity = builder addEntity SymbolicIdEntity(oldName, SampleEntitySource("test"))
    val symbolicId = builder.indexes.symbolicIdIndex.getEntryById((entity as SymbolicIdEntityImpl.Builder).id)
    assertEquals(oldName, symbolicId!!.presentableName)
    assertEquals(entity.id, builder.indexes.symbolicIdIndex.getIdsByEntry(symbolicId))

    val newEntity = builder.modifyEntity(entity) {
      data = newName
    } as SymbolicIdEntityImpl
    val newSymbolicId = builder.indexes.symbolicIdIndex.getEntryById(newEntity.id)
    assertEquals(newName, newSymbolicId!!.presentableName)
    assertEquals(entity.id, builder.indexes.symbolicIdIndex.getIdsByEntry(newSymbolicId))
    assertNull(builder.indexes.symbolicIdIndex.getIdsByEntry(symbolicId))

    builder.removeEntity(entity)
    assertNull(builder.indexes.symbolicIdIndex.getIdsByEntry(symbolicId))
    assertNull(builder.indexes.symbolicIdIndex.getIdsByEntry(newSymbolicId))
  }

  @Test
  fun `base index with diff test`() {
    val oldName = "oldName"
    val newName = "newName"
    val builder = createEmptyBuilder()
    val entity = builder addEntity SymbolicIdEntity(oldName, SampleEntitySource("test"))
    val symbolicId = builder.indexes.symbolicIdIndex.getEntryById((entity as SymbolicIdEntityImpl.Builder).id)
    assertEquals(oldName, symbolicId!!.presentableName)
    assertEquals(entity.id, builder.indexes.symbolicIdIndex.getIdsByEntry(symbolicId))

    val diff = createBuilderFrom(builder.toSnapshot())
    assertEquals(entity.id, diff.indexes.symbolicIdIndex.getIdsByEntry(symbolicId))

    val secondEntity = diff addEntity SymbolicIdEntity(newName, SampleEntitySource("test"))
    val secondSymbolicId = diff.indexes.symbolicIdIndex.getEntryById((secondEntity as SymbolicIdEntityImpl.Builder).id)
    assertEquals(newName, secondSymbolicId!!.presentableName)
    assertNull(builder.indexes.symbolicIdIndex.getIdsByEntry(secondSymbolicId))
    assertEquals(secondEntity.id, diff.indexes.symbolicIdIndex.getIdsByEntry(secondSymbolicId))

    builder.applyChangesFrom(diff)
    assertEquals(entity.id, builder.indexes.symbolicIdIndex.getIdsByEntry(symbolicId))
    assertEquals(secondEntity.id, builder.indexes.symbolicIdIndex.getIdsByEntry(secondSymbolicId))
  }

  @Test
  fun `remove from diff test`() {
    val oldName = "oldName"
    val builder = createEmptyBuilder()
    val entity = builder addEntity SymbolicIdEntity(oldName, SampleEntitySource("test"))
    val symbolicId = builder.indexes.symbolicIdIndex.getEntryById((entity as SymbolicIdEntityImpl.Builder).id)
    assertEquals(oldName, symbolicId!!.presentableName)
    assertEquals(entity.id, builder.indexes.symbolicIdIndex.getIdsByEntry(symbolicId))

    val diff = createBuilderFrom(builder.toSnapshot())
    assertEquals(entity.id, diff.indexes.symbolicIdIndex.getIdsByEntry(symbolicId))

    diff.removeEntity(entity.from(diff))
    assertEquals(entity.id, builder.indexes.symbolicIdIndex.getIdsByEntry(symbolicId))
    assertNull(diff.indexes.symbolicIdIndex.getIdsByEntry(symbolicId))

    builder.applyChangesFrom(diff)
    assertNull(builder.indexes.symbolicIdIndex.getIdsByEntry(symbolicId))
  }

  @Test
  fun `change source in diff test`() {
    val oldName = "oldName"
    val newName = "newName"
    val builder = createEmptyBuilder()
    val entity = builder addEntity SymbolicIdEntity(oldName, SampleEntitySource("test"))
    val symbolicId = builder.indexes.symbolicIdIndex.getEntryById((entity as SymbolicIdEntityImpl.Builder).id)
    assertEquals(oldName, symbolicId!!.presentableName)
    assertEquals(entity.id, builder.indexes.symbolicIdIndex.getIdsByEntry(symbolicId))

    val diff = createBuilderFrom(builder.toSnapshot())
    assertEquals(entity.id, diff.indexes.symbolicIdIndex.getIdsByEntry(symbolicId))

    val newEntity = diff.modifyEntity(entity.from(diff)) {
      data = newName
    }
    val newSymbolicId = diff.indexes.symbolicIdIndex.getEntryById((newEntity as SymbolicIdEntityImpl).id)
    assertEquals(newName, newSymbolicId!!.presentableName)
    assertEquals(newEntity.id, diff.indexes.symbolicIdIndex.getIdsByEntry(newSymbolicId))
    assertEquals(entity.id, builder.indexes.symbolicIdIndex.getIdsByEntry(symbolicId))

    builder.applyChangesFrom(diff)
    assertNull(builder.indexes.symbolicIdIndex.getIdsByEntry(symbolicId))
    assertEquals(newEntity.id, builder.indexes.symbolicIdIndex.getIdsByEntry(newSymbolicId))
  }
}