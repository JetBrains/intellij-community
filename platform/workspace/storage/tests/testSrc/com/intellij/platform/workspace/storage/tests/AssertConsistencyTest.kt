// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.workspace.storage.tests

import com.intellij.platform.workspace.storage.impl.AbstractEntityStorage
import com.intellij.platform.workspace.storage.impl.assertConsistency
import com.intellij.platform.workspace.storage.testEntities.entities.addAssertConsistencyEntity
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

class AssertConsistencyTest {
  @Test
  fun `check should pass`() {
    val builder = createEmptyBuilder()
    builder.addAssertConsistencyEntity(true)
    (builder.toSnapshot() as AbstractEntityStorage).assertConsistency()
  }

  @Test
  fun `check should fail`() {
    val builder = createEmptyBuilder()
    builder.addAssertConsistencyEntity(false)
    (builder.toSnapshot() as AbstractEntityStorage).assertConsistency()
  }
}