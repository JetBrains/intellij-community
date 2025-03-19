// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests

import com.intellij.idea.TestFor
import com.intellij.platform.workspace.storage.testEntities.entities.*
import com.intellij.platform.workspace.storage.toBuilder
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class OneToOneTest {
  @Test
  fun `move child to other parent`() {
    val builder = createEmptyBuilder()
    val parent = builder addEntity OoParentEntity("data", MySource) {
      this.child = OoChildEntity("Data", MySource)
    }

    val newBuilder = builder.toSnapshot().toBuilder()
    newBuilder addEntity OoParentEntity("newData", MySource) parent@{
      newBuilder.modifyOoChildEntity(parent.from(newBuilder).child!!) child@{
        this@parent.child = this@child
      }
    }

    assertEquals("newData", newBuilder.entities(OoChildEntity::class.java).single().parentEntity.parentProperty)
  }

  @Test
  @TestFor(issues = ["IDEA-338250"])
  fun `the first child is not removed after modifications`() {
    val builder = createEmptyBuilder()
    builder addEntity OoParentEntity("data", MySource) {
      this.child = OoChildEntity("data", MySource)
    }
    val parent = builder addEntity OoParentEntity("data2", MySource)
    val parentForChildSource = builder addEntity OoParentEntity("info", MySource) {
      this.child = OoChildEntity("data", MySource)
    }

    val newBuilder = builder.toSnapshot().toBuilder()
    newBuilder.modifyOoParentEntity(parent.from(newBuilder)) parent@{
      newBuilder.modifyOoChildEntity(parentForChildSource.from(newBuilder).child!!) child@{
        this@parent.child = this@child
      }
    }

    assertEquals(2, newBuilder.entities(OoChildEntity::class.java).toList().size)
  }

  @Test
  fun `add parent change data add parent again`() {
    val builder = createEmptyBuilder()
    val parent = builder addEntity OoParentEntity("data", MySource)

    val newBuilder = builder.toSnapshot().toBuilder()
    newBuilder addEntity OoChildWithNullableParentEntity(MySource) child@{
      newBuilder.modifyOoParentEntity(parent.from(newBuilder)) parent@{
        this@child.parentEntity = this@parent
      }
    }
    newBuilder.modifyOoParentEntity(parent.from(newBuilder)) {
      this.entitySource = AnotherSource
    }
    newBuilder addEntity OoChildWithNullableParentEntity(AnotherSource) child@{
      newBuilder.modifyOoParentEntity(parent.from(newBuilder)) parent@{
        this@child.parentEntity = this@parent
      }
    }

    val targetBuilder = builder.toSnapshot().toBuilder()
    targetBuilder.applyChangesFrom(newBuilder)

    val updatedParentEntity = targetBuilder
      .entities(OoChildWithNullableParentEntity::class.java)
      .single { it.entitySource is AnotherSource }
      .parentEntity
    assertEquals("data", updatedParentEntity!!.parentProperty)
  }
}