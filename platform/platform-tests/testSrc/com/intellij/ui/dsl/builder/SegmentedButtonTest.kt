// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.builder

import com.intellij.testFramework.TestApplicationManager
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SegmentedButtonTest {

  @Before
  fun before() {
    TestApplicationManager.getInstance()
  }

  @Test
  fun testSelectedItem() {
    // Test buttons mode
    testSelectedItem((1..4).map { it.toString() })
    // Test combobox mode
    testSelectedItem((1..10).map { it.toString() })
  }

  private fun testSelectedItem(items: List<String>) {
    lateinit var segmentedButton: SegmentedButton<String>
    panel {
      row {
        segmentedButton = segmentedButton(items) { it }
      }
    }

    assertNull(segmentedButton.selectedItem)
    for (item in items) {
      segmentedButton.selectedItem = item
      assertEquals(segmentedButton.selectedItem, item)
    }
  }
}