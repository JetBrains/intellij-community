// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.openapi.application.UI
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.awt.Component
import java.awt.Container
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.LayoutFocusTraversalPolicy
import javax.swing.SwingConstants

@TestApplication
internal class TabbedPaneWrapperTest {
  @Test
  fun `tab stops recursive focus requests from its ancestor`(): Unit = timeoutRunBlocking {
    val results = withContext(Dispatchers.UI) {
      var requests = 0
      val tab = createTab(JPanel())
      JPanel().apply {
        isFocusCycleRoot = true
        focusTraversalPolicy = object : LayoutFocusTraversalPolicy() {
          override fun getDefaultComponent(container: Container): Component {
            requests++
            return tab
          }
        }
        add(tab)
      }

      val defaultFocus = tab.requestDefaultFocus()
      tab.requestFocus()
      Triple(defaultFocus, tab.requestFocusInWindow(), requests)
    }

    assertThat(results.first).isTrue()
    assertThat(results.second).isTrue()
    assertThat(results.third).isEqualTo(3)
  }

  @Test
  fun `tab preserves focus requests through its ancestor`(): Unit = timeoutRunBlocking {
    val results = withContext(Dispatchers.UI) {
      var requests = 0
      val button = object : JButton() {
        override fun requestFocus() {
          requests++
        }
      }
      val tab = createTab(JPanel())
      JPanel().apply {
        isFocusCycleRoot = true
        focusTraversalPolicy = object : LayoutFocusTraversalPolicy() {
          override fun getDefaultComponent(container: Container): Component = button
        }
        add(tab)
        add(button)
      }

      val defaultFocus = tab.requestDefaultFocus()
      tab.requestFocus()
      Triple(defaultFocus, tab.requestFocusInWindow(), requests)
    }

    assertThat(results.first).isTrue()
    assertThat(results.second).isTrue()
    assertThat(results.third).isEqualTo(3)
  }

  @Test
  fun `tab forwards focus requests to its focusable child`(): Unit = timeoutRunBlocking {
    val results = withContext(Dispatchers.UI) {
      var requests = 0
      val button = object : JButton() {
        override fun requestFocusInWindow(): Boolean {
          requests++
          return true
        }
      }
      val tab = createTab(JPanel().apply { add(button) })

      val defaultFocus = tab.requestDefaultFocus()
      tab.requestFocus()
      Triple(defaultFocus, tab.requestFocusInWindow(), requests)
    }

    assertThat(results.first).isTrue()
    assertThat(results.second).isTrue()
    assertThat(results.third).isEqualTo(3)
  }

  @Test
  fun `tab permits another fallback request after an exception`() {
    val tab = timeoutRunBlocking(context = Dispatchers.UI) {
      createTab(object : JPanel() {
        override fun requestDefaultFocus(): Boolean = throw IllegalStateException("Focus request failed")
      })
    }

    repeat(2) {
      assertThrows<IllegalStateException> {
        timeoutRunBlocking(context = Dispatchers.UI) {
          tab.requestDefaultFocus()
        }
      }
    }
  }

  private fun createTab(component: JComponent): JComponent {
    val tabs = TabbedPaneWrapper.AsJTabbedPane(SwingConstants.TOP)
    tabs.addTab("Test", component)
    return tabs.getTabbedPane().selectedComponent as JComponent
  }
}
