// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui

import com.intellij.openapi.application.UI
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.UiInterceptors
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.awt.Point
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities
import kotlin.test.assertEquals

@TestApplication
internal class ComponentValidatorTest {
  @ParameterizedTest
  @CsvSource("200, 0", "0, 50", "200, 50")
  fun `popup position follows the visible area`(x: Int, y: Int): Unit = timeoutRunBlocking {
    val (locations, viewPosition) = withContext(Dispatchers.UI) {
      val textArea = JBTextArea(List(30) { "value ".repeat(100) }.joinToString("\n"), 6, 36)
      val scrollPane = JBScrollPane(textArea)
      scrollPane.setSize(400, 150)
      scrollPane.doLayout()
      scrollPane.viewport.doLayout()

      val disposable = Disposer.newDisposable()
      try {
        val locations = mutableListOf<Point>()
        UiInterceptors.registerPersistent(disposable, object : UiInterceptors.PersistentUiInterceptor<JBPopup>(JBPopup::class.java) {
          override fun shouldIntercept(component: JBPopup): Boolean = true

          override fun doIntercept(component: JBPopup, owner: RelativePoint?) {
            val location = requireNotNull(owner)
            locations.add(SwingUtilities.convertPoint(location.originalComponent, location.originalPoint, scrollPane.viewport))
            Disposer.register(disposable, component)
          }
        })

        val validator = ComponentValidator(disposable).installOn(textArea)
        validator.updateInfo(ValidationInfo("Invalid arguments", textArea))
        showPopup(textArea)

        scrollPane.viewport.viewPosition = Point(x, y)
        showPopup(textArea)

        locations.toList() to scrollPane.viewport.viewPosition
      }
      finally {
        Disposer.dispose(disposable)
      }
    }

    assertEquals(Point(x, y), viewPosition)
    assertEquals(2, locations.size)
    assertEquals(locations[0], locations[1])
  }

  private fun showPopup(textArea: JBTextArea) {
    val visibleArea = textArea.visibleRect
    textArea.dispatchEvent(MouseEvent(textArea, MouseEvent.MOUSE_ENTERED, 0, 0, visibleArea.x, visibleArea.y, 0, false))
  }
}
