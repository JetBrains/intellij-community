// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.messages.impl

import com.intellij.codeWithMe.ClientId
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.messages.ListenerDescriptor
import com.intellij.util.messages.MessageBusOwner
import com.intellij.util.messages.Topic
import com.intellij.util.messages.impl.MessageBusImpl.RootBus
import org.assertj.core.api.Assertions
import org.junit.Assert
import org.junit.Test
import java.util.*

class CwmMessageBusTest : LightPlatformTestCase() {

  private val MESSAGE_1_HANDLER_1 = "m1:h1"
  private val MESSAGE_1_HANDLER_2 = "m1:h2"
  private val MESSAGE_2_HANDLER_1 = "m2:h1"

  @Test
  fun testMessageSetsCallerClientIdForHandler() {
    val bus = RootBus(MyMockMessageBusOwner())
    Disposer.register(testRootDisposable, bus)

    val eventsLog: MutableList<String> = ArrayList()

    val topic1 = Topic(Runnable::class.java, Topic.BroadcastDirection.TO_CHILDREN)
    val topic2 = Topic(Runnable::class.java, Topic.BroadcastDirection.TO_CHILDREN)

    val clientId_m1 = ClientId("client1")
    val clientId_m2 = ClientId("client2")

    val handler_m1_h1 = Runnable {
      eventsLog.add(MESSAGE_1_HANDLER_1)
      Assert.assertEquals(clientId_m1, ClientId.current)

      ClientId.withClientId(clientId_m2) {
        bus.syncPublisher(topic2).run()
      }
    }

    val handler_m1_h2 = Runnable {
      eventsLog.add(MESSAGE_1_HANDLER_2)
      Assert.assertEquals(clientId_m1, ClientId.current)
    }

    val handler_m2_h1 = Runnable {
      eventsLog.add(MESSAGE_2_HANDLER_1)
      Assert.assertEquals(clientId_m2, ClientId.current)
    }

    bus.connect(testRootDisposable).subscribe(topic1, handler_m1_h1)
    bus.connect(testRootDisposable).subscribe(topic1, handler_m1_h2)
    bus.connect(testRootDisposable).subscribe(topic2, handler_m2_h1)

    ClientId.withClientId(clientId_m1) {
      bus.syncPublisher(topic1).run()
    }

    Disposer.dispose(bus)

    assertEventsOrder(eventsLog, MESSAGE_1_HANDLER_1, MESSAGE_1_HANDLER_2, MESSAGE_2_HANDLER_1)
  }

  private fun assertEventsOrder(eventsLog: MutableList<String>, vararg expected: String) {
    Assertions.assertThat(java.lang.String.join("\n", eventsLog)).isEqualTo(java.lang.String.join("\n", *expected))
  }

  private class MyMockMessageBusOwner : MessageBusOwner {
    override fun createListener(descriptor: ListenerDescriptor): Any = throw NotImplementedError()
    override fun isDisposed(): Boolean = false
  }
}