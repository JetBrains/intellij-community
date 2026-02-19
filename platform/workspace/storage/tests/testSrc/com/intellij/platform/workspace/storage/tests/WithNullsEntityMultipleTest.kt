// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests

import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.testEntities.entities.ChildWithNullsMultiple
import com.intellij.platform.workspace.storage.testEntities.entities.MySource
import com.intellij.platform.workspace.storage.testEntities.entities.ParentWithNullsMultiple
import com.intellij.platform.workspace.storage.testEntities.entities.modifyChildWithNullsMultiple
import com.intellij.platform.workspace.storage.testEntities.entities.parent
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class WithNullsEntityMultipleTest {
  @Test
  fun `add parent and then child nullable`() {
    val builder = MutableEntityStorage.create()
    val parent = builder addEntity ParentWithNullsMultiple("Parent", MySource)

    val child = ChildWithNullsMultiple("data", MySource) {
      this.parent = parent.builderFrom(builder)
    }

    assertEquals("Parent", child.parent!!.children.single().parent!!.parentData)
  }

  @Test
  fun `add parent and then child nullable 1`() {
    val builder = MutableEntityStorage.create()
    val child = builder addEntity ChildWithNullsMultiple("data", MySource)

    builder.modifyChildWithNullsMultiple(child) {
      this.parent = ParentWithNullsMultiple("Parent", MySource)
    }

    assertEquals("Parent", child.parent!!.children.single().parent!!.parentData)
  }
}