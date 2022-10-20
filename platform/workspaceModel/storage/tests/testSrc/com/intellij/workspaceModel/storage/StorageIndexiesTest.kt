// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.impl

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.impl.url.VirtualFileUrlManagerImpl
import com.intellij.workspaceModel.storage.entities.test.api.*
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StorageIndexiesTest {
  @Test
  fun `check entity source index`() {
    val entity = ParentSubEntity("ParentData", MySource) {
      child = ChildSubEntity(MySource) {
        child = ChildSubSubEntity("ChildData", MySource)
      }
    }

    val builder = MutableEntityStorage.create()
    builder.addEntity(entity)
    assertEquals(MySource, entity.entitySource)

    val entityMap = builder.entitiesBySource { it == MySource }[MySource]
    assertEquals(3, entityMap?.size)

    val entities = entityMap?.get(ParentSubEntity::class.java)
    assertNotNull(entities)
    assertEquals(1, entities.size)
    val indexedEntity = entities[0]
    assertNotNull(indexedEntity)
    assertTrue(indexedEntity is ParentSubEntity)
    assertEquals(entity.parentData, indexedEntity.parentData)
    assertEquals(entity.entitySource, indexedEntity.entitySource)
  }

  @Test
  fun `check virtual file index`() {
    val virtualFileUrlManager = VirtualFileUrlManagerImpl()
    val sourceUrl = virtualFileUrlManager.fromPath("/source")
    val directory = virtualFileUrlManager.fromPath("/tmp/example")
    val firstRoot = virtualFileUrlManager.fromPath("/m2/root/one")
    val secondRoot = virtualFileUrlManager.fromPath("/m2/root/second")

    val entity = VFUEntity2("VFUEntityData", directory, listOf(firstRoot, secondRoot), VFUEntitySource(sourceUrl))

    val builder = MutableEntityStorage.create()
    builder.addEntity(entity)

    compareEntityByProperty(builder, entity, "entitySource", sourceUrl) { it.entitySource.virtualFileUrl!! }
    compareEntityByProperty(builder, entity, "directoryPath", directory) { it.directoryPath }
    compareEntityByProperty(builder, entity, "notNullRoots", firstRoot) { it.notNullRoots[0] }
    compareEntityByProperty(builder, entity, "notNullRoots", secondRoot) { it.notNullRoots[1] }
  }

  @Test
  fun `check persistent id index`() {
    val entity = FirstEntityWithPId("FirstEntityData", MySource)

    val builder = MutableEntityStorage.create()
    builder as MutableEntityStorageImpl
    builder.addEntity(entity)
    val entityIds = builder.indexes.symbolicIdIndex.getIdsByEntry(entity.symbolicId)
    assertNotNull(entityIds)
  }

  private fun compareEntityByProperty(builder: MutableEntityStorage, originEntity: VFUEntity2,
                                      propertyName: String, virtualFileUrl: VirtualFileUrl,
                                      propertyExtractor: (VFUEntity2) -> VirtualFileUrl) {
    val entities = builder.getVirtualFileUrlIndex().findEntitiesByUrl(virtualFileUrl).toList()
    assertEquals(1, entities.size)
    val entity = entities[0]
    assertEquals(propertyName, entity.second)
    val oldProperty = propertyExtractor.invoke(originEntity)
    val newProperty = propertyExtractor.invoke(entity.first as VFUEntity2)
    assertEquals(oldProperty, newProperty)
  }
}

internal class VFUEntitySource(private val vfu: VirtualFileUrl) : EntitySource {
  override val virtualFileUrl: VirtualFileUrl
    get() = vfu
}