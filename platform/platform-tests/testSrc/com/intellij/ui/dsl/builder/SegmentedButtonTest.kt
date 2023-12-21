// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.builder

import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.ui.DialogPanel
import com.intellij.testFramework.TestApplicationManager
import com.intellij.ui.dsl.UiDslException
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.assertThrows
import javax.swing.JComboBox
import javax.swing.JLabel
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.fail

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
        segmentedButton = segmentedButton(items) { text = it }
      }
    }

    assertNull(segmentedButton.selectedItem)
    for (item in items) {
      segmentedButton.selectedItem = item
      assertEquals(segmentedButton.selectedItem, item)
    }
  }

  private var rendererText: @Nls String? = null
  private var rendererToolTip: @Nls String? = null

  @Test
  fun testPresentations() {
    lateinit var segmentedButton: SegmentedButton<Int>
    val panel = panel {
      row {
        segmentedButton = segmentedButton(listOf(1, 2, 3)) {
          text = rendererText ?: "item $it"
          toolTipText = rendererToolTip
        }
      }
    }
    rendererText = "item 1"
    validate(panel)

    rendererText = "item updated"
    segmentedButton.update(1)
    validate(panel)

    rendererToolTip = "item tooltip"
    segmentedButton.update(1)
    validate(panel)
  }

  private fun validate(panel: DialogPanel) {
    val button1 = UIUtil.findComponentOfType(panel, ActionButtonWithText::class.java) ?: fail()
    val presentation = button1.presentation

    assertEquals(presentation.text, rendererText)
    assertEquals(presentation.description, rendererToolTip)
  }

  @Test
  fun testEmptyPresentation() {
    panel {
      row {
        segmentedButton(listOf(1)) { text = "$it" }
      }
    }

    assertThrows<UiDslException> {
      panel {
        row {
          segmentedButton(listOf(1)) { }
        }
      }
    }
  }

  @Test
  fun testDisabledItem() {
    lateinit var segmentedButton: SegmentedButton<Int>
    var enabledItem1 = true
    panel {
      row {
        segmentedButton = segmentedButton(listOf(1, 2)) {
          text = "$it"
          enabled = it != 1 || enabledItem1
        }
      }
    }

    segmentedButton.selectedItem = 2
    enabledItem1 = false
    segmentedButton.update(1)
    assertEquals(segmentedButton.selectedItem, 2)

    segmentedButton.selectedItem = 1
    assertEquals(segmentedButton.selectedItem, 2, "Disabled item cannot be selected")

    segmentedButton.selectedItem = 3
    assertEquals(segmentedButton.selectedItem, 2, "Absent item cannot be selected")

    enabledItem1 = true
    segmentedButton.update(1)
    assertEquals(segmentedButton.selectedItem, 2)

    segmentedButton.selectedItem = null
    assertEquals(segmentedButton.selectedItem, null)

    segmentedButton.selectedItem = 1
    assertEquals(segmentedButton.selectedItem, 1)
  }

  @Test
  fun testLabelFor() {
    val label = JLabel("label")
    val panel = panel {
      row(label) {
        segmentedButton(listOf(1, 2, 3)) { text = "$it" }
      }
    }

    val button = UIUtil.findComponentOfType(panel, ActionButtonWithText::class.java) ?: fail()
    val segmentedButtonComponent = button.parent
    assertEquals(segmentedButtonComponent, label.labelFor)
  }

  @Test
  fun testLabelForSegmentedButtonAsComboBox() {
    val label = JLabel("label")
    val panel = panel {
      row(label) {
        segmentedButton(listOf(1, 2, 3)) { text = "$it" }
          .apply {
            maxButtonsCount(1) // Segmented button should now use combo box as its component
          }
      }
    }

    val comboBox = UIUtil.findComponentOfType(panel, JComboBox::class.java) ?: fail()
    assertEquals(comboBox, label.labelFor)
  }

  @Test
  fun testLabelForWhenChangingComponent() {
    val label = JLabel("label")
    lateinit var segmentedButton: SegmentedButton<Int>
    val panel = panel {
      row(label) {
        segmentedButton = segmentedButton(listOf(1, 2, 3)) { text = "$it" }
          .apply {
            maxButtonsCount(3)
          }
      }
    }

    val button = UIUtil.findComponentOfType(panel, ActionButtonWithText::class.java) ?: fail()
    val segmentedButtonComponent = button.parent
    assertEquals(segmentedButtonComponent, label.labelFor)

    segmentedButton.items = listOf(1, 2, 3, 4) // Exceeds maxButtonsCount and forces a rebuild of the SegmentedButton

    val comboBox = UIUtil.findComponentOfType(panel, JComboBox::class.java) ?: fail()
    assertEquals(comboBox, label.labelFor)
  }
}