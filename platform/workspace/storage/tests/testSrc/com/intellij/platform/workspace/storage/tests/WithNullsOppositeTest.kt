// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests

import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.testEntities.entities.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class WithNullsOppositeTest {
  @Test
  fun `add parent and child nullable`() {
    val builder = MutableEntityStorage.create()
    val parent = builder addEntity ParentWithNullsOppositeMultiple("Parent", MySource)

    builder.modifyParentWithNullsOppositeMultiple(parent) {
      this.children = listOf(ChildWithNullsOppositeMultiple("data", MySource))
    }

    assertEquals("Parent", parent.children.single().parentEntity!!.parentData)
  }
}