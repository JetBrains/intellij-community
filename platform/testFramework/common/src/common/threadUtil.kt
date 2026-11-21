// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.common

import com.intellij.ReviseWhenPortedToJDK
import com.intellij.util.ReflectionUtil
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.DelayQueue
import java.util.concurrent.TimeUnit
import javax.swing.Timer

/**
 * Fails on the first started [Timer] of the Swing timer queue, and stops that timer. The check skips the message pump of
 * JCEF, see [isJcefMessagePump], and never stops it.
 */
@ReviseWhenPortedToJDK("9")
@TestOnly
@Internal
fun checkJavaSwingTimersAreDisposed() {
  val timerQueueClass = Class.forName("javax.swing.TimerQueue")
  val sharedInstance = timerQueueClass.getMethod("sharedInstance")
  sharedInstance.isAccessible = true
  val timerQueue = sharedInstance.invoke(null)
  val delayQueue = ReflectionUtil.getField(timerQueueClass, timerQueue, DelayQueue::class.java, "queue")
  for (timer in delayQueue) {
    val getTimer = ReflectionUtil.getDeclaredMethod(timer.javaClass, "getTimer")!!
    val swingTimer = getTimer.invoke(timer) as Timer
    if (isJcefMessagePump(swingTimer)) {
      continue
    }

    val text = "Timer (listeners: ${listOf(*swingTimer.actionListeners)}) (delayed for ${timer.getDelay(TimeUnit.MILLISECONDS)}ms)"
    try {
      throw AssertionError("Not disposed javax.swing.Timer: $text; queue:$timerQueue")
    }
    finally {
      swingTimer.stop()
    }
  }
}

/**
 * True for the timer of the message pump of `org.cef.CefApp`: JCEF in process schedules its work with it, and the timer
 * lives as long as the JCEF application. The check knows the timer by the class of its listener.
 */
private fun isJcefMessagePump(timer: Timer): Boolean = timer.actionListeners.any { it.javaClass.name.startsWith(JCEF_APP_CLASS) }

private const val JCEF_APP_CLASS = "org.cef.CefApp"
