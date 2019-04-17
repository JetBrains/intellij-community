// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.actions

import com.intellij.ide.DataManager
import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.util.Disposer
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.Alarm
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.AWTEvent
import java.awt.Cursor
import java.awt.Point
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JScrollPane
import javax.swing.SwingUtilities
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlin.math.sign

class FastMouseScrollComponent : IdeEventQueue.EventDispatcher {
  companion object {
    private const val DELAY_MS: Int = 30
  }

  private var handler: Handler? = null

  init {
    IdeEventQueue.getInstance().addDispatcher(this, ApplicationManager.getApplication())
  }

  override fun dispatch(event: AWTEvent): Boolean {
    if (event is KeyEvent && event.keyCode == KeyEvent.VK_ESCAPE && event.id == KeyEvent.KEY_PRESSED) {
      if (handler != null) {
        Disposer.dispose(handler!!)
        handler = null
        return true
      }
    }

    if (event is MouseEvent && event.button == MouseEvent.BUTTON2 &&
        !(event.isControlDown || event.isShiftDown || event.isMetaDown)) { // FIXME: event.isAltDown || event.isAltGraphDown? But isAltDown is passed even if button was up!?
      val component = SwingUtilities.getDeepestComponentAt(event.component, event.x, event.y) as? JComponent
      val editor = DataManager.getInstance().getDataContext(component).getData(CommonDataKeys.EDITOR) as? EditorEx
      val scrollPane = UIUtil.getParentOfType(JScrollPane::class.java, component)

      if (handler != null || editor != null || scrollPane != null) {
        if (event.id == MouseEvent.MOUSE_PRESSED) {
          if (handler != null) {
            Disposer.dispose(handler!!)
            handler = null
            return true
          }
          else {
            if (editor != null) {
              handler = EditorHandler(editor, event)
              handler!!.start()
              return true
            }

            if (scrollPane != null) {
              handler = ScrollPaneHandler(scrollPane, event)
              handler!!.start()
            }
          }
        }
        return true
      }
    }

    if (event is MouseEvent && event.id == MouseEvent.MOUSE_MOVED) {
      if (handler != null) {
        handler!!.mouseMoved(event)
      }
    }

    return false
  }

  private inner class EditorHandler(val editor: EditorEx, startEvent: MouseEvent)
    : Handler(editor.component, startEvent) {

    override fun scrollComponent(delta: Int) {
      editor.scrollingModel.disableAnimation()
      editor.scrollingModel.scrollVertically(editor.scrollingModel.verticalScrollOffset + delta)
      editor.scrollingModel.enableAnimation()
    }

    override fun setCursor(cursor: Cursor?) {
      editor.setCustomCursor(this, cursor)
    }
  }

  private inner class ScrollPaneHandler(val scrollPane: JScrollPane, startEvent: MouseEvent)
    : Handler(scrollPane, startEvent) {

    override fun scrollComponent(delta: Int) {
      scrollPane.verticalScrollBar.value = scrollPane.verticalScrollBar.value + delta
    }

    override fun setCursor(cursor: Cursor?) {
      scrollPane.cursor = cursor
    }
  }

  private abstract inner class Handler(val component: JComponent, startEvent: MouseEvent) : Disposable {
    private val startPoint: Point = RelativePoint(startEvent).getPoint(component)
    private val alarm = Alarm()

    private var currentSpeed: Double = 0.0 // pixels to scroll per second
    private var lastEventTimestamp: Long = Long.MAX_VALUE
    private var lastEventRemainder: Double = 0.0

    fun start() {
      setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR))
      scheduleScrollEvent()
    }

    override fun dispose() {
      setCursor(null)
      Disposer.dispose(alarm)
    }

    fun mouseMoved(event: MouseEvent) {
      val currentPoint = RelativePoint(event).getPoint(component)
      currentSpeed = calcSpeed(currentPoint.y - startPoint.y)

      if (currentSpeed != 0.0 && lastEventTimestamp == Long.MAX_VALUE) {
        lastEventTimestamp = System.currentTimeMillis()
        lastEventRemainder = 0.0
      }
      if (currentSpeed == 0.0) {
        lastEventTimestamp = Long.MAX_VALUE
        lastEventRemainder = 0.0
      }

      val cursor = when {
        currentSpeed > 0 -> Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR)
        currentSpeed < 0 -> Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR)
        else -> Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)
      }
      setCursor(cursor)
    }

    /**
     * FIXME: Reuse curve from a browser?
     *        But we'll need to move it to separate file and add license.
     *        MPL2 is per-file and can't be just replaced with Apache2 IIRC (though, can be <used> in an Apache2 project).
     *  Gecko: https://github.com/mozilla/gecko-dev - 'autoScrollLoop' and '_accelerate' methods in /toolkit/content/widgets/browser.xml
     *  Chromium - AutoscrollController::HandleMouseMoveForMiddleClickAutoscroll ?
     *  WebKit - RenderBox::calculateAutoscrollDirection ?
     */
    private fun calcSpeed(delta: Int): Double {
      val value = delta.absoluteValue - JBUI.scale(10)
      if (value < 1) return 0.0

      val square = value.toDouble()
      val scrollSpeed = square * square / 40 / JBUI.scale(1f)
      if (scrollSpeed < 1) return 0.0

      return scrollSpeed * delta.sign
    }

    private fun doScroll() {
      if (currentSpeed != 0.0) {
        val timestamp = System.currentTimeMillis()
        val timeDelta = (timestamp - lastEventTimestamp).coerceAtLeast(0)
        val pixels = lastEventRemainder + currentSpeed * timeDelta / 1000
        val delta = pixels.roundToInt()

        lastEventTimestamp = timestamp
        lastEventRemainder = pixels - delta

        scrollComponent(delta)
      }

      scheduleScrollEvent()
    }

    private fun scheduleScrollEvent() {
      alarm.addRequest(this@Handler::doScroll, DELAY_MS)
    }

    protected abstract fun scrollComponent(delta: Int)
    protected abstract fun setCursor(cursor: Cursor?)
  }
}