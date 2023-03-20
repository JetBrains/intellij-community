// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage

import com.intellij.workspaceModel.storage.entities.test.addSymbolicIdEntity
import com.intellij.workspaceModel.storage.entities.test.api.SymbolicIdEntityImpl
import com.intellij.workspaceModel.storage.entities.test.api.modifyEntity
import org.junit.Assert
import org.junit.Test

class SymbolicIdIndexTest {
  @Test
  fun `base index test`() {
    val oldName = "oldName"
    val newName = "newName"
    val builder = createEmptyBuilder()
    val entity = builder.addSymbolicIdEntity(oldName)
    val symbolicId = builder.indexes.symbolicIdIndex.getEntryById((entity as SymbolicIdEntityImpl.Builder).id)
    Assert.assertEquals(oldName, symbolicId!!.presentableName)
    Assert.assertEquals(entity.id, builder.indexes.symbolicIdIndex.getIdsByEntry(symbolicId))

    val newEntity = builder.modifyEntity(entity) {
      data = newName
    } as SymbolicIdEntityImpl
    val newSymbolicId = builder.indexes.symbolicIdIndex.getEntryById(newEntity.id)
    Assert.assertEquals(newName, newSymbolicId!!.presentableName)
    Assert.assertEquals(entity.id, builder.indexes.symbolicIdIndex.getIdsByEntry(newSymbolicId))
    Assert.assertNull(builder.indexes.symbolicIdIndex.getIdsByEntry(symbolicId))

    builder.removeEntity(entity)
    Assert.assertNull(builder.indexes.symbolicIdIndex.getIdsByEntry(symbolicId))
    Assert.assertNull(builder.indexes.symbolicIdIndex.getIdsByEntry(newSymbolicId))
  }

  @Test
  fun `base index with diff test`() {
    val oldName = "oldName"
    val newName = "newName"
    val builder = createEmptyBuilder()
    val entity = builder.addSymbolicIdEntity(oldName)
    val symbolicId = builder.indexes.symbolicIdIndex.getEntryById((entity as SymbolicIdEntityImpl.Builder).id)
    Assert.assertEquals(oldName, symbolicId!!.presentableName)
    Assert.assertEquals(entity.id, builder.indexes.symbolicIdIndex.getIdsByEntry(symbolicId))

    val diff = createBuilderFrom(builder.toSnapshot())
    Assert.assertEquals(entity.id, diff.indexes.symbolicIdIndex.getIdsByEntry(symbolicId))

    val secondEntity = diff.addSymbolicIdEntity(newName)
    val secondSymbolicId = diff.indexes.symbolicIdIndex.getEntryById((secondEntity as SymbolicIdEntityImpl.Builder).id)
    Assert.assertEquals(newName, secondSymbolicId!!.presentableName)
    Assert.assertNull(builder.indexes.symbolicIdIndex.getIdsByEntry(secondSymbolicId))
    Assert.assertEquals(secondEntity.id, diff.indexes.symbolicIdIndex.getIdsByEntry(secondSymbolicId))

    builder.addDiff(diff)
    Assert.assertEquals(entity.id, builder.indexes.symbolicIdIndex.getIdsByEntry(symbolicId))
    Assert.assertEquals(secondEntity.id, builder.indexes.symbolicIdIndex.getIdsByEntry(secondSymbolicId))
  }

  @Test
  fun `remove from diff test`() {
    val oldName = "oldName"
    val builder = createEmptyBuilder()
    val entity = builder.addSymbolicIdEntity(oldName)
    val symbolicId = builder.indexes.symbolicIdIndex.getEntryById((entity as SymbolicIdEntityImpl.Builder).id)
    Assert.assertEquals(oldName, symbolicId!!.presentableName)
    Assert.assertEquals(entity.id, builder.indexes.symbolicIdIndex.getIdsByEntry(symbolicId))

    val diff = createBuilderFrom(builder.toSnapshot())
    Assert.assertEquals(entity.id, diff.indexes.symbolicIdIndex.getIdsByEntry(symbolicId))

    diff.removeEntity(entity.from(diff))
    Assert.assertEquals(entity.id, builder.indexes.symbolicIdIndex.getIdsByEntry(symbolicId))
    Assert.assertNull(diff.indexes.symbolicIdIndex.getIdsByEntry(symbolicId))

    builder.addDiff(diff)
    Assert.assertNull(builder.indexes.symbolicIdIndex.getIdsByEntry(symbolicId))
  }

  @Test
  fun `change source in diff test`() {
    val oldName = "oldName"
    val newName = "newName"
    val builder = createEmptyBuilder()
    val entity = builder.addSymbolicIdEntity(oldName)
    val symbolicId = builder.indexes.symbolicIdIndex.getEntryById((entity as SymbolicIdEntityImpl.Builder).id)
    Assert.assertEquals(oldName, symbolicId!!.presentableName)
    Assert.assertEquals(entity.id, builder.indexes.symbolicIdIndex.getIdsByEntry(symbolicId))

    val diff = createBuilderFrom(builder.toSnapshot())
    Assert.assertEquals(entity.id, diff.indexes.symbolicIdIndex.getIdsByEntry(symbolicId))

    val newEntity = diff.modifyEntity(entity.from(diff)) {
      data = newName
    }
    val newSymbolicId = diff.indexes.symbolicIdIndex.getEntryById((newEntity as SymbolicIdEntityImpl).id)
    Assert.assertEquals(newName, newSymbolicId!!.presentableName)
    Assert.assertEquals(newEntity.id, diff.indexes.symbolicIdIndex.getIdsByEntry(newSymbolicId))
    Assert.assertEquals(entity.id, builder.indexes.symbolicIdIndex.getIdsByEntry(symbolicId))

    builder.addDiff(diff)
    Assert.assertNull(builder.indexes.symbolicIdIndex.getIdsByEntry(symbolicId))
    Assert.assertEquals(newEntity.id, builder.indexes.symbolicIdIndex.getIdsByEntry(newSymbolicId))
  }
}