package com.intellij.remoteServer.eventbus

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

/*
  Very simple eventbus. Support only runnable functions
 */
object EventBus {
  private val myEvents: ConcurrentMap<String, Runnable> = ConcurrentHashMap()

  fun on(event: String, runnable: Runnable) {
    myEvents[event] = runnable
  }

  fun emmit(event: String) = myEvents[event]?.run()

  fun emmitAndRemove(event: String) = myEvents.remove(event)?.run()
}