// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.serviceContainer

import com.intellij.concurrency.currentThreadContext
import com.intellij.diagnostic.PluginException
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LoggedErrorProcessor
import kotlinx.coroutines.CoroutineScope
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertInstanceOf
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.assertContains

private val testPluginDescriptor: DefaultPluginDescriptor = DefaultPluginDescriptor("test")

class ServiceContainerTest {
  @Test
  fun `cyclic detection`() {
    val disposable = Disposer.newDisposable()
    com.intellij.openapi.diagnostic.DefaultLogger.disableStderrDumping(disposable)
    try {
      val componentManager = TestComponentManager()
      val pluginDescriptor = DefaultPluginDescriptor("test")
      componentManager.registerServiceInstance(ComponentManager::class.java, componentManager, pluginDescriptor)

      componentManager.registerService(C1::class.java, C1::class.java, pluginDescriptor, override = false)
      componentManager.registerService(C2::class.java, C2::class.java, pluginDescriptor, override = false)
      try {
        componentManager.getService(C1::class.java)
      }
      catch (e: Throwable) {
        val stringBuilder = StringBuilder()
        var cause = e
        while (true) {
          stringBuilder.append(cause.javaClass.simpleName).append(" " + cause.message).appendLine()
          val newCause = cause.cause
          if (newCause !== cause) {
            cause = newCause ?: break
          }
        }
        assertThat(stringBuilder).isEqualToIgnoringWhitespace("""
            PluginException [com.intellij.serviceContainer.C1, com.intellij.serviceContainer.C2] [Plugin: test]
            CycleInitializationException [com.intellij.serviceContainer.C1, com.intellij.serviceContainer.C2]
          """.trimIndent())
      }
    }
    finally {
      Disposer.dispose(disposable)
    }
  }

  @Test
  fun `service as data class`() {
    val componentManager = TestComponentManager()
    componentManager.registerService(ServiceAsDataClass::class.java, ServiceAsDataClass::class.java, testPluginDescriptor, false)
    componentManager.getService(ServiceAsDataClass::class.java)
  }

  @Test
  fun `initializer overwrites caller context`() {
    @Suppress("SSBasedInspection") val componentManager = TestComponentManager(parentScope = CoroutineScope(ContextElement(MARKER_1)))
    @Suppress("SSBasedInspection") val componentManager2 = TestComponentManager(parentScope = CoroutineScope(ContextElement(MARKER_2)))
    componentManager.registerService(T1::class.java, T1::class.java, testPluginDescriptor, false)
    componentManager2.registerService(T2::class.java, T2::class.java, testPluginDescriptor, false)
    publishComponentManager(componentManager2) {
      componentManager.getService(T1::class.java)
    }
  }

  @Test
  fun `initializer is isolated from the caller`() {
    @Suppress("SSBasedInspection") val componentManager = TestComponentManager(parentScope = CoroutineScope(ContextElement(MARKER_1)))
    @Suppress("SSBasedInspection") val componentManager2 = TestComponentManager(parentScope = CoroutineScope(EmptyCoroutineContext))
    componentManager.registerService(R1::class.java, R1::class.java, testPluginDescriptor, false)
    componentManager2.registerService(R2::class.java, R2::class.java, testPluginDescriptor, false)
    publishComponentManager(componentManager2) {
      componentManager.getService(R1::class.java)
    }
  }

  @Test
  fun `service created by getService`() {
    val service = TestComponentManager().apply {
      registerService(SimpleService::class.java, SimpleService::class.java, testPluginDescriptor, false)
    }.run {
      getService(SimpleService::class.java)
    }
    assertNotNull(service)
  }

  @Test
  fun `service not created by getServiceIfCreated`() {
    val service = TestComponentManager().apply {
      registerService(SimpleService::class.java, SimpleService::class.java, testPluginDescriptor, false)
    }.run {
      getServiceIfCreated(SimpleService::class.java)
    }
    assertNull(service)
  }

  @Test
  fun `service not returned by component fallback`() {
    val service = TestComponentManager().apply {
      registerService(SimpleService::class.java, SimpleService::class.java, testPluginDescriptor, false)
    }.run {
      getComponentAsServiceFallback(SimpleService::class.java)
    }
    assertNull(service)
  }

  @Test
  fun `service component fallback`() {
    val error = LoggedErrorProcessor.executeAndReturnLoggedError {
      val component = TestComponentManager().apply {
        registerComponentInstance(SimpleService::class.java, SimpleService())
      }.run {
        getService(SimpleService::class.java)
      }
      assertNotNull(component)
    }
    assertNotNull(error)
    assertInstanceOf<PluginException>(error)
    assertContains(error.message, "requested as a service, but it is a component")
  }

  @Test
  fun `service created by getComponent`() {
    val service = TestComponentManager().apply {
      registerService(SimpleService::class.java, SimpleService::class.java, testPluginDescriptor, false)
    }.run {
      getComponent(SimpleService::class.java)
    }
    assertNotNull(service)
  }

  private fun publishComponentManager(componentManager: ComponentManager, action: () -> Unit): Unit {
    componentManagerHolder.set(componentManager)
    try {
      action()
    }
    finally {
      componentManagerHolder.set(null)
    }
  }
}

private class SimpleService

private class C1(componentManager: ComponentManager) {
  init {
    componentManager.getService(C2::class.java)
  }
}

private class C2(componentManager: ComponentManager) {
  init {
    componentManager.getService(C1::class.java)
  }
}

private data class ServiceAsDataClass(var a: String? = null, var b: String? = null)

private val componentManagerHolder: AtomicReference<ComponentManager> = AtomicReference(null)


private class T1 {
  init {
    assertThat(currentThreadContext()[ContextElement.Key]).isNotNull().extracting("marker").isEqualTo(MARKER_1)
    // imitation of a globally-available `application`
    componentManagerHolder.get()!!.getService(T2::class.java)
  }
}

private class T2 {
  init {
    assertThat(currentThreadContext()[ContextElement.Key]).isNotNull().extracting("marker").isEqualTo(MARKER_2)
  }
}

private class R1 {
  init {
    assertThat(currentThreadContext()[ContextElement.Key]).isNotNull().extracting("marker").isEqualTo(MARKER_1)
    // imitation of a globally-available `application`
    componentManagerHolder.get()!!.getService(R2::class.java)
  }
}

private class R2 {
  init {
    assertThat(currentThreadContext()[ContextElement.Key]).isNull()
  }
}

private class ContextElement(val marker: String) : AbstractCoroutineContextElement(Key) {
  object Key : CoroutineContext.Key<ContextElement>
}

private const val MARKER_1 = "marker 1"
private const val MARKER_2 = "marker 2"