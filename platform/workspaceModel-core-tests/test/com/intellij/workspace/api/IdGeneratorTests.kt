// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api

import org.junit.Assert.assertEquals
import org.junit.Test

class IdGeneratorTests {
  @Test
  fun `test few generator executions`() {
    val generator = IdGenerator.startGenerator()

    assertEquals(1, generator.getId())
    assertEquals(2, generator.getId())
    assertEquals(3, generator.getId())
    assertEquals(4, generator.getId())
  }

  @Test
  fun `test generator adjustment to lower value`() {
    val generator = IdGenerator.startGenerator()
    generator.getId()  // update to 2
    generator.getId()  // update to 3
    generator.getId()  // update to 4

    generator.adjustId(3)
    assertEquals(4, generator.getId())
  }

  @Test
  fun `test generator adjustment to eq value`() {
    val generator = IdGenerator.startGenerator()
    generator.getId()  // update to 2
    generator.getId()  // update to 3
    generator.getId()  // update to 4

    generator.adjustId(4)
    assertEquals(5, generator.getId())
  }

  @Test
  fun `test generator adjustment to greater value`() {
    val generator = IdGenerator.startGenerator()
    generator.getId()  // update to 2
    generator.getId()  // update to 3
    generator.getId()  // update to 4

    generator.adjustId(6)
    assertEquals(7, generator.getId())
  }
}