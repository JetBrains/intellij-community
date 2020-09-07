// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage

import com.intellij.workspaceModel.storage.impl.WorkspaceEntityStorageBuilderImpl
import com.intellij.workspaceModel.storage.impl.VirtualFileUrlManagerImpl
import com.intellij.workspaceModel.storage.entities.*
import com.intellij.workspaceModel.storage.entities.ModifiableVFUEntity
import com.intellij.workspaceModel.storage.entities.addNullableVFUEntity
import com.intellij.workspaceModel.storage.entities.addVFU2Entity
import com.intellij.workspaceModel.storage.entities.addVFUEntity
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class VirtualFileIndexTest {
  private lateinit var virtualFileManager: VirtualFileUrlManager
  @Before
  fun setUp() {
    virtualFileManager = VirtualFileUrlManagerImpl()
  }

  @Test
  fun `add entity with not null vfu`() {
    val fileUrl = "/user/opt/app/a.txt"
    val builder = WorkspaceEntityStorageBuilderImpl.create()
    val entity = builder.addVFUEntity("hello", fileUrl, virtualFileManager)
    assertEquals(fileUrl, entity.fileProperty.url)
    assertEquals(entity.fileProperty, builder.indexes.virtualFileIndex.getVirtualFiles(entity.id).first())
  }

  @Test
  fun `change virtual file url`() {
    val fileUrl = "/user/opt/app/a.txt"
    val fileUrl2 = "/user/opt/app/b.txt"
    val fileUrl3 = "/user/opt/app/c.txt"
    val builder = WorkspaceEntityStorageBuilderImpl.create()
    val entity = builder.addVFUEntity("hello", fileUrl, virtualFileManager)
    assertEquals(fileUrl, entity.fileProperty.url)

    val modifiedEntity = builder.modifyEntity(ModifiableVFUEntity::class.java, entity) {
      this.fileProperty = virtualFileManager.fromUrl(fileUrl2)
      this.fileProperty = virtualFileManager.fromUrl(fileUrl3)
    }
    assertEquals(fileUrl3, modifiedEntity.fileProperty.url)
    val virtualFiles = builder.indexes.virtualFileIndex.getVirtualFiles(modifiedEntity.id)
    assertEquals(1, virtualFiles.size)
    assertEquals(modifiedEntity.fileProperty, virtualFiles.first())
  }

  @Test
  fun `add entity with nullable vfu`() {
    val builder = WorkspaceEntityStorageBuilderImpl.create()
    val entity = builder.addNullableVFUEntity("hello", null, virtualFileManager)
    assertNull(entity.fileProperty)
    assertTrue(builder.indexes.virtualFileIndex.getVirtualFiles(entity.id).isEmpty())
  }

  @Test
  fun `add entity with two properties`() {
    val fileUrl = "/user/opt/app/a.txt"
    val secondUrl = "/user/opt/app/b.txt"
    val builder = WorkspaceEntityStorageBuilderImpl.create()
    val entity = builder.addVFU2Entity("hello", fileUrl, secondUrl, virtualFileManager)
    assertEquals(fileUrl, entity.fileProperty.url)
    assertEquals(secondUrl, entity.secondFileProperty.url)

    val virtualFiles = builder.indexes.virtualFileIndex.getVirtualFiles(entity.id)
    assertEquals(2, virtualFiles.size)
    assertEquals(entity.fileProperty, virtualFiles.first())
    assertEquals(entity.secondFileProperty, virtualFiles.last())
  }

  @Test
  fun `add entity with vfu list`() {
    val fileUrlList = listOf("/user/a.txt", "/user/opt/app/a.txt", "/user/opt/app/b.txt")
    val builder = WorkspaceEntityStorageBuilderImpl.create()
    val entity = builder.addListVFUEntity("hello", fileUrlList, virtualFileManager)
    assertEquals(fileUrlList, entity.fileProperty.map { it.url }.sorted())
    assertEquals(fileUrlList.size, builder.indexes.virtualFileIndex.getVirtualFiles(entity.id).size)
  }

  @Test
  fun `add entity to diff`() {
    val fileUrlA = "/user/opt/app/a.txt"
    val fileUrlB = "/user/opt/app/b.txt"
    val builder = WorkspaceEntityStorageBuilderImpl.create()
    val entityA = builder.addVFUEntity("bar", fileUrlA, virtualFileManager)
    assertEquals(fileUrlA, entityA.fileProperty.url)
    assertEquals(entityA.fileProperty, builder.indexes.virtualFileIndex.getVirtualFiles(entityA.id).first())

    val diff = WorkspaceEntityStorageBuilderImpl.from(builder.toStorage())
    val entityB = diff.addVFUEntity("foo", fileUrlB, virtualFileManager)
    assertEquals(fileUrlB, entityB.fileProperty.url)
    assertEquals(entityA.fileProperty, diff.indexes.virtualFileIndex.getVirtualFiles(entityA.id).first())
    assertEquals(entityB.fileProperty, diff.indexes.virtualFileIndex.getVirtualFiles(entityB.id).first())

    assertTrue(builder.indexes.virtualFileIndex.getVirtualFiles(entityB.id).isEmpty())
    builder.addDiff(diff)

    assertEquals(entityA.fileProperty, builder.indexes.virtualFileIndex.getVirtualFiles(entityA.id).first())
    assertEquals(entityB.fileProperty, builder.indexes.virtualFileIndex.getVirtualFiles(entityB.id).first())
  }

  @Test
  fun `remove entity from diff`() {
    val fileUrlA = "/user/opt/app/a.txt"
    val fileUrlB = "/user/opt/app/b.txt"
    val builder = WorkspaceEntityStorageBuilderImpl.create()
    val entityA = builder.addVFUEntity("bar", fileUrlA, virtualFileManager)
    val entityB = builder.addVFUEntity("foo", fileUrlB, virtualFileManager)
    assertEquals(entityA.fileProperty, builder.indexes.virtualFileIndex.getVirtualFiles(entityA.id).first())
    assertEquals(entityB.fileProperty, builder.indexes.virtualFileIndex.getVirtualFiles(entityB.id).first())

    val diff = WorkspaceEntityStorageBuilderImpl.from(builder.toStorage())
    assertEquals(entityA.fileProperty, diff.indexes.virtualFileIndex.getVirtualFiles(entityA.id).first())
    assertEquals(entityB.fileProperty, diff.indexes.virtualFileIndex.getVirtualFiles(entityB.id).first())

    diff.removeEntity(entityB)
    assertEquals(entityA.fileProperty, diff.indexes.virtualFileIndex.getVirtualFiles(entityA.id).first())
    assertTrue(diff.indexes.virtualFileIndex.getVirtualFiles(entityB.id).isEmpty())
    assertEquals(entityB.fileProperty, builder.indexes.virtualFileIndex.getVirtualFiles(entityB.id).first())
    builder.addDiff(diff)

    assertEquals(entityA.fileProperty, builder.indexes.virtualFileIndex.getVirtualFiles(entityA.id).first())
    assertTrue(builder.indexes.virtualFileIndex.getVirtualFiles(entityB.id).isEmpty())
  }

  @Test
  fun `update entity in diff`() {
    val fileUrlA = "/user/opt/app/a.txt"
    val fileUrlB = "/user/opt/app/b.txt"
    val fileUrlC = "/user/opt/app/c.txt"
    val builder = WorkspaceEntityStorageBuilderImpl.create()
    val entityA = builder.addVFUEntity("bar", fileUrlA, virtualFileManager)
    var entityB = builder.addVFUEntity("foo", fileUrlB, virtualFileManager)
    assertEquals(entityA.fileProperty, builder.indexes.virtualFileIndex.getVirtualFiles(entityA.id).first())
    assertEquals(entityB.fileProperty, builder.indexes.virtualFileIndex.getVirtualFiles(entityB.id).first())

    val diff = WorkspaceEntityStorageBuilderImpl.from(builder.toStorage())
    assertEquals(entityA.fileProperty, diff.indexes.virtualFileIndex.getVirtualFiles(entityA.id).first())
    var virtualFile = diff.indexes.virtualFileIndex.getVirtualFiles(entityB.id)
    assertNotNull(virtualFile)
    assertEquals(fileUrlB, entityB.fileProperty.url)
    assertEquals(entityB.fileProperty, virtualFile.first())

    entityB = diff.modifyEntity(ModifiableVFUEntity::class.java, entityB) {
      fileProperty = virtualFileManager.fromUrl(fileUrlC)
    }
    assertEquals(entityA.fileProperty, diff.indexes.virtualFileIndex.getVirtualFiles(entityA.id).first())
    virtualFile = diff.indexes.virtualFileIndex.getVirtualFiles(entityB.id)
    assertNotNull(virtualFile)
    assertEquals(fileUrlC, entityB.fileProperty.url)
    assertEquals(fileUrlC, virtualFile.first().url)
    assertNotEquals(fileUrlB, entityB.fileProperty.url)
    assertEquals(entityB.fileProperty, virtualFile.first())
    builder.addDiff(diff)

    assertEquals(entityA.fileProperty, builder.indexes.virtualFileIndex.getVirtualFiles(entityA.id).first())
    virtualFile = builder.indexes.virtualFileIndex.getVirtualFiles(entityB.id)
    assertNotNull(virtualFile)
    assertEquals(fileUrlC, entityB.fileProperty.url)
    assertEquals(fileUrlC, virtualFile.first().url)
    assertNotEquals(fileUrlB, entityB.fileProperty.url)
    assertEquals(entityB.fileProperty, virtualFile.first())
  }
}