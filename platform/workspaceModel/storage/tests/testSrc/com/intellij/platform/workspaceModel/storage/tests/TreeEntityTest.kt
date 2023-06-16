// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspaceModel.storage.tests

import com.intellij.platform.workspaceModel.storage.testEntities.entities.MySource
import com.intellij.platform.workspaceModel.storage.testEntities.entities.TreeEntity
import junit.framework.TestCase.assertEquals
import org.junit.Test

class TreeEntityTest {
  @Test
  fun `tree entity`() {
    val entity = TreeEntity("Parent", MySource) {
      children = listOf(
        TreeEntity("childOne", MySource), TreeEntity("childTwo", MySource)
      )
    }

    assertEquals(2, entity.children.size)
  }

  @Test
  fun `tree entity add parent`() {
    val entity = TreeEntity("Parent", MySource) {
      children = listOf(
        TreeEntity("childTwo", MySource)
      )
    }

    assertEquals(1, entity.children.size)
    val child = TreeEntity("childOne", MySource) {
      parentEntity = entity
    }

    assertEquals(2, entity.children.size)
    assertEquals(child.parentEntity, entity)
  }

  @Test
  fun `tree entity add parent and child`() {
    val entity = TreeEntity("Parent", MySource)

    assertEquals(0, entity.children.size)
    val child = TreeEntity("childOne", MySource) {
      parentEntity = entity
      children = listOf(TreeEntity("deepChild", MySource))
    }

    assertEquals(1, entity.children.size)

    assertEquals(child.parentEntity, entity)
    assertEquals(1, child.children.size)
  }
}
