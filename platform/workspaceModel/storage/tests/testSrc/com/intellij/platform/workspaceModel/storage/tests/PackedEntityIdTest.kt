// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.workspaceModel.storage.tests

import com.intellij.workspaceModel.storage.impl.arrayId
import com.intellij.workspaceModel.storage.impl.clazz
import com.intellij.workspaceModel.storage.impl.createEntityId
import org.junit.Assert.assertEquals
import org.junit.Test

class PackedEntityIdTest {
  @Test
  fun `one and one`() {
    val packedEntityId = createEntityId(1, 1)
    assertEquals(1, packedEntityId.arrayId)
    assertEquals(1, packedEntityId.clazz)
  }

  @Test
  fun `one and two`() {
    val arrayId = 1
    val clazz = 2
    val packedEntityId = createEntityId(arrayId, clazz)
    assertEquals(arrayId, packedEntityId.arrayId)
    assertEquals(clazz, packedEntityId.clazz)
  }
}