// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests

import com.intellij.platform.workspace.storage.impl.ImmutableEntityStorageImpl
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.url.VirtualFileUrlManagerImpl
import com.intellij.platform.workspace.storage.testEntities.entities.ListVFUEntity
import com.intellij.platform.workspace.storage.testEntities.entities.NullableVFUEntity
import com.intellij.platform.workspace.storage.testEntities.entities.SampleEntitySource
import com.intellij.platform.workspace.storage.testEntities.entities.VFUEntity
import com.intellij.platform.workspace.storage.testEntities.entities.VFUWithTwoPropertiesEntity
import com.intellij.platform.workspace.storage.testEntities.entities.modifyVFUEntity
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@TestApplication
class VirtualFileIndexTest {
  private lateinit var virtualFileManager: VirtualFileUrlManager

  @BeforeEach
  fun setUp() {
    virtualFileManager = VirtualFileUrlManagerImpl()
  }

  @Test
  fun `add entity with not null vfu`() {
    val fileUrl = "/user/opt/app/a.txt"
    val builder = createEmptyBuilder()
    val entity = builder addEntity VFUEntity("hello", virtualFileManager.storeAndGet(fileUrl), SampleEntitySource("test"))
    assertEquals(fileUrl, entity.fileProperty.url)
    assertEquals(entity.fileProperty,
                 builder.indexes.virtualFileIndex.getVirtualFiles((entity as WorkspaceEntityBase).id).first())
  }

  @Test
  fun `change virtual file url`() {
    val fileUrl = "/user/opt/app/a.txt"
    val fileUrl2 = "/user/opt/app/b.txt"
    val fileUrl3 = "/user/opt/app/c.txt"
    val builder = createEmptyBuilder()
    val entity = builder addEntity VFUEntity("hello", virtualFileManager.storeAndGet(fileUrl), SampleEntitySource("test"))
    assertEquals(fileUrl, entity.fileProperty.url)

    val modifiedEntity = builder.modifyVFUEntity(entity) {
      this.fileProperty = virtualFileManager.storeAndGet(fileUrl2)
      this.fileProperty = virtualFileManager.storeAndGet(fileUrl3)
    }
    assertEquals(fileUrl3, modifiedEntity.fileProperty.url)
    modifiedEntity as WorkspaceEntityBase
    val virtualFiles = builder.indexes.virtualFileIndex.getVirtualFiles(modifiedEntity.id)
    assertEquals(1, virtualFiles.size)
    assertEquals(modifiedEntity.fileProperty, virtualFiles.first())
  }

  @Test
  fun `add entity with nullable vfu`() {
    val builder = createEmptyBuilder()
    val entity = builder addEntity NullableVFUEntity("hello", SampleEntitySource("test")) {
      fileProperty = null?.let<String, VirtualFileUrl> { virtualFileManager.storeAndGet(it) }
    }
    assertNull(entity.fileProperty)
    assertTrue(builder.indexes.virtualFileIndex.getVirtualFiles((entity as WorkspaceEntityBase).id).isEmpty())
  }

  @Test
  fun `add entity with two properties`() {
    val fileUrl = "/user/opt/app/a.txt"
    val secondUrl = "/user/opt/app/b.txt"
    val builder = createEmptyBuilder()
    val entity = builder addEntity VFUWithTwoPropertiesEntity("hello",
                                                              virtualFileManager.storeAndGet(fileUrl),
                                                              virtualFileManager.storeAndGet(secondUrl), SampleEntitySource("test"))
    entity as WorkspaceEntityBase
    assertEquals(fileUrl, entity.fileProperty.url)
    assertEquals(secondUrl, entity.secondFileProperty.url)

    val virtualFiles = builder.indexes.virtualFileIndex.getVirtualFiles(entity.id)
    assertEquals(2, virtualFiles.size)
    assertTrue(virtualFiles.contains(entity.fileProperty))
    assertTrue(virtualFiles.contains(entity.secondFileProperty))
  }

