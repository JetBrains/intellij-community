// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests

import com.intellij.platform.workspace.storage.createEntityTreeCopy
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.url.VirtualFileUrlManagerImpl
import com.intellij.platform.workspace.storage.testEntities.entities.MySource
import com.intellij.platform.workspace.storage.testEntities.entities.OptionalOneToOneChildEntity
import com.intellij.platform.workspace.storage.testEntities.entities.OptionalOneToOneParentEntity
import com.intellij.platform.workspace.storage.testEntities.entities.SampleEntity
import com.intellij.platform.workspace.storage.testEntities.entities.SampleEntitySource
import com.intellij.platform.workspace.storage.testEntities.entities.XChildChildEntity
import com.intellij.platform.workspace.storage.testEntities.entities.XChildEntity
import com.intellij.platform.workspace.storage.testEntities.entities.XChildWithOptionalParentEntity
import com.intellij.platform.workspace.storage.testEntities.entities.XParentEntity
import com.intellij.platform.workspace.storage.testEntities.entities.modifyOptionalOneToOneChildEntity
import com.intellij.platform.workspace.storage.testEntities.entities.modifyXChildEntity
import com.intellij.platform.workspace.storage.testEntities.entities.modifyXParentEntity
import com.intellij.platform.workspace.storage.toBuilder
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIsNot

class MoveEntitiesBetweenStoragesTest {
  @Test
  fun `move entity`() {
    val snapshot = createEmptyBuilder().also {
      it addEntity SampleEntity(false, "to copy", ArrayList(), HashMap(), VirtualFileUrlManagerImpl().getOrCreateFromUrl("file:///tmp"),
                                SampleEntitySource("test"))
    }.toSnapshot()

    val target = createEmptyBuilder().also {
      it.addEntity(snapshot.singleSampleEntity().createEntityTreeCopy(true))
    }.toSnapshot()
    val entity = target.singleSampleEntity()
    assertEquals("to copy", entity.stringProperty)
  }

  @Test
  fun `move entity with child`() {
    val snapshot = createEmptyBuilder().also {
      val parent = it addEntity XParentEntity("parent", MySource)
      it.modifyXParentEntity(parent) parent@{
        it addEntity XChildEntity("child", MySource) {
          parentEntity = this@parent
        }
        it addEntity XChildWithOptionalParentEntity("child", MySource) {
          optionalParent = this@parent
        }
      }
    }.toSnapshot()

    val target = createEmptyBuilder().also {
      it.addEntity(snapshot.entities(XParentEntity::class.java).single().createEntityTreeCopy(true))
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
        it.modifyXParentEntity(parentEntity) parent@{
          this@XChildEntity.parentEntity = this@parent
        }
      }
      it addEntity XChildChildEntity(MySource) {
        it.modifyXParentEntity(parentEntity) parent@{
          it.modifyXChildEntity(childEntity) child@{
            parent1 = this@parent
            parent2 = this@child
          }
        }
      }
    }.toSnapshot()

    val target = createEmptyBuilder().also {
      it.addEntity(snapshot.entities(XParentEntity::class.java).single().createEntityTreeCopy(true))
    }.toSnapshot()
    val entity = target.entities(XParentEntity::class.java).single()
    assertEquals("parent", entity.parentProperty)
    val child = entity.children.single()
    assertEquals("child", child.childProperty)
    val grandChild = child.childChild.single()
    assertEquals(entity, grandChild.parent1)
    assertEquals(child, grandChild.parent2)
  }

  @Test
  fun `adding entity with a reference to the entity from the same builder doesnot create second entity`() {
    val builder = createEmptyBuilder()
    val child = builder addEntity OptionalOneToOneChildEntity("data", MySource)

    val newBuilder = builder.toSnapshot().toBuilder()

    val sameChild = child.from(newBuilder)
    assertIsNot<ModifiableWorkspaceEntityBase<*, *>>(sameChild)
    newBuilder addEntity OptionalOneToOneParentEntity(MySource) parent@{
      newBuilder.modifyOptionalOneToOneChildEntity(sameChild) child@{
        this@parent.child = this@child
      }
    }

    val children = newBuilder.entities(OptionalOneToOneChildEntity::class.java).toList()

    assertEquals(1, children.size)
  }
}