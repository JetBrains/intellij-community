/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.wm

import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.impl.WindowInfoImpl
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.assertions.doSerializerTest
import org.junit.ClassRule
import org.junit.Test
import java.awt.Rectangle

internal class WindowInfoTest {
  companion object {
    @JvmField
    @ClassRule
    val projectRule = ProjectRule()
  }

  @Test
  fun test() {
    val a = WindowInfoImpl()
    a.id = "a"
    doSerializerTest("""<window_info id="a" />""", a)

    a.isActive = true
    doSerializerTest("""<window_info active="true" id="a" />""", a)
  }

  @Test
  fun `anchor, isAutoHide`() {
    val a = WindowInfoImpl()
    a.id = "a"
    a.anchor = ToolWindowAnchor.BOTTOM
    a.isAutoHide = false
    doSerializerTest("""<window_info anchor="bottom" id="a" />""", a)

    a.isAutoHide = true
    doSerializerTest("""<window_info anchor="bottom" auto_hide="true" id="a" />""", a)
  }

  @Test
  fun `floatingBounds`() {
    val a = WindowInfoImpl()
    a.id = "a"
    a.floatingBounds = Rectangle(1, 42, 23, 4)
    doSerializerTest("""<window_info x="1" y="42" width="23" height="4" id="a" />""", a)
  }

  @Test
  fun `weight`() {
    val a = WindowInfoImpl()
    a.id = "a"
    a.weight = 0.3f
    doSerializerTest("""<window_info id="a" weight="0.3" />""", a)
    a.weight = WindowInfoImpl.DEFAULT_WEIGHT
    doSerializerTest("""<window_info id="a" />""", a)
  }
}