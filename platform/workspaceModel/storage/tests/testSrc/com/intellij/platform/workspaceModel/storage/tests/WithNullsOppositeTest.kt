// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspaceModel.storage.tests

import com.intellij.platform.workspaceModel.storage.testEntities.entities.*
import com.intellij.workspaceModel.storage.MutableEntityStorage
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class WithNullsOppositeTest {
  @Test
  fun `add parent and child nullable`() {
    val parent = ParentWithNullsOppositeMultiple("Parent", MySource) {
    }
    val builder = MutableEntityStorage.create()
    builder.addEntity(parent)

    builder.modifyEntity(parent) {
      this.children = listOf(ChildWithNullsOppositeMultiple("data", MySource) {
      })
    }

    assertEquals("Parent", parent.children.single().parentEntity!!.parentData)
  }
}