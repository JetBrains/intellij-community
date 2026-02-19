// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.builder

import com.intellij.testFramework.TestApplicationManager
import com.intellij.ui.TitledSeparator
import com.intellij.util.ui.UIUtil
import org.junit.Before
import org.junit.Test
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.KeyStroke
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CollapsibleRowTest {

  @Before
  fun before() {
    TestApplicationManager.getInstance()
  }

  @Test
  fun testMnemonic() {
    lateinit var collapsibleRow: CollapsibleRow
    val panel = panel {
      collapsibleRow = collapsibleGroup("&Group") {
      }
    }
    val titledSeparator = UIUtil.findComponentOfType(panel, TitledSeparator::class.java)
    assertNotNull(titledSeparator)
    val inputMap = titledSeparator.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)

    assertNotNull(inputMap.get(KeyStroke.getKeyStroke(KeyEvent.VK_G, InputEvent.ALT_DOWN_MASK, false)))

    collapsibleRow.setTitle("G&roup")
    assertNull(inputMap.get(KeyStroke.getKeyStroke(KeyEvent.VK_G, InputEvent.ALT_DOWN_MASK, false)))
    assertNotNull(inputMap.get(KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.ALT_DOWN_MASK, false)))
  }
}