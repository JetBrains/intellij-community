// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.common

import com.intellij.ReviseWhenPortedToJDK
import com.intellij.util.ReflectionUtil
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.DelayQueue
import java.util.concurrent.TimeUnit
import javax.swing.Timer

@ReviseWhenPortedToJDK("9")
@TestOnly
@Internal
fun checkJavaSwingTimersAreDisposed() {
  val timerQueueClass = Class.forName("javax.swing.TimerQueue")
  val sharedInstance = timerQueueClass.getMethod("sharedInstance")
  sharedInstance.isAccessible = true
  val timerQueue = sharedInstance.invoke(null)
  val delayQueue = ReflectionUtil.getField(timerQueueClass, timerQueue, DelayQueue::class.java, "queue")
  val timer = delayQueue.peek()
  if (timer == null) {
    return
  }

  val delay = timer.getDelay(TimeUnit.MILLISECONDS)
  var text = "(delayed for ${delay}ms)"
  val getTimer = ReflectionUtil.getDeclaredMethod(timer.javaClass, "getTimer")!!
  val swingTimer = getTimer.invoke(timer) as Timer
  text = "Timer (listeners: ${listOf(*swingTimer.actionListeners)}) $text"
  try {
    throw AssertionError("Not disposed javax.swing.Timer: $text; queue:$timerQueue")
  }
  finally {
    swingTimer.stop()
  }
}
