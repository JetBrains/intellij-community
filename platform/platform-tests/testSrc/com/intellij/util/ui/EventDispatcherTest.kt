// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.LoggedErrorProcessorEnabler
import com.intellij.util.EventDispatcher
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.*

@ExtendWith(DoNoRethrowEventDispatcherErrors::class)
class EventDispatcherTest {
  private val log: MutableList<String> = ArrayList()

  @Test
  fun cancellationErrorPropagationFromListener() {
    val eventDispatcher = EventDispatcher.create(Listener1::class.java)
    eventDispatcher.addListener(L1Handler("handler3"))

    eventDispatcher.addListener(object : Listener1 {
      override fun eventFired1() {
        log.add("pce")
        throw ProcessCanceledException()
      }
    })

    eventDispatcher.addListener(L1Handler("handler2"))

    eventDispatcher.multicaster.eventFired1()

    // event is delivered to all subscribers, then the error is rethrown
    assertEvents("handler3:t1", "pce", "handler2:t1")
  }

  @Test
  fun runtimeErrorPropagationFromListener1() {
    val eventDispatcher = EventDispatcher.create(Listener1::class.java)
    eventDispatcher.addListener(L1Handler("handler3"))

    eventDispatcher.addListener(object : Listener1 {
      override fun eventFired1() {
        log.add("uoe")
        throw UnsupportedOperationException()
      }
    })

    eventDispatcher.addListener(L1Handler("handler2"))

    eventDispatcher.multicaster.eventFired1()

    // event is delivered to all subscribers, then the error is rethrown
    assertEvents("handler3:t1", "uoe", "handler2:t1")
  }

  @Test
  fun runtimeErrorPropagationFromListener2() {
    val eventDispatcher = EventDispatcher.create(Listener1::class.java)
    eventDispatcher.addListener(object : Listener1 {
      override fun eventFired1() {
        log.add("ise")
        throw UnsupportedOperationException()
      }
    })

    eventDispatcher.addListener(object : Listener1 {
      override fun eventFired1() {
        log.add("uoe")
        throw ProcessCanceledException()
      }
    })

    eventDispatcher.addListener(L1Handler("handler2"))

    eventDispatcher.multicaster.eventFired1()

    // event is delivered to all subscribers, then the error is rethrown
    assertEvents("ise", "uoe", "handler2:t1")
  }


  private fun assertEvents(vararg expected: String) {
    assertThat(java.lang.String.join("\n", log)).isEqualTo(java.lang.String.join("\n", *expected))
  }

  interface Listener1 : EventListener {
    fun eventFired1()
  }

  private inner class L1Handler(private val id: String) : Listener1 {
    override fun eventFired1() {
      log.add("$id:t1")
    }
  }
}

private class DoNoRethrowEventDispatcherErrors : LoggedErrorProcessorEnabler {
  override fun createErrorProcessor(): LoggedErrorProcessor {
    return object : LoggedErrorProcessor() {
      override fun processError(category: String, message: String, details: Array<String>, t: Throwable?): Set<Action> {
        if ("#com.intellij.util.EventDispatcher" == category) {
          return setOf(Action.LOG)
        }
        return super.processError(category, message, details, t)
      }
    }
  }
}
