// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.ui

import com.intellij.util.ConcurrencyUtil
import com.intellij.util.ui.EdtInvocationManager
import org.fest.swing.awt.AWT
import org.fest.swing.core.*
import org.fest.swing.core.Robot
import org.fest.swing.edt.GuiActionRunner
import org.fest.swing.edt.GuiQuery
import org.fest.swing.hierarchy.ComponentHierarchy
import org.fest.swing.timing.Pause
import java.awt.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.swing.JComponent
import javax.swing.JPopupMenu
import javax.swing.SwingUtilities

// It is a copy-paster from testGuiFramework (with several changes)
internal class IftSmartWaitRobot : Robot {

  override fun moveMouse(component: Component) {
    var where = AWT.visibleCenterOf(component)
    if (component is JComponent) {
      Scrolling.scrollToVisible(this, component)
      where = AWT.visibleCenterOf(component)
    }
    moveMouse(component, where)
  }

  override fun moveMouse(component: Component, point: Point) {
    val translatedPoint = performOnEdt { AWT.translate(component, point.x, point.y) }
    requireNotNull(translatedPoint) { "Translated point should be not null" }
    moveMouse(translatedPoint.x, translatedPoint.y)
  }

  override fun moveMouse(point: Point) {
    moveMouse(point.x, point.y)
  }

  override fun click(component: Component) {
    moveMouse(component)
    basicRobot.click(component)
  }

  override fun click(component: Component, mouseButton: MouseButton) {
    moveMouse(component)
    basicRobot.click(component, mouseButton)
  }

  override fun click(component: Component, mouseButton: MouseButton, counts: Int) {
    moveMouse(component)
    basicRobot.click(component, mouseButton, counts)
  }

  override fun click(component: Component, point: Point) {
    moveMouse(component, point)
    basicRobot.click(component, point)
  }

  override fun showWindow(window: Window) {
    basicRobot.showWindow(window)
  }

  override fun showWindow(window: Window, dimension: Dimension) {
    basicRobot.showWindow(window, dimension)
  }

  override fun showWindow(window: Window, dimension: Dimension?, p2: Boolean) {
    basicRobot.showWindow(window, dimension, p2)
  }

  override fun isActive(): Boolean = basicRobot.isActive


  override fun pressAndReleaseKey(p0: Int, vararg p1: Int) {
    basicRobot.pressAndReleaseKey(p0, *p1)
  }

  override fun showPopupMenu(component: Component): JPopupMenu =
    basicRobot.showPopupMenu(component)

  override fun showPopupMenu(component: Component, point: Point): JPopupMenu =
    basicRobot.showPopupMenu(component, point)

  override fun jitter(component: Component) {
    basicRobot.jitter(component)
  }

  override fun jitter(component: Component, point: Point) {
    basicRobot.jitter(component, point)
  }

  override fun pressModifiers(p0: Int) {
    basicRobot.pressModifiers(p0)
  }

  override fun pressMouse(mouseButton: MouseButton) {
    basicRobot.pressMouse(mouseButton)
  }

  override fun pressMouse(component: Component, point: Point) {
    basicRobot.pressMouse(component, point)
  }

  override fun pressMouse(component: Component, point: Point, mouseButton: MouseButton) {
    basicRobot.pressMouse(component, point, mouseButton)
  }

  override fun pressMouse(point: Point, mouseButton: MouseButton) {
    basicRobot.pressMouse(point, mouseButton)
  }

  override fun hierarchy(): ComponentHierarchy =
    basicRobot.hierarchy()

  override fun releaseKey(p0: Int) {
    basicRobot.releaseKey(p0)
  }

  override fun isDragging(): Boolean = basicRobot.isDragging

  override fun printer(): ComponentPrinter = basicRobot.printer()

  override fun type(char: Char) {
    basicRobot.type(char)
  }

  override fun type(char: Char, component: Component) {
    basicRobot.type(char, component)
  }

  override fun requireNoJOptionPaneIsShowing() {
    basicRobot.requireNoJOptionPaneIsShowing()
  }

  override fun cleanUp() {
    basicRobot.cleanUp()
  }

  override fun releaseMouse(mouseButton: MouseButton) {
    basicRobot.releaseMouse(mouseButton)
  }

  override fun pressKey(p0: Int) {
    basicRobot.pressKey(p0)
  }

  override fun settings(): Settings = basicRobot.settings()

  override fun enterText(text: String) {
    basicRobot.enterText(text)
  }

  override fun enterText(text: String, component: Component) {
    basicRobot.enterText(text, component)
  }

  override fun releaseMouseButtons() {
    basicRobot.releaseMouseButtons()
  }

  override fun rightClick(component: Component) {
    basicRobot.rightClick(component)
  }

  override fun focus(component: Component) {
    basicRobot.focus(component)
  }

  override fun doubleClick(component: Component) {
    basicRobot.doubleClick(component)
  }

  override fun cleanUpWithoutDisposingWindows() {
    basicRobot.cleanUpWithoutDisposingWindows()
  }

  override fun isReadyForInput(component: Component): Boolean = basicRobot.isReadyForInput(component)

  override fun focusAndWaitForFocusGain(component: Component) {
    basicRobot.focusAndWaitForFocusGain(component)
  }

  override fun releaseModifiers(p0: Int) {
    basicRobot.releaseModifiers(p0)
  }

  override fun findActivePopupMenu(): JPopupMenu? {
    return basicRobot.findActivePopupMenu()
  }

  override fun rotateMouseWheel(component: Component, p1: Int) {
    basicRobot.rotateMouseWheel(component, p1)
  }

  override fun rotateMouseWheel(p0: Int) {
    basicRobot.rotateMouseWheel(p0)
  }

  override fun pressAndReleaseKeys(vararg p0: Int) {
    basicRobot.pressAndReleaseKeys(*p0)
  }

  override fun finder(): ComponentFinder = basicRobot.finder()

  private val basicRobot: BasicRobot = BasicRobot.robotWithCurrentAwtHierarchyWithoutScreenLock() as BasicRobot

  init {
    settings().delayBetweenEvents(10)
  }

  private val waitConst = 30L
  private var myAwareClick: Boolean = false

  override fun waitForIdle() {
    if (myAwareClick) {
      Thread.sleep(50)
    }
    else {
      Pause.pause(waitConst)
      if (!SwingUtilities.isEventDispatchThread()) EdtInvocationManager.getInstance().invokeAndWait({ })
    }
  }

  override fun close(w: Window) {
    basicRobot.close(w)
    basicRobot.waitForIdle()
  }

  //smooth mouse move
  override fun moveMouse(x: Int, y: Int) {
    val pauseConstMs = settings().delayBetweenEvents().toLong()
    val n = 20
    val start = MouseInfo.getPointerInfo().location
    val dx = (x - start.x) / n.toDouble()
    val dy = (y - start.y) / n.toDouble()
    for (step in 1..n) {
      try {
        Pause.pause(pauseConstMs)
      }
      catch (e: InterruptedException) {
        e.printStackTrace()
      }

      basicRobot.moveMouse(
        (start.x + dx * ((Math.log(1.0 * step / n) - Math.log(1.0 / n)) * n / (0 - Math.log(1.0 / n)))).toInt(),
        (start.y + dy * ((Math.log(1.0 * step / n) - Math.log(1.0 / n)) * n / (0 - Math.log(1.0 / n)))).toInt())
    }
    basicRobot.moveMouse(x, y)
  }

  //smooth mouse move to component
  override fun moveMouse(c: Component, x: Int, y: Int) {
    moveMouseWithAttempts(c, x, y)
  }

  //smooth mouse move for find and click actions
  override fun click(c: Component, where: Point, button: MouseButton, times: Int) {
    moveMouseAndClick(c, where, button, times)
  }

  //we are replacing BasicRobot click with our click because the original one cannot handle double click rightly (BasicRobot creates unnecessary move event between click event which breaks clickCount from 2 to 1)
  override fun click(where: Point, button: MouseButton, times: Int) {
    moveMouseAndClick(null, where, button, times)
  }

  private fun moveMouseAndClick(c: Component? = null, where: Point, button: MouseButton, times: Int) {
    if (c != null) moveMouse(c, where.x, where.y) else moveMouse(where.x, where.y)
    //pause between moving cursor and performing a click.
    Pause.pause(waitConst)
    myEdtAwareClick(button, times, where, c)
  }


  private fun moveMouseWithAttempts(c: Component, x: Int, y: Int, attempts: Int = 3) {
    if (attempts == 0) return
    waitFor { c.isShowing }

    val componentLocation: Point = requireNotNull(performOnEdt { AWT.translate(c, x, y) })
    moveMouse(componentLocation.x, componentLocation.y)

    val componentLocationAfterMove: Point = requireNotNull(performOnEdt { AWT.translate(c, x, y) })
    val mouseLocation = MouseInfo.getPointerInfo().location
    if (mouseLocation.x != componentLocationAfterMove.x || mouseLocation.y != componentLocationAfterMove.y)
      moveMouseWithAttempts(c, x, y, attempts - 1)
  }

  private fun myInnerClick(button: MouseButton, times: Int, point: Point, component: Component?) {
    if (component == null)
      basicRobot.click(point, button, times)
    else
      basicRobot.click(component, point, button, times)
  }

  private fun waitFor(condition: () -> Boolean) {
    val timeout = 5000 //5 sec
    val cdl = CountDownLatch(1)
    val executor = ConcurrencyUtil.newSingleScheduledThreadExecutor("SmartWaitRobot").scheduleWithFixedDelay(Runnable {
      if (condition()) {
        cdl.countDown()
      }
    }, 0, 100, TimeUnit.MILLISECONDS)
    cdl.await(timeout.toLong(), TimeUnit.MILLISECONDS)
    executor.cancel(true)
  }

  private fun myEdtAwareClick(button: MouseButton, times: Int, point: Point, component: Component?) {
    awareClick {
      myInnerClick(button, times, point, component)
    }
    waitForIdle()
  }

  private fun <T> performOnEdt(body: () -> T): T? =
    GuiActionRunner.execute(object : GuiQuery<T>() {
      override fun executeInEDT() = body.invoke()
    })

  private fun awareClick(body: () -> Unit) {
    myAwareClick = true
    body.invoke()
    myAwareClick = false
  }

}
