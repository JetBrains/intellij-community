// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests

import com.intellij.platform.workspace.storage.testEntities.entities.*
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class WithNullsEntityMultipleTest {
  @Test
  fun `add parent and then child nullable`() {
    val parent = ParentWithNullsMultiple("Parent", MySource) {
    }
    val builder = MutableEntityStorage.create()
    builder.addEntity(parent)

    val child = ChildWithNullsMultiple("data", MySource) {
      this.parent = parent
    }

    assertEquals("Parent", child.parent!!.children.single().parent!!.parentData)
  }

  @Test
  fun `add parent and then child nullable 1`() {
    val child = ChildWithNullsMultiple("data", MySource) {
    }
    val builder = MutableEntityStorage.create()
    builder.addEntity(child)

    builder.modifyEntity(child) {
      this.parent = ParentWithNullsMultiple("Parent", MySource) {
      }
    }

    assertEquals("Parent", child.parent!!.children.single().parent!!.parentData)
  }
}