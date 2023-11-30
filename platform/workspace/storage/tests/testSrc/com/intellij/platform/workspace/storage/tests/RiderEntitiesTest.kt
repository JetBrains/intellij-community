// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests

import com.intellij.platform.workspace.storage.testEntities.entities.*
import com.intellij.platform.workspace.storage.toBuilder
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class RiderEntitiesTest {
  @Test
  fun `check for update the connection to rider entity via extension property `() {
    val builder = createEmptyBuilder()

    val contentRoot = ContentRootTestEntity(MySource)
    val moduleEntity = ModuleTestEntity("one", MySource) {
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
    val existingContentRootTestEntity = anotherBuilder.entities(ContentRootTestEntity::class.java).single()
    val newProjectModelEntity = ProjectModelTestEntity("2", DescriptorInstance("project model data"), MySource) {
      this.contentRoot = existingContentRootTestEntity
    }
    anotherBuilder.addEntity(newProjectModelEntity)

    val contentRootTestEntity = newBuilder.entities(ContentRootTestEntity::class.java).single()
    val sameContentRootTestEntity = anotherBuilder.entities(ContentRootTestEntity::class.java).single()
    assertEquals(contentRootTestEntity, sameContentRootTestEntity)
    newBuilder.replaceBySource({ it is MySource }, anotherBuilder)
    assertEquals(1, newBuilder.entities(ContentRootTestEntity::class.java).toList().size)
  }

  @Test
  fun `check links survive after replace operation`() {
    val builder = createEmptyBuilder()

    val module = ModuleTestEntity("one", MySource)
    val rootContentRoot = ContentRootTestEntity(MySource) {
      this.module = module
    }
    builder.addEntity(rootContentRoot)

    val leftContentRoot = ContentRootTestEntity(MySource) {
      this.module = module
    }
    builder.addEntity(leftContentRoot)

    val rightContentRoot = ContentRootTestEntity(MySource) {
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

    var existingProjectModelEntity = anotherBuilder.entities(ProjectModelTestEntity::class.java).single {
      it.descriptor.data.contains("left")
    }
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