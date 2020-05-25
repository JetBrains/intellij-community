// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage

import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Test

class ClassToIntConverterTest {
  @Test
  fun testIntRelease() {
    val firstValue = ClassToIntConverter.getInt(Test::class.java)
    assertEquals(0, firstValue)
    assertEquals(0, ClassToIntConverter.getInt(Test::class.java))
    ClassToIntConverter.releaseInt(firstValue)
    val secondValue = ClassToIntConverter.getInt(Assert::class.java)
    assertEquals(0, secondValue)
    assertEquals(1, ClassToIntConverter.getInt(Test::class.java))
  }
}