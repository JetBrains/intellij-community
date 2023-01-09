// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage

import com.intellij.workspaceModel.storage.bridgeEntities.ContentRootEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity
import com.intellij.workspaceModel.storage.entities.test.api.DescriptorInstance
import com.intellij.workspaceModel.storage.entities.test.api.MySource
import com.intellij.workspaceModel.storage.entities.test.api.ProjectModelTestEntity
import com.intellij.workspaceModel.storage.entities.test.api.modifyEntity
import com.intellij.workspaceModel.storage.impl.url.VirtualFileUrlManagerImpl
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull

class RiderEntitiesTest {
  @Test
  fun `check for update the connection to rider entity via extension property `() {
    val virtualFileManager = VirtualFileUrlManagerImpl()
    val builder = createEmptyBuilder()

    val contentRoot = ContentRootEntity(virtualFileManager.fromUrl("/a/b/a.txt"), emptyList(), MySource)
    val moduleEntity = ModuleEntity("one", emptyList(), MySource) {
      this.contentRoots = listOf(contentRoot)
    }
    builder.addEntity(moduleEntity)
    val snapshot = builder.toSnapshot()

    val projectModelEntity = ProjectModelTestEntity("1", DescriptorInstance("project model data"), MySource) {
      this.contentRoot = contentRoot
    }
    val newBuilder = snapshot.toBuilder()
    newBuilder.addEntity(projectModelEntity)

    val anotherBuilder = snapshot.toBuilder()
    val existingContentRootEntity = anotherBuilder.entities(ContentRootEntity::class.java).single()
    val newProjectModelEntity = ProjectModelTestEntity("2", DescriptorInstance("project model data"), MySource) {
      this.contentRoot = existingContentRootEntity
    }
    anotherBuilder.addEntity(newProjectModelEntity)

    val contentRootEntity = newBuilder.entities(ContentRootEntity::class.java).single()
    val sameContentRootEntity = anotherBuilder.entities(ContentRootEntity::class.java).single()
    Assertions.assertEquals(contentRootEntity, sameContentRootEntity)
    newBuilder.replaceBySource({ it is MySource }, anotherBuilder)
    Assertions.assertEquals(1, newBuilder.entities(ContentRootEntity::class.java).toList().size)
  }

  @Test
  fun `check links survive after replace operation`() {
    val virtualFileManager = VirtualFileUrlManagerImpl()
    val builder = createEmptyBuilder()

    val module = ModuleEntity("one", emptyList(), MySource)
    val rootContentRoot = ContentRootEntity(virtualFileManager.fromUrl("/a"), emptyList(), MySource) {
      this.module = module
    }
    builder.addEntity(rootContentRoot)

    val leftContentRoot = ContentRootEntity(virtualFileManager.fromUrl("/a/b/a.txt"), emptyList(), MySource) {
      this.module = module
    }
    builder.addEntity(leftContentRoot)

    val rightContentRoot = ContentRootEntity(virtualFileManager.fromUrl("/a/c/a.txt"), emptyList(), MySource) {
      this.module = module
    }
    builder.addEntity(rightContentRoot)

    val rootProjectModelEntity = ProjectModelTestEntity("0", DescriptorInstance("root project model data"), MySource) {
      this.contentRoot = rootContentRoot
    }
    builder.addEntity(rootProjectModelEntity)

    val leftProjectModelEntity = ProjectModelTestEntity("1", DescriptorInstance("left project model data"), MySource) {
      this.contentRoot = leftContentRoot
      this.parentEntity = rootProjectModelEntity
    }
    builder.addEntity(leftProjectModelEntity)

    val rightProjectModelEntity = ProjectModelTestEntity("2", DescriptorInstance("right project model data"), MySource) {
      this.contentRoot = rightContentRoot
      this.parentEntity = rootProjectModelEntity
    }
    builder.addEntity(rightProjectModelEntity)

    val snapshot = builder.toSnapshot()
    val newBuilder = snapshot.toBuilder()
    val anotherBuilder = snapshot.toBuilder()

    var existingProjectModelEntity = anotherBuilder.entities(ProjectModelTestEntity::class.java).single { it.descriptor.data.contains("left") }
    assertNotNull(existingProjectModelEntity.contentRoot)
    anotherBuilder.modifyEntity(existingProjectModelEntity) {
      this.descriptor = DescriptorInstance("project model data left")
    }

    existingProjectModelEntity = anotherBuilder.entities(ProjectModelTestEntity::class.java).single { it.descriptor.data.contains("right") }
    assertNotNull(existingProjectModelEntity.contentRoot)
    anotherBuilder.modifyEntity(existingProjectModelEntity) {
      this.descriptor = DescriptorInstance("project model data right")
    }

    newBuilder.replaceBySource({ it is MySource }, anotherBuilder)
    newBuilder.entities(ProjectModelTestEntity::class.java).forEach { assertNotNull(it.contentRoot) }
  }
}