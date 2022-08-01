// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder

import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBTextField
import org.junit.Test
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.text.AbstractDocument
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaceholderTest {

  @Test
  fun testRemovingListeners() {
    val placeholderTestData = PlaceholderTestData()

    with(placeholderTestData) {
      val validateCallbacksCount = panel.validateCallbacks.size
      val documentListenersCount = getDocumentListenersCount()

      placeholder.component = subPanel

      assertTrue(panel.validateCallbacks.size > validateCallbacksCount)
      assertTrue(getDocumentListenersCount() > documentListenersCount)

      placeholder.component = null

      assertEquals(panel.validateCallbacks.size, validateCallbacksCount)
      assertEquals(getDocumentListenersCount(), documentListenersCount)
    }

    Disposer.dispose(placeholderTestData.disposable)
  }


  @Test
  fun testMovingComponent() {
    lateinit var placeholder1: Placeholder
    lateinit var placeholder2: Placeholder
    val component1 = JLabel("1")
    val component2 = JLabel("2")

    fun testConsistency(placeholder1Component: JComponent?, placeholder2Component: JComponent?, invokeLater: Boolean) {
      assertEquals(placeholder1.component, placeholder1Component)
      assertEquals(placeholder2.component, placeholder2Component)

      val runnable = Runnable {
        assertEquals(component1.hierarchyListeners.size, if (component1.parent == null) 0 else 1)
        assertEquals(component2.hierarchyListeners.size, if (component2.parent == null) 0 else 1)
      }

      if (invokeLater) {
        SwingUtilities.invokeAndWait(runnable)
      } else {
        runnable.run()
      }
    }

    val panel = panel {
      row {
        placeholder1 = placeholder()
        placeholder1.component = component1

        placeholder2 = placeholder()
      }
    }

    testConsistency(component1, null, false)

    placeholder1.component = null
    testConsistency(null, null, true)

    placeholder2.component = component2
    testConsistency(null, component2, false)

    placeholder1.component = component2
    // placeholder2.component must be updated
    testConsistency(component2, null, true)

    panel.remove(component2)
    // placeholder1.component must be updated
    testConsistency(null, null, true)

    placeholder1.component = component1
    testConsistency(component1, null, true)
    JPanel().add(panel) // force hierarchyEvent with another parent
    testConsistency(component1, null, true)
  }

  @Test
  fun testApply_Reset_IsModified() {
    val placeholderTestData = PlaceholderTestData()

    with(placeholderTestData) {
      placeholder.component = subPanel
      // Disable UI validation, because there is no application and popups don't work
      textField.putClientProperty("JComponent.componentValidator", null)

      assertFalse(panel.isModified())
      assertTrue(isValid())

      textField.text = "7"

      assertTrue(panel.isModified())
      assertTrue(isValid())
      assertEquals(textFieldValue, 5)

      panel.apply()

      assertFalse(panel.isModified())
      assertTrue(isValid())
      assertEquals(textFieldValue, 7)

      textField.text = "0"

      assertTrue(panel.isModified())
      assertFalse(isValid())

      panel.reset()

      assertFalse(panel.isModified())
      assertTrue(isValid())
      assertEquals(textField.text, "7")

      textField.text = "0"
      placeholder.component = null

      assertFalse(panel.isModified())
      assertTrue(isValid())
    }

    Disposer.dispose(placeholderTestData.disposable)
  }
}

internal class PlaceholderTestData {

  val disposable = Disposer.newDisposable()

  var textFieldValue = 5

  lateinit var textField: JBTextField

  val subPanel = panel {
    row {
      textField = intTextField(0..100)
        .bindIntText(::textFieldValue)
        .errorOnApply("Int should be positive") {
          (it.text.toIntOrNull() ?: 0) <= 0
        }.component
    }
  }

  lateinit var placeholder: Placeholder

  val panel = panel {
    row {
      placeholder = placeholder()
    }
  }

  init {
    panel.registerValidators(disposable)
  }

  fun getDocumentListenersCount(): Int {
    return (textField.document as AbstractDocument).documentListeners.size
  }

  fun isValid(): Boolean {
    return panel.validateCallbacks.mapNotNull { it() }.isEmpty()
  }
}