  @Test
  fun `add entity with vfu list`() {
    val fileUrlList = listOf("/user/a.txt", "/user/opt/app/a.txt", "/user/opt/app/b.txt")
    val builder = createEmptyBuilder()
    val entity = builder addEntity ListVFUEntity("hello", fileUrlList.map { virtualFileManager.storeAndGet(it) }, SampleEntitySource("test"))
    assertEquals(fileUrlList, entity.fileProperty.map { it.url }.sorted())
    assertEquals(fileUrlList.size, builder.indexes.virtualFileIndex.getVirtualFiles((entity as WorkspaceEntityBase).id).size)
  }

  @Test
  fun `add entity to diff`() {
    val fileUrlA = "/user/opt/app/a.txt"
    val fileUrlB = "/user/opt/app/b.txt"
    val builder = createEmptyBuilder()
    val entityA = builder addEntity VFUEntity("bar", virtualFileManager.storeAndGet(fileUrlA), SampleEntitySource("test"))
    entityA as WorkspaceEntityBase
    assertEquals(fileUrlA, entityA.fileProperty.url)
    assertEquals(entityA.fileProperty, builder.indexes.virtualFileIndex.getVirtualFiles(entityA.id).first())

    val diff = createBuilderFrom(builder.toSnapshot())
    val entityB = diff addEntity VFUEntity("foo", virtualFileManager.storeAndGet(fileUrlB), SampleEntitySource("test"))
    entityB as WorkspaceEntityBase
    assertEquals(fileUrlB, entityB.fileProperty.url)
    assertEquals(entityA.fileProperty, diff.indexes.virtualFileIndex.getVirtualFiles(entityA.id).first())
    assertEquals(entityB.fileProperty, diff.indexes.virtualFileIndex.getVirtualFiles(entityB.id).first())

    assertTrue(builder.indexes.virtualFileIndex.getVirtualFiles(entityB.id).isEmpty())
    builder.applyChangesFrom(diff)

    assertEquals(entityA.fileProperty, builder.indexes.virtualFileIndex.getVirtualFiles(entityA.id).first())
    assertEquals(entityB.fileProperty, builder.indexes.virtualFileIndex.getVirtualFiles(entityB.id).first())
  }

  @Test
  fun `mutating a builder does not change the index of the snapshot it was created from`() {
    // `vfu2EntityId` is copied shallowly in `startWrite`, so a builder and the snapshot it came from share
    // the inner per-url maps until the builder copies one on write. Entities sharing a single url exercise
    // that sharing: both records live in the same inner map.
    val sharedVfu = virtualFileManager.storeAndGet("/user/opt/app/shared.txt")
    val builder = createEmptyBuilder()
    builder addEntity VFUEntity("first", sharedVfu, SampleEntitySource("test"))
    builder addEntity VFUEntity("second", sharedVfu, SampleEntitySource("test"))

    val snapshot = builder.toSnapshot() as ImmutableEntityStorageImpl
    assertEquals(2, snapshot.getVirtualFileUrlIndex().findEntitiesByUrl(sharedVfu).count())

    val diff = createBuilderFrom(snapshot)
    diff.removeEntity(diff.entities(VFUEntity::class.java).single { it.data == "first" })

    assertEquals(1, diff.getVirtualFileUrlIndex().findEntitiesByUrl(sharedVfu).count())
    assertEquals(2, snapshot.getVirtualFileUrlIndex().findEntitiesByUrl(sharedVfu).count())
    snapshot.indexes.virtualFileIndex.assertConsistency()

    // The other direction: adding a record for a url the snapshot already indexes.
    val addingDiff = createBuilderFrom(snapshot)
    addingDiff addEntity VFUEntity("third", sharedVfu, SampleEntitySource("test"))

    assertEquals(3, addingDiff.getVirtualFileUrlIndex().findEntitiesByUrl(sharedVfu).count())
    assertEquals(2, snapshot.getVirtualFileUrlIndex().findEntitiesByUrl(sharedVfu).count())
    snapshot.indexes.virtualFileIndex.assertConsistency()
  }

