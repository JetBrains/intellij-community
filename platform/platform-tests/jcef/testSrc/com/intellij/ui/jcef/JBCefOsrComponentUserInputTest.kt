// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import org.cef.browser.CefBrowser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.KeyboardFocusManager
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.lang.reflect.Proxy
import javax.swing.JPanel

/**
 * Tests which input of the off-screen component counts as a user input for the clipboard bridge.
 */
@TestApplication
class JBCefOsrComponentUserInputTest {
  @Test
  fun `a mouse press and a press of a key that is not a modifier or Escape and does not repeat are user input`() {
    runInEdtAndWait {
      val component = JBCefOsrComponent(false)
      component.setBrowser(Proxy.newProxyInstance(javaClass.classLoader, arrayOf(CefBrowser::class.java)) { _, method, _ ->
        if (method.returnType == Boolean::class.javaPrimitiveType) false else null
      } as CefBrowser)
      var inputs = 0
      component.setUserInputListener { inputs++ }

      fun mouse(id: Int) = component.dispatchEvent(MouseEvent(component, id, 0, InputEvent.BUTTON1_DOWN_MASK, 5, 5, 1, false, MouseEvent.BUTTON1))
      // The component has no focus in the test, so the key events go to it directly, as the focus manager passes them to the focus owner.
      fun key(id: Int, code: Int) = KeyboardFocusManager.getCurrentKeyboardFocusManager()
        .redispatchEvent(component, KeyEvent(component, id, 0, 0, code, KeyEvent.CHAR_UNDEFINED))

      mouse(MouseEvent.MOUSE_PRESSED)
      assertEquals(1, inputs)
      mouse(MouseEvent.MOUSE_RELEASED)
      assertEquals(1, inputs)
      key(KeyEvent.KEY_PRESSED, KeyEvent.VK_A)
      assertEquals(2, inputs)
      key(KeyEvent.KEY_PRESSED, KeyEvent.VK_A)
      assertEquals(2, inputs, "a repeated press")
      key(KeyEvent.KEY_RELEASED, KeyEvent.VK_A)
      key(KeyEvent.KEY_PRESSED, KeyEvent.VK_A)
      assertEquals(3, inputs)
      key(KeyEvent.KEY_PRESSED, KeyEvent.VK_SHIFT)
      key(KeyEvent.KEY_PRESSED, KeyEvent.VK_CONTROL)
      key(KeyEvent.KEY_PRESSED, KeyEvent.VK_META)
      assertEquals(3, inputs, "modifiers")
      key(KeyEvent.KEY_PRESSED, KeyEvent.VK_ESCAPE)
      assertEquals(3, inputs, "Escape")
    }
  }

  @Test
  fun `a mouse press forgets the keys that are down, whose releases may have gone elsewhere`() {
    val filter = JBCefUserInputFilter()
    val source = JPanel()
    fun key(id: Int) = KeyEvent(source, id, 0, 0, KeyEvent.VK_A, KeyEvent.CHAR_UNDEFINED)

    assertTrue(filter.isUserInput(key(KeyEvent.KEY_PRESSED)))
    // The release of the key went to another component, so the next press looks like a repeated one.
    assertFalse(filter.isUserInput(key(KeyEvent.KEY_PRESSED)))
    assertTrue(filter.isUserInput(MouseEvent(source, MouseEvent.MOUSE_PRESSED, 0, InputEvent.BUTTON1_DOWN_MASK, 5, 5, 1, false, MouseEvent.BUTTON1)))
    assertTrue(filter.isUserInput(key(KeyEvent.KEY_PRESSED)))
  }

  @Test
  fun `the component records a user input before the browser gets it`() {
    runInEdtAndWait {
      val component = JBCefOsrComponent(false)
      var inputs = 0
      // The number of the inputs that the component recorded when the browser got each mouse press and key press.
      val inputsAtSend = ArrayList<Int>()
      component.setBrowser(Proxy.newProxyInstance(javaClass.classLoader, arrayOf(CefBrowser::class.java)) { _, method, args ->
        if (method.name == "sendMouseEvent" || method.name == "sendKeyEvent") {
          val id = (args[0] as InputEvent).id
          if (id == MouseEvent.MOUSE_PRESSED || id == KeyEvent.KEY_PRESSED) inputsAtSend.add(inputs)
        }
        if (method.returnType == Boolean::class.javaPrimitiveType) false else null
      } as CefBrowser)
      component.setUserInputListener { inputs++ }

      component.dispatchEvent(MouseEvent(component, MouseEvent.MOUSE_PRESSED, 0, InputEvent.BUTTON1_DOWN_MASK, 5, 5, 1, false, MouseEvent.BUTTON1))
      KeyboardFocusManager.getCurrentKeyboardFocusManager()
        .redispatchEvent(component, KeyEvent(component, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_A, KeyEvent.CHAR_UNDEFINED))
      assertEquals(listOf(1, 2), inputsAtSend)
    }
  }
}
