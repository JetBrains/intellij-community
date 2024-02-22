// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests

import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.impl.MutableEntityStorageImpl
import com.intellij.platform.workspace.storage.impl.indices.VirtualFileIndex
import com.intellij.platform.workspace.storage.impl.url.VirtualFileUrlManagerImpl
import com.intellij.platform.workspace.storage.testEntities.entities.*
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.util.io.URLUtil
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StorageIndexesTest {
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

    val entityMap = builder.entitiesBySource { it == MySource }
      .groupBy { it.getEntityInterface() }
    assertEquals(3, entityMap.size)

    val entities = entityMap[ParentSubEntity::class.java]
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
    val sourceUrl = virtualFileUrlManager.getOrCreateFromUrl("/source".toPathWithScheme())
    val directory = virtualFileUrlManager.getOrCreateFromUrl("/tmp/example".toPathWithScheme())
    val firstRoot = virtualFileUrlManager.getOrCreateFromUrl("/m2/root/one".toPathWithScheme())
    val secondRoot = virtualFileUrlManager.getOrCreateFromUrl("/m2/root/second".toPathWithScheme())

    val builder = MutableEntityStorage.create()
    val entity = builder addEntity VFUEntity2("VFUEntityData", directory, listOf(firstRoot, secondRoot), VFUEntitySource(sourceUrl))

    compareEntityByProperty(builder, entity, "entitySource", sourceUrl) { it.entitySource.virtualFileUrl!! }
    compareEntityByProperty(builder, entity, "directoryPath", directory) { it.directoryPath }
    compareEntityByProperty(builder, entity, "notNullRoots", firstRoot) { it.notNullRoots[0] }
    compareEntityByProperty(builder, entity, "notNullRoots", secondRoot) { it.notNullRoots[1] }
  }

  @Test
  fun `check persistent id index`() {
    val builder = MutableEntityStorage.create()
    builder as MutableEntityStorageImpl
    val entity = builder addEntity FirstEntityWithPId("FirstEntityData", MySource)

    val entityIds = builder.indexes.symbolicIdIndex.getIdsByEntry(entity.symbolicId)
    assertNotNull(entityIds)
  }

  @Test
  fun `get entities by sources and update the builder in iteration`() {
    val builder = MutableEntityStorage.create()
    builder addEntity ParentEntity("ParentData", MySource)
    builder addEntity ParentEntity("X", SampleEntitySource("X"))

    val entities = builder.entitiesBySource { true }
    assertDoesNotThrow {
      entities.forEach {
        builder.modifyEntity(WorkspaceEntity.Builder::class.java, it) {
          this.entitySource = AnotherSource
        }
      }
    }
  }

  private fun compareEntityByProperty(builder: MutableEntityStorage, originEntity: VFUEntity2,
                                      propertyName: String, virtualFileUrl: VirtualFileUrl,
                                      propertyExtractor: (VFUEntity2) -> VirtualFileUrl) {
    val virtualFileUrlIndex = builder.getVirtualFileUrlIndex() as VirtualFileIndex
    val entities = virtualFileUrlIndex.findEntitiesToPropertyNameByUrl(virtualFileUrl).toList()
    assertEquals(1, entities.size)
    val entity = entities[0]
    assertEquals(propertyName, entity.second)
    val oldProperty = propertyExtractor.invoke(originEntity)
    val newProperty = propertyExtractor.invoke(entity.first as VFUEntity2)
    assertEquals(oldProperty, newProperty)
  }

  private fun String.toPathWithScheme(): String {
    return URLUtil.FILE_PROTOCOL + URLUtil.SCHEME_SEPARATOR + FileUtil.toSystemIndependentName(this)
  }
}