  @Test
  fun `remove entity from diff`() {
    val fileUrlA = "/user/opt/app/a.txt"
    val fileUrlB = "/user/opt/app/b.txt"
    val builder = createEmptyBuilder()
    val entityA = builder addEntity VFUEntity("bar", virtualFileManager.storeAndGet(fileUrlA), SampleEntitySource("test"))
    val entityB = builder addEntity VFUEntity("foo", virtualFileManager.storeAndGet(fileUrlB), SampleEntitySource("test"))
    entityA as WorkspaceEntityBase
    entityB as WorkspaceEntityBase
    assertEquals(entityA.fileProperty, builder.indexes.virtualFileIndex.getVirtualFiles(entityA.id).first())
    assertEquals(entityB.fileProperty, builder.indexes.virtualFileIndex.getVirtualFiles(entityB.id).first())

    val diff = createBuilderFrom(builder.toSnapshot())
    assertEquals(entityA.fileProperty, diff.indexes.virtualFileIndex.getVirtualFiles(entityA.id).first())
    assertEquals(entityB.fileProperty, diff.indexes.virtualFileIndex.getVirtualFiles(entityB.id).first())

    diff.removeEntity(entityB.from(diff))
    assertEquals(entityA.fileProperty, diff.indexes.virtualFileIndex.getVirtualFiles(entityA.id).first())
    assertTrue(diff.indexes.virtualFileIndex.getVirtualFiles(entityB.id).isEmpty())
    assertEquals(entityB.fileProperty, builder.indexes.virtualFileIndex.getVirtualFiles(entityB.id).first())
    builder.applyChangesFrom(diff)

    assertEquals(entityA.fileProperty, builder.indexes.virtualFileIndex.getVirtualFiles(entityA.id).first())
    assertTrue(builder.indexes.virtualFileIndex.getVirtualFiles(entityB.id).isEmpty())
  }

  @Test
  fun `update entity in diff`() {
    val fileUrlA = "/user/opt/app/a.txt"
    val fileUrlB = "/user/opt/app/b.txt"
    val fileUrlC = "/user/opt/app/c.txt"
    val builder = createEmptyBuilder()
    val entityA = builder addEntity VFUEntity("bar", virtualFileManager.storeAndGet(fileUrlA), SampleEntitySource("test"))
    var entityB = builder addEntity VFUEntity("foo", virtualFileManager.storeAndGet(fileUrlB), SampleEntitySource("test"))
    entityA as WorkspaceEntityBase
    entityB as WorkspaceEntityBase
    assertEquals(entityA.fileProperty, builder.indexes.virtualFileIndex.getVirtualFiles(entityA.id).first())
    assertEquals(entityB.fileProperty, builder.indexes.virtualFileIndex.getVirtualFiles(entityB.id).first())

    val diff = createBuilderFrom(builder.toSnapshot())
    assertEquals(entityA.fileProperty, diff.indexes.virtualFileIndex.getVirtualFiles(entityA.id).first())
    var virtualFile = diff.indexes.virtualFileIndex.getVirtualFiles(entityB.id)
    assertNotNull(virtualFile)
    assertEquals(fileUrlB, entityB.fileProperty.url)
    assertEquals(entityB.fileProperty, virtualFile.first())

    entityB = diff.modifyVFUEntity((entityB as VFUEntity).from(diff)) {
      fileProperty = virtualFileManager.storeAndGet(fileUrlC)
    }
    assertEquals(entityA.fileProperty, diff.indexes.virtualFileIndex.getVirtualFiles(entityA.id).first())

    entityB as WorkspaceEntityBase
    virtualFile = diff.indexes.virtualFileIndex.getVirtualFiles(entityB.id)
    assertNotNull(virtualFile)
    assertEquals(fileUrlC, entityB.fileProperty.url)
    assertEquals(fileUrlC, virtualFile.first().url)
    assertNotEquals(fileUrlB, entityB.fileProperty.url)
    assertEquals(entityB.fileProperty, virtualFile.first())
    builder.applyChangesFrom(diff)

    assertEquals(entityA.fileProperty, builder.indexes.virtualFileIndex.getVirtualFiles(entityA.id).first())
    virtualFile = builder.indexes.virtualFileIndex.getVirtualFiles(entityB.id)
    assertNotNull(virtualFile)
    assertEquals(fileUrlC, entityB.fileProperty.url)
    assertEquals(fileUrlC, virtualFile.first().url)
    assertNotEquals(fileUrlB, entityB.fileProperty.url)
    assertEquals(entityB.fileProperty, virtualFile.first())
  }
}
