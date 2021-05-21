// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage

import com.intellij.workspaceModel.storage.entities.ModifiablePersistentIdEntity
import com.intellij.workspaceModel.storage.entities.addPersistentIdEntity
import org.junit.Assert
import org.junit.Test

class PersistentIdIndexTest {
  @Test
  fun `base index test`() {
    val oldName = "oldName"
    val newName = "newName"
    val builder = createEmptyBuilder()
    val entity = builder.addPersistentIdEntity(oldName)
    val persistentId = builder.indexes.persistentIdIndex.getEntryById(entity.id)
    Assert.assertEquals(oldName, persistentId!!.presentableName)
    Assert.assertEquals(entity.id, builder.indexes.persistentIdIndex.getIdsByEntry(persistentId)?.get(0))

    val newEntity = builder.modifyEntity(ModifiablePersistentIdEntity::class.java, entity) {
      data = newName
    }
    val newPersistentId = builder.indexes.persistentIdIndex.getEntryById(newEntity.id)
    Assert.assertEquals(newName, newPersistentId!!.presentableName)
    Assert.assertEquals(entity.id, builder.indexes.persistentIdIndex.getIdsByEntry(newPersistentId)?.get(0))
    Assert.assertNull(builder.indexes.persistentIdIndex.getIdsByEntry(persistentId))

    builder.removeEntity(entity)
    Assert.assertNull(builder.indexes.persistentIdIndex.getIdsByEntry(persistentId))
    Assert.assertNull(builder.indexes.persistentIdIndex.getIdsByEntry(newPersistentId))
  }

  @Test
  fun `base index with diff test`() {
    val oldName = "oldName"
    val newName = "newName"
    val builder = createEmptyBuilder()
    val entity = builder.addPersistentIdEntity(oldName)
    val persistentId = builder.indexes.persistentIdIndex.getEntryById(entity.id)
    Assert.assertEquals(oldName, persistentId!!.presentableName)
    Assert.assertEquals(entity.id, builder.indexes.persistentIdIndex.getIdsByEntry(persistentId)?.get(0))

    val diff = createBuilderFrom(builder.toStorage())
    Assert.assertEquals(entity.id, diff.indexes.persistentIdIndex.getIdsByEntry(persistentId)?.get(0))

    val secondEntity = diff.addPersistentIdEntity(newName)
    val secondPersistentId = diff.indexes.persistentIdIndex.getEntryById(secondEntity.id)
    Assert.assertEquals(newName, secondPersistentId!!.presentableName)
    Assert.assertNull(builder.indexes.persistentIdIndex.getIdsByEntry(secondPersistentId)?.get(0))
    Assert.assertEquals(secondEntity.id, diff.indexes.persistentIdIndex.getIdsByEntry(secondPersistentId)?.get(0))

    builder.addDiff(diff)
    Assert.assertEquals(entity.id, builder.indexes.persistentIdIndex.getIdsByEntry(persistentId)?.get(0))
    Assert.assertEquals(secondEntity.id, builder.indexes.persistentIdIndex.getIdsByEntry(secondPersistentId)?.get(0))
  }

  @Test
  fun `remove from diff test`() {
    val oldName = "oldName"
    val builder = createEmptyBuilder()
    val entity = builder.addPersistentIdEntity(oldName)
    val persistentId = builder.indexes.persistentIdIndex.getEntryById(entity.id)
    Assert.assertEquals(oldName, persistentId!!.presentableName)
    Assert.assertEquals(entity.id, builder.indexes.persistentIdIndex.getIdsByEntry(persistentId)?.get(0))

    val diff = createBuilderFrom(builder.toStorage())
    Assert.assertEquals(entity.id, diff.indexes.persistentIdIndex.getIdsByEntry(persistentId)?.get(0))

    diff.removeEntity(entity)
    Assert.assertEquals(entity.id, builder.indexes.persistentIdIndex.getIdsByEntry(persistentId)?.get(0))
    Assert.assertNull(diff.indexes.persistentIdIndex.getIdsByEntry(persistentId)?.get(0))

    builder.addDiff(diff)
    Assert.assertNull(builder.indexes.persistentIdIndex.getIdsByEntry(persistentId))
  }

  @Test
  fun `change source in diff test`() {
    val oldName = "oldName"
    val newName = "newName"
    val builder = createEmptyBuilder()
    val entity = builder.addPersistentIdEntity(oldName)
    val persistentId = builder.indexes.persistentIdIndex.getEntryById(entity.id)
    Assert.assertEquals(oldName, persistentId!!.presentableName)
    Assert.assertEquals(entity.id, builder.indexes.persistentIdIndex.getIdsByEntry(persistentId)?.get(0))

    val diff = createBuilderFrom(builder.toStorage())
    Assert.assertEquals(entity.id, diff.indexes.persistentIdIndex.getIdsByEntry(persistentId)?.get(0))

    val newEntity = diff.modifyEntity(ModifiablePersistentIdEntity::class.java, entity) {
      data = newName
    }
    val newPersistentId = diff.indexes.persistentIdIndex.getEntryById(newEntity.id)
    Assert.assertEquals(newName, newPersistentId!!.presentableName)
    Assert.assertEquals(newEntity.id, diff.indexes.persistentIdIndex.getIdsByEntry(newPersistentId)?.get(0))
    Assert.assertEquals(entity.id, builder.indexes.persistentIdIndex.getIdsByEntry(persistentId)?.get(0))

    builder.addDiff(diff)
    Assert.assertNull(builder.indexes.persistentIdIndex.getIdsByEntry(persistentId))
    Assert.assertEquals(newEntity.id, builder.indexes.persistentIdIndex.getIdsByEntry(newPersistentId)?.get(0))
  }
}