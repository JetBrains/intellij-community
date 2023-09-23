// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests

import com.intellij.platform.workspace.storage.impl.ChangeEntry
import com.intellij.platform.workspace.storage.impl.assertConsistency
import com.intellij.platform.workspace.storage.testEntities.entities.AnotherSource
import com.intellij.platform.workspace.storage.testEntities.entities.KeyChild
import com.intellij.platform.workspace.storage.testEntities.entities.KeyParent
import com.intellij.platform.workspace.storage.testEntities.entities.MySource
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test
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
