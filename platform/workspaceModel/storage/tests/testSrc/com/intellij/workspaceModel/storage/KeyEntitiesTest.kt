// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage

import com.intellij.workspaceModel.storage.entities.test.api.AnotherSource
import com.intellij.workspaceModel.storage.entities.test.api.KeyChild
import com.intellij.workspaceModel.storage.entities.test.api.KeyParent
import com.intellij.workspaceModel.storage.entities.test.api.MySource
import com.intellij.workspaceModel.storage.impl.ChangeEntry
import com.intellij.workspaceModel.storage.impl.assertConsistency
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KeyEntitiesTest {
  @Test
  fun rbs() {
    val builder1 = createEmptyBuilder()
    builder1.addEntity(KeyParent("One", "Two", MySource) {
      this.children = listOf(KeyChild("One", AnotherSource) {})
    })

    val builder2 = createEmptyBuilder()
    builder2.addEntity(KeyParent("One", "Three", MySource) {
      this.children = listOf(KeyChild("AnotherOne", AnotherSource) {})
    })

    builder1.changeLog.clear()

    builder1.replaceBySource({ it is MySource }, builder2)

    builder1.assertConsistency()

    assertEquals("One", builder1.entities(KeyParent::class.java).single().children.single().data)
    assertTrue(builder1.changeLog.changeLog.values.single() is ChangeEntry.ReplaceEntity)
  }
}
