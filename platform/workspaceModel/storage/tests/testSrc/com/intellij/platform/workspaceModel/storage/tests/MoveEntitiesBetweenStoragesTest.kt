// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspaceModel.storage.tests

import com.intellij.platform.workspaceModel.storage.testEntities.entities.*
import com.intellij.workspaceModel.storage.impl.url.VirtualFileUrlManagerImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MoveEntitiesBetweenStoragesTest {
  @Test
  fun `move entity`() {
    val snapshot = createEmptyBuilder().also {
      it addEntity SampleEntity(false, "to copy", ArrayList(), HashMap(), VirtualFileUrlManagerImpl().fromUrl("file:///tmp"),
                                SampleEntitySource("test"))
    }.toSnapshot()

    val target = createEmptyBuilder().also {
      it.addEntity(snapshot.singleSampleEntity())
    }.toSnapshot()
    val entity = target.singleSampleEntity()
    assertEquals("to copy", entity.stringProperty)
  }

  @Test
  fun `move entity with child`() {
    val snapshot = createEmptyBuilder().also {
      val parent = it addEntity XParentEntity("parent", MySource)
      it addEntity XChildEntity("child", MySource) {
        parentEntity = parent
      }
      it addEntity XChildWithOptionalParentEntity("child", MySource) {
        optionalParent = parent
      }
    }.toSnapshot()

    val target = createEmptyBuilder().also {
      it.addEntity(snapshot.entities(XParentEntity::class.java).single())
    }.toSnapshot()
    val entity = target.entities(XParentEntity::class.java).single()
    assertEquals("parent", entity.parentProperty)
    assertEquals("child", entity.children.single().childProperty)
    assertEquals("child", entity.optionalChildren.single().childProperty)
  }

  @Test
  fun `move entity with child and grand child`() {
    val snapshot = createEmptyBuilder().also {
      val parentEntity = it addEntity XParentEntity("parent", MySource)
      val childEntity = it addEntity XChildEntity("child", MySource) {
        this@XChildEntity.parentEntity = parentEntity
      }
      it addEntity XChildChildEntity(MySource) {
        parent1 = parentEntity
        parent2 = childEntity
      }
    }.toSnapshot()

    val target = createEmptyBuilder().also {
      it.addEntity(snapshot.entities(XParentEntity::class.java).single())
    }.toSnapshot()
    val entity = target.entities(XParentEntity::class.java).single()
    assertEquals("parent", entity.parentProperty)
    val child = entity.children.single()
    assertEquals("child", child.childProperty)
    val grandChild = child.childChild.single()
    assertEquals(entity, grandChild.parent1)
    assertEquals(child, grandChild.parent2)
  }
}