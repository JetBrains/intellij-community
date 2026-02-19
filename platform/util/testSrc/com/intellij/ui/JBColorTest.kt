// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import junit.framework.TestCase
import javax.swing.UIManager

class JBColorTest: TestCase() {
  fun testColorResolving() {
    UIManager.put("testColor", JBColor.BLUE)

    val color = JBColor.namedColor("testColor")
    val nonExistent = JBColor.namedColor("nonexistentcolor29039")
    val colorOrNull = JBColor.namedColorOrNull("testColor")
    val nonExistentAsNull = JBColor.namedColorOrNull("nonexistentcolor29039")

    assertEquals(color, colorOrNull)
    assertEquals(nonExistent, Gray.TRANSPARENT)
    assertNull(nonExistentAsNull)
  }
}