// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests

import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.testEntities.entities.ChildWithNulls
import com.intellij.platform.workspace.storage.testEntities.entities.MySource
import com.intellij.platform.workspace.storage.testEntities.entities.ParentWithNulls
import com.intellij.platform.workspace.storage.testEntities.entities.modifyChildWithNulls
import com.intellij.platform.workspace.storage.testEntities.entities.parentEntity
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class WithNullsEntityTest {
  @Test
  fun `add parent and then child nullable`() {
    val builder = MutableEntityStorage.create()
    val parent = builder addEntity ParentWithNulls("Parent", MySource)

    val child = ChildWithNulls("data", MySource) {
      this.parentEntity = parent.builderFrom(builder)
    }

    assertEquals("Parent", child.parentEntity!!.child!!.parentEntity!!.parentData)
  }

  @Test
  fun `add parent and then child nullable 1`() {
    val builder = MutableEntityStorage.create()
    val child = builder addEntity ChildWithNulls("data", MySource)

    builder.modifyChildWithNulls(child) {
      this.parentEntity = ParentWithNulls("Parent", MySource)
    }

    assertEquals("Parent", child.parentEntity!!.child!!.parentEntity!!.parentData)
  }
}