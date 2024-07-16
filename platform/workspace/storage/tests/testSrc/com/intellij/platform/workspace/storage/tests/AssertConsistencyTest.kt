// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests

import com.intellij.platform.workspace.storage.impl.AbstractEntityStorage
import com.intellij.platform.workspace.storage.impl.assertConsistency
import com.intellij.platform.workspace.storage.testEntities.entities.AssertConsistencyEntity
import com.intellij.platform.workspace.storage.testEntities.entities.MySource
import org.junit.jupiter.api.Test

class AssertConsistencyTest {
  @Test
  fun `check should pass`() {
    val builder = createEmptyBuilder()
    builder addEntity AssertConsistencyEntity(true, MySource)
    (builder.toSnapshot() as AbstractEntityStorage).assertConsistency()
  }

  @Test
  fun `check should fail`() {
    val builder = createEmptyBuilder()
    builder addEntity AssertConsistencyEntity(false, MySource)
    (builder.toSnapshot() as AbstractEntityStorage).assertConsistency()
  }
}