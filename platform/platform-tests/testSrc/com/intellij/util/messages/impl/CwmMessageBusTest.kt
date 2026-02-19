// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.messages.impl

import com.intellij.codeWithMe.ClientId
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.client.ClientAppSession
import com.intellij.openapi.client.ClientAppSessionImpl
import com.intellij.openapi.client.ClientSessionsManager
import com.intellij.openapi.client.ClientType
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.application
import com.intellij.util.messages.MessageBusFactory
import com.intellij.util.messages.MessageBusOwner
import com.intellij.util.messages.Topic
import org.assertj.core.api.Assertions
import org.junit.Assert
import org.junit.Test

class CwmMessageBusTest : LightPlatformTestCase() {

  private val MESSAGE_1_HANDLER_1 = "m1:h1"
  private val MESSAGE_1_HANDLER_2 = "m1:h2"
  private val MESSAGE_2_HANDLER_1 = "m2:h1"

  @Test
  fun testMessageSetsCallerClientIdForHandler() {
    val bus = MessageBusFactory.getInstance().createMessageBus(MyMockMessageBusOwner(), null)
    Disposer.register(testRootDisposable, bus)
    val eventsLog: MutableList<String> = ArrayList()

    val session1 = createSession("client1")
    val session2 = createSession("client2")

    val manager = service<ClientSessionsManager<ClientAppSession>>()
    manager.registerSession(testRootDisposable, session1)
    manager.registerSession(testRootDisposable, session2)
    val topic1 = Topic(Runnable::class.java, Topic.BroadcastDirection.TO_CHILDREN)
    val topic2 = Topic(Runnable::class.java, Topic.BroadcastDirection.TO_CHILDREN)


    val handler_m1_h1 = Runnable {
      eventsLog.add(MESSAGE_1_HANDLER_1)
      Assert.assertEquals(session1.clientId, ClientId.current)

      ClientId.withExplicitClientId(session2.clientId) {
        bus.syncPublisher(topic2).run()
      }
    }

    val handler_m1_h2 = Runnable {
      eventsLog.add(MESSAGE_1_HANDLER_2)
      Assert.assertEquals(session1.clientId, ClientId.current)
    }

    val handler_m2_h1 = Runnable {
      eventsLog.add(MESSAGE_2_HANDLER_1)
      Assert.assertEquals(session2.clientId, ClientId.current)
    }

    bus.connect(testRootDisposable).subscribe(topic1, handler_m1_h1)
    bus.connect(testRootDisposable).subscribe(topic1, handler_m1_h2)
    bus.connect(testRootDisposable).subscribe(topic2, handler_m2_h1)

    ClientId.withClientId(session1.clientId) {
      bus.syncPublisher(topic1).run()
    }

    Disposer.dispose(bus)

    assertEventsOrder(eventsLog, MESSAGE_1_HANDLER_1, MESSAGE_1_HANDLER_2, MESSAGE_2_HANDLER_1)
  }

  private fun createSession(name: String) = object : ClientAppSessionImpl(
    ClientId(name),
    ClientType.GUEST,
    application as ApplicationImpl
  ) {
    override val name: String = name
  }

  private fun assertEventsOrder(eventsLog: MutableList<String>, vararg expected: String) {
    Assertions.assertThat(java.lang.String.join("\n", eventsLog)).isEqualTo(java.lang.String.join("\n", *expected))
  }

  private class MyMockMessageBusOwner : MessageBusOwner {
    override fun createListener(descriptor: PluginListenerDescriptor): Any = throw NotImplementedError()
    override fun isDisposed(): Boolean = false
  }
}