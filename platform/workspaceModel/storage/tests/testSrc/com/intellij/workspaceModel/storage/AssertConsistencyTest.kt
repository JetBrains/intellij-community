// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage

import com.intellij.workspaceModel.storage.entities.test.api.addAssertConsistencyEntity
import com.intellij.workspaceModel.storage.impl.AbstractEntityStorage
import com.intellij.workspaceModel.storage.impl.assertConsistency
import org.junit.Test

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