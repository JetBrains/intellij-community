// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests

import com.intellij.platform.workspace.storage.testEntities.entities.*
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class WithNullsEntityTest {
  @Test
  fun `add parent and then child nullable`() {
    val parent = ParentWithNulls("Parent", MySource) {
    }
    val builder = MutableEntityStorage.create()
    builder.addEntity(parent)

    val child = ChildWithNulls("data", MySource) {
      this.parentEntity = parent
    }

    assertEquals("Parent", child.parentEntity!!.child!!.parentEntity!!.parentData)
  }

  @Test
  fun `add parent and then child nullable 1`() {
    val child = ChildWithNulls("data", MySource) {
    }
    val builder = MutableEntityStorage.create()
    builder.addEntity(child)

    builder.modifyEntity(child) {
      this.parentEntity = ParentWithNulls("Parent", MySource) {
      }
    }

    assertEquals("Parent", child.parentEntity!!.child!!.parentEntity!!.parentData)
  }
}