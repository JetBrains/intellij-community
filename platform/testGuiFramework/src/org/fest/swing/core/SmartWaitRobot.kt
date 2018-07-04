/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fest.swing.core

import com.intellij.util.ui.EdtInvocationManager
import org.fest.swing.awt.AWT
import org.fest.swing.edt.GuiActionRunner
import org.fest.swing.edt.GuiTask
import org.fest.swing.hierarchy.ExistingHierarchy
import org.fest.swing.keystroke.KeyStrokeMap
import org.fest.swing.timing.Pause.pause
import org.fest.swing.util.Modifiers
import org.fest.util.Preconditions
import java.awt.Component
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Window
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.swing.KeyStroke
import javax.swing.SwingUtilities

class SmartWaitRobot : BasicRobot(null, ExistingHierarchy()) {

  init {
    settings().delayBetweenEvents(10)
  }

  private val waitConst = 30L
  private var myAwareClick: Boolean = false
  private val fastRobot: java.awt.Robot = java.awt.Robot()

  override fun waitForIdle() {
    if (myAwareClick) {
      Thread.sleep(50)
    }
    else {
      pause(waitConst)
      if (!SwingUtilities.isEventDispatchThread()) EdtInvocationManager.getInstance().invokeAndWait({ })
    }
  }

  override fun close(w: Window) {
    super.close(w)
    super.waitForIdle()
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
        pause(pauseConstMs)
      }
      catch (e: InterruptedException) {
        e.printStackTrace()
      }

      super.moveMouse(
        (start.x + dx * ((Math.log(1.0 * step / n) - Math.log(1.0 / n)) * n / (0 - Math.log(1.0 / n)))).toInt(),
        (start.y + dy * ((Math.log(1.0 * step / n) - Math.log(1.0 / n)) * n / (0 - Math.log(1.0 / n)))).toInt())
    }
    super.moveMouse(x, y)
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
    pause(waitConst)
    myEdtAwareClick(button, times, where, c)
  }


  private fun moveMouseWithAttempts(c: Component, x: Int, y: Int, attempts: Int = 3) {
    if (attempts == 0) return
    waitFor { c.isShowing }
    val p = Preconditions.checkNotNull(AWT.translate(c, x, y))
    moveMouse(p.x, p.y)
    val p1 = Preconditions.checkNotNull(AWT.translate(c, x, y))
    val mouseLocation = MouseInfo.getPointerInfo().location
    if (mouseLocation.x != p1.x || mouseLocation.y != p1.y) moveMouseWithAttempts(c, x, y, attempts - 1)
  }

  private fun myInnerClick(button: MouseButton, times: Int, point: Point, component: Component?) {
    if (component == null)
      super.click(point, button, times)
    else
      super.click(component, point, button, times)
  }

  private fun waitFor(condition: () -> Boolean) {
    val timeout = 5000 //5 sec
    val cdl = CountDownLatch(1)
    val executor = Executors.newSingleThreadScheduledExecutor().scheduleWithFixedDelay(Runnable {
      if(condition()){
        cdl.countDown()
      }
    }, 0, 100, TimeUnit.MILLISECONDS)
    cdl.await(timeout.toLong(), TimeUnit.MILLISECONDS)
    executor.cancel(true)
  }

  fun fastPressAndReleaseKey(keyCode: Int, vararg modifiers: Int) {
    val unifiedModifiers = InputModifiers.unify(*modifiers)
    val updatedModifiers = Modifiers.updateModifierWithKeyCode(keyCode, unifiedModifiers)
    fastPressModifiers(updatedModifiers)
    if (updatedModifiers == unifiedModifiers) {
      fastPressKey(keyCode)
      fastReleaseKey(keyCode)
    }
    fastReleaseModifiers(updatedModifiers)
  }

  fun fastPressAndReleaseModifiers(vararg modifiers: Int) {
    val unifiedModifiers = InputModifiers.unify(*modifiers)
    fastPressModifiers(unifiedModifiers)
    pause(50)
    fastReleaseModifiers(unifiedModifiers)
  }

  fun fastPressAndReleaseKeyWithoutModifiers(keyCode: Int) {
    fastPressKey(keyCode)
    fastReleaseKey(keyCode)
  }

  fun shortcut(keyStoke: KeyStroke) {
    fastPressAndReleaseKey(keyStoke.keyCode, keyStoke.modifiers)
  }

  fun shortcutAndTypeString(keyStoke: KeyStroke, string: String, delayBetweenShortcutAndTypingMs: Int = 0) {
    fastPressAndReleaseKey(keyStoke.keyCode, keyStoke.modifiers)
    fastTyping(string, delayBetweenShortcutAndTypingMs)
  }

  fun fastTyping(string: String, delayBetweenShortcutAndTypingMs: Int = 0) {
    val keyCodeArray: IntArray = string
      .map { KeyStrokeMap.keyStrokeFor(it)?.keyCode ?: throw Exception("Unable to get keystroke for char '$it'") }
      .toIntArray()
    if (delayBetweenShortcutAndTypingMs > 0) pause(delayBetweenShortcutAndTypingMs.toLong())
    keyCodeArray.forEach { fastPressAndReleaseKeyWithoutModifiers(keyCode = it); pause(50) }
  }

  private fun fastPressKey(keyCode: Int) {
    fastRobot.keyPress(keyCode)
  }

  private fun fastReleaseKey(keyCode: Int) {
    fastRobot.keyRelease(keyCode)
  }

  private fun fastPressModifiers(modifierMask: Int) {
    val keys = Modifiers.keysFor(modifierMask)
    val keysSize = keys.size
    (0 until keysSize)
      .map { keys[it] }
      .forEach { fastPressKey(it) }
  }

  private fun fastReleaseModifiers(modifierMask: Int) {
    val modifierKeys = Modifiers.keysFor(modifierMask)
    for (i in modifierKeys.indices.reversed())
      fastReleaseKey(modifierKeys[i])
  }

  private fun myEdtAwareClick(button: MouseButton, times: Int, point: Point, component: Component?) {
    awareClick {
      performOnEdt {
        myInnerClick(button, times, point, component)
      }
    }
    waitForIdle()
  }

  private fun performOnEdt(body: () -> Unit) {
    if (!EdtInvocationManager.getInstance().isEventDispatchThread)
      GuiActionRunner.execute(object : GuiTask() {
        override fun executeInEDT() {
          body.invoke()
        }
      })
    else
      body.invoke()
  }

  private fun awareClick(body: () -> Unit) {
    myAwareClick = true
    body.invoke()
    myAwareClick = false
  }

}