// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder

import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBTextField
import org.junit.Test
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
