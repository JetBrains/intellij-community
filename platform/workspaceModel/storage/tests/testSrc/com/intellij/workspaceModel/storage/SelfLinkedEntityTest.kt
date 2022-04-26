// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage

import com.intellij.workspaceModel.storage.entities.test.api.MySource
import com.intellij.workspaceModel.storage.entities.test.api.SelfLinkedEntity
import com.intellij.workspaceModel.storage.entities.test.api.children
import junit.framework.TestCase.assertEquals
import org.junit.Test

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
