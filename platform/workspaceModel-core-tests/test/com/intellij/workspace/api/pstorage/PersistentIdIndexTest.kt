// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage

import com.intellij.workspace.api.EntitySource
import com.intellij.workspace.api.TypedEntityStorage
import com.intellij.workspace.api.TypedEntityStorageBuilder
import com.intellij.workspace.api.TypedEntityWithPersistentId
import org.junit.Assert
import org.junit.Test

internal class PPersistentIdEntityData : PEntityData.WithCalculatablePersistentId<PPersistentIdEntity>() {
  lateinit var data: String
  override fun createEntity(snapshot: TypedEntityStorage): PPersistentIdEntity {
    return PPersistentIdEntity(data).also { addMetaData(it, snapshot) }
  }

  override fun persistentId(): PSampleEntityId = PSampleEntityId(data)
}

internal class PPersistentIdEntity(val data: String) : TypedEntityWithPersistentId, PTypedEntity() {
  override fun persistentId(): PSampleEntityId = PSampleEntityId(data)
}

internal class ModifiablePPersistentIdEntity : PModifiableTypedEntity<PPersistentIdEntity>() {
  var data: String by EntityDataDelegation()
}

internal fun TypedEntityStorageBuilder.addPersistentIdEntity(data: String,
                                                             source: EntitySource = PSampleEntitySource("test")): PPersistentIdEntity {
  return addEntity(ModifiablePPersistentIdEntity::class.java, source) {
    this.data = data
  }
}

class PersistentIdIndexTest {
  @Test
  fun `base index test`() {
    val oldName = "oldName"
    val newName = "newName"
    val builder = PEntityStorageBuilder.create()
    val entity = builder.addPersistentIdEntity(oldName)
    val persistentId = builder.persistentIdIndex.getEntryById(entity.id)
    Assert.assertEquals(oldName, persistentId!!.presentableName)
    Assert.assertEquals(entity.id, builder.persistentIdIndex.getIdsByEntry(persistentId)?.get(0))

    val newEntity = builder.modifyEntity(ModifiablePPersistentIdEntity::class.java, entity) {
      data = newName
    }
    val newPersistentId = builder.persistentIdIndex.getEntryById(newEntity.id)
    Assert.assertEquals(newName, newPersistentId!!.presentableName)
    Assert.assertEquals(entity.id, builder.persistentIdIndex.getIdsByEntry(newPersistentId)?.get(0))
    Assert.assertNull(builder.persistentIdIndex.getIdsByEntry(persistentId))

    builder.removeEntity(entity)
    Assert.assertNull(builder.persistentIdIndex.getIdsByEntry(persistentId))
    Assert.assertNull(builder.persistentIdIndex.getIdsByEntry(newPersistentId))
  }

  @Test
  fun `base index with diff test`() {
    val oldName = "oldName"
    val newName = "newName"
    val builder = PEntityStorageBuilder.create()
    val entity = builder.addPersistentIdEntity(oldName)
    val persistentId = builder.persistentIdIndex.getEntryById(entity.id)
    Assert.assertEquals(oldName, persistentId!!.presentableName)
    Assert.assertEquals(entity.id, builder.persistentIdIndex.getIdsByEntry(persistentId)?.get(0))

    val diff = PEntityStorageBuilder.from(builder.toStorage())
    Assert.assertEquals(entity.id, diff.persistentIdIndex.getIdsByEntry(persistentId)?.get(0))

    val secondEntity = diff.addPersistentIdEntity(newName)
    val secondPersistentId = diff.persistentIdIndex.getEntryById(secondEntity.id)
    Assert.assertEquals(newName, secondPersistentId!!.presentableName)
    Assert.assertNull(builder.persistentIdIndex.getIdsByEntry(secondPersistentId)?.get(0))
    Assert.assertEquals(secondEntity.id, diff.persistentIdIndex.getIdsByEntry(secondPersistentId)?.get(0))

    builder.addDiff(diff)
    Assert.assertEquals(entity.id, builder.persistentIdIndex.getIdsByEntry(persistentId)?.get(0))
    Assert.assertEquals(secondEntity.id, builder.persistentIdIndex.getIdsByEntry(secondPersistentId)?.get(0))
  }

  @Test
  fun `remove from diff test`() {
    val oldName = "oldName"
    val builder = PEntityStorageBuilder.create()
    val entity = builder.addPersistentIdEntity(oldName)
    val persistentId = builder.persistentIdIndex.getEntryById(entity.id)
    Assert.assertEquals(oldName, persistentId!!.presentableName)
    Assert.assertEquals(entity.id, builder.persistentIdIndex.getIdsByEntry(persistentId)?.get(0))

    val diff = PEntityStorageBuilder.from(builder.toStorage())
    Assert.assertEquals(entity.id, diff.persistentIdIndex.getIdsByEntry(persistentId)?.get(0))

    diff.removeEntity(entity)
    Assert.assertEquals(entity.id, builder.persistentIdIndex.getIdsByEntry(persistentId)?.get(0))
    Assert.assertNull(diff.persistentIdIndex.getIdsByEntry(persistentId)?.get(0))

    builder.addDiff(diff)
    Assert.assertNull(builder.persistentIdIndex.getIdsByEntry(persistentId))
  }

  @Test
  fun `change source in diff test`() {
    val oldName = "oldName"
    val newName = "newName"
    val builder = PEntityStorageBuilder.create()
    val entity = builder.addPersistentIdEntity(oldName)
    val persistentId = builder.persistentIdIndex.getEntryById(entity.id)
    Assert.assertEquals(oldName, persistentId!!.presentableName)
    Assert.assertEquals(entity.id, builder.persistentIdIndex.getIdsByEntry(persistentId)?.get(0))

    val diff = PEntityStorageBuilder.from(builder.toStorage())
    Assert.assertEquals(entity.id, diff.persistentIdIndex.getIdsByEntry(persistentId)?.get(0))

    val newEntity = diff.modifyEntity(ModifiablePPersistentIdEntity::class.java, entity) {
      data = newName
    }
    val newPersistentId = diff.persistentIdIndex.getEntryById(newEntity.id)
    Assert.assertEquals(newName, newPersistentId!!.presentableName)
    Assert.assertEquals(newEntity.id, diff.persistentIdIndex.getIdsByEntry(newPersistentId)?.get(0))
    Assert.assertEquals(entity.id, builder.persistentIdIndex.getIdsByEntry(persistentId)?.get(0))

    builder.addDiff(diff)
    Assert.assertNull(builder.persistentIdIndex.getIdsByEntry(persistentId))
    Assert.assertEquals(newEntity.id, builder.persistentIdIndex.getIdsByEntry(newPersistentId)?.get(0))
  }
}