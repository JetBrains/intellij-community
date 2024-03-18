// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests

import com.intellij.platform.workspace.storage.testEntities.entities.MySource
import com.intellij.platform.workspace.storage.testEntities.entities.SelfLinkedEntity
import com.intellij.platform.workspace.storage.testEntities.entities.children
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SelfLinkedEntityTest {
  @Test
  fun `simple test`() {
    val builder = createEmptyBuilder()
    val parent = SelfLinkedEntity(MySource) {
      this.parentEntity = null
    }
    SelfLinkedEntity(MySource) {
      this.parentEntity = parent
    }
    SelfLinkedEntity(MySource) {
      this.parentEntity = parent
    }
    builder.addEntity(parent)


    val foundParent = builder.entities(SelfLinkedEntity::class.java).single { it.parentEntity == null }
    assertEquals(2, foundParent.children.size)
  }
}
