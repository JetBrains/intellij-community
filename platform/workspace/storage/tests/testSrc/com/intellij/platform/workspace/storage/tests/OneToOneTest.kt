// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests

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
    newBuilder addEntity OoParentEntity("newData", MySource) {
      this.child = parent.from(newBuilder).child
    }

    assertEquals("newData", newBuilder.entities(OoChildEntity::class.java).single().parentEntity.parentProperty)
  }

  @Test
  fun `add parent change data add parent again`() {
    val builder = createEmptyBuilder()
    val parent = builder addEntity OoParentEntity("data", MySource)

    val newBuilder = builder.toSnapshot().toBuilder()
    newBuilder addEntity OoChildWithNullableParentEntity(MySource) {
      this.parentEntity = parent.from(newBuilder)
    }
    newBuilder.modifyEntity(parent.from(newBuilder)) {
      this.entitySource = AnotherSource
    }
    newBuilder addEntity OoChildWithNullableParentEntity(AnotherSource) {
      this.parentEntity = parent.from(newBuilder)
    }

    val targetBuilder = builder.toSnapshot().toBuilder()
    targetBuilder.addDiff(newBuilder)

    val updatedParentEntity = targetBuilder
      .entities(OoChildWithNullableParentEntity::class.java)
      .single { it.entitySource is AnotherSource }
      .parentEntity
    assertEquals("data", updatedParentEntity!!.parentProperty)
  }
}