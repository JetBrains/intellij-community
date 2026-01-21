// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("SSBasedInspection")

package com.intellij.serviceContainer

import com.intellij.concurrency.currentThreadContext
import com.intellij.diagnostic.PluginException
import com.intellij.ide.plugins.TestIdeaPluginDescriptor
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.components.ServiceDescriptor
import com.intellij.openapi.components.ServiceDescriptor.PreloadMode
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import com.intellij.testFramework.LoggedErrorProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
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
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame

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
  fun `initializer overwrites caller context - load 1`() {
    val componentManager = TestComponentManager(parentScope = CoroutineScope(ContextElement(MARKER_1)))
    val componentManager2 = TestComponentManager(parentScope = CoroutineScope(ContextElement(MARKER_2)))
    componentManager.registerService(T1::class.java, T1::class.java, testPluginDescriptor, false)
    componentManager2.registerService(T2::class.java, T2::class.java, testPluginDescriptor, false)
    publishComponentManager2(componentManager2) {
      componentManager.getService(T1::class.java)
    }
  }

  @Test
  fun `initializer overwrites caller context - load 2`() {
    val componentManager = TestComponentManager(parentScope = CoroutineScope(ContextElement(MARKER_1)))
    val componentManager2 = TestComponentManager(parentScope = CoroutineScope(ContextElement(MARKER_3)),
                                                 additionalContext = ContextElement(MARKER_2))
    componentManager.registerService(T1::class.java, T1::class.java, testPluginDescriptor, false)
    componentManager2.registerService(T2::class.java, T2::class.java, testPluginDescriptor, false)
    publishComponentManager1(componentManager) {
      publishComponentManager2(componentManager2) {
        componentManager.getService(T1::class.java)
      }
    }
  }

  @Test
  fun `initializer overwrites caller context - load 3`() {
    val componentManager = TestComponentManager(additionalContext = ContextElement(MARKER_1))
    val componentManager2 = TestComponentManager(parentScope = CoroutineScope(EmptyCoroutineContext),
                                                 additionalContext = ContextElement(MARKER_2))
    componentManager.registerService(T1::class.java, T1::class.java, testPluginDescriptor, false)
    componentManager2.registerService(T2::class.java, T2::class.java, testPluginDescriptor, false)
    publishComponentManager1(componentManager) {
      publishComponentManager2(componentManager2) {
        runBlocking {
          withContext(ContextElement(MARKER_3)) {
            componentManager.getService(T1::class.java)
          }
        }
      }
    }
  }

  @Test
  fun `initializer overwrites caller context - load 4`() {
    val componentManager = TestComponentManager(additionalContext = ContextElement(MARKER_1))
    val componentManager2 = TestComponentManager(parentScope = CoroutineScope(ContextElement(MARKER_3)),
                                                 additionalContext = ContextElement(MARKER_2))
    componentManager.registerService(T1::class.java, T1::class.java, testPluginDescriptor, false)
    componentManager2.registerService(T2::class.java, T2::class.java, testPluginDescriptor, false)
    publishComponentManager1(componentManager) {
      publishComponentManager2(componentManager2) {
        runBlocking {
          withContext(ContextElement(MARKER_3)) {
            componentManager.getService(T1::class.java)
          }
        }
      }
    }
  }

  @Test
  fun `initializer overwrites caller context - load 5`() {
    val componentManager = TestComponentManager(additionalContext = ContextElement(MARKER_1))
    val componentManager2 = TestComponentManager(parentScope = CoroutineScope(ContextElement(MARKER_3)),
                                                 additionalContext = ContextElement(MARKER_2))
    componentManager2.registerService(T0::class.java, T0::class.java, testPluginDescriptor, false)
    componentManager.registerService(T1::class.java, T1::class.java, testPluginDescriptor, false)
    componentManager2.registerService(T2::class.java, T2::class.java, testPluginDescriptor, false)
    publishComponentManager1(componentManager) {
      publishComponentManager2(componentManager2) {
        runBlocking {
          withContext(ContextElement(MARKER_3)) {
            componentManager.getService(T0::class.java)
          }
        }
      }
    }
  }

  @Test
  fun `initializer overwrites caller context - preload 1`() {
    val componentManager = TestComponentManager(parentScope = CoroutineScope(ContextElement(MARKER_1)))
    val componentManager2 = TestComponentManager(parentScope = CoroutineScope(ContextElement(MARKER_2)))
    componentManager.registerService(T1::class.java, T1::class.java, testPluginDescriptor, false)
    componentManager2.registerService(T2::class.java, T2::class.java, testPluginDescriptor, false)
    publishComponentManager2(componentManager2) {
      runBlocking {
        componentManager.preload(T1::class.java)
      }
    }
  }

  @Test
  fun `initializer overwrites caller context - preload 2`() {
    val componentManager = TestComponentManager(parentScope = CoroutineScope(ContextElement(MARKER_1)))
    val componentManager2 = TestComponentManager(parentScope = CoroutineScope(ContextElement(MARKER_3)),
                                                 additionalContext = ContextElement(MARKER_2))
    componentManager.registerService(T1::class.java, T1::class.java, testPluginDescriptor, false)
    componentManager2.registerService(T2::class.java, T2::class.java, testPluginDescriptor, false)
    publishComponentManager1(componentManager) {
      publishComponentManager2(componentManager2) {
        runBlocking {
          componentManager.preload(T1::class.java)
        }
      }
    }
  }

  @Test
  fun `initializer overwrites caller context - preload 3`() {
    val componentManager = TestComponentManager(additionalContext = ContextElement(MARKER_1))
    val componentManager2 = TestComponentManager(parentScope = CoroutineScope(EmptyCoroutineContext),
                                                 additionalContext = ContextElement(MARKER_2))
    componentManager.registerService(T1::class.java, T1::class.java, testPluginDescriptor, false)
    componentManager2.registerService(T2::class.java, T2::class.java, testPluginDescriptor, false)
    publishComponentManager1(componentManager) {
      publishComponentManager2(componentManager2) {
        runBlocking {
          withContext(ContextElement(MARKER_3)) {
            componentManager.preload(T1::class.java)
          }
        }
      }
    }
  }

  @Test
  fun `initializer overwrites caller context - preload 4`() {
    val componentManager = TestComponentManager(additionalContext = ContextElement(MARKER_1))
    val componentManager2 = TestComponentManager(parentScope = CoroutineScope(ContextElement(MARKER_3)),
                                                 additionalContext = ContextElement(MARKER_2))
    componentManager.registerService(T1::class.java, T1::class.java, testPluginDescriptor, false)
    componentManager2.registerService(T2::class.java, T2::class.java, testPluginDescriptor, false)
    publishComponentManager1(componentManager) {
      publishComponentManager2(componentManager2) {
        runBlocking {
          withContext(ContextElement(MARKER_3)) {
            componentManager.preload(T1::class.java)
          }
        }
      }
    }
  }

  @Test
  fun `initializer overwrites caller context - preload 5`() {
    val componentManager = TestComponentManager(additionalContext = ContextElement(MARKER_1))
    val componentManager2 = TestComponentManager(parentScope = CoroutineScope(ContextElement(MARKER_3)),
                                                 additionalContext = ContextElement(MARKER_2))
    componentManager2.registerService(T0::class.java, T0::class.java, testPluginDescriptor, false)
    componentManager.registerService(T1::class.java, T1::class.java, testPluginDescriptor, false)
    componentManager2.registerService(T2::class.java, T2::class.java, testPluginDescriptor, false)
    publishComponentManager1(componentManager) {
      publishComponentManager2(componentManager2) {
        runBlocking {
          withContext(ContextElement(MARKER_3)) {
            componentManager2.preload(T0::class.java)
          }
        }
      }
    }
  }

  @Test
  fun `initializer is isolated from the caller`() {
    val componentManager = TestComponentManager(parentScope = CoroutineScope(ContextElement(MARKER_1)))
    val componentManager2 = TestComponentManager(parentScope = CoroutineScope(EmptyCoroutineContext))
    componentManager.registerService(R1::class.java, R1::class.java, testPluginDescriptor, false)
    componentManager2.registerService(R2::class.java, R2::class.java, testPluginDescriptor, false)
    publishComponentManager2(componentManager2) {
      componentManager.getService(R1::class.java)
    }
  }

  @Test
  fun `additional context has priority - load`() {
    val componentManager =
      TestComponentManager(parentScope = CoroutineScope(ContextElement(MARKER_0)), additionalContext = ContextElement(MARKER_1))
    val componentManager2 =
      TestComponentManager(parentScope = CoroutineScope(ContextElement(MARKER_0)), additionalContext = ContextElement(MARKER_2))
    componentManager.registerService(T1::class.java, T1::class.java, testPluginDescriptor, false)
    componentManager2.registerService(T2::class.java, T2::class.java, testPluginDescriptor, false)
    publishComponentManager2(componentManager2) {
      componentManager.getService(T1::class.java)
    }
  }

  @Test
  fun `additional context has priority - preload`() {
    val componentManager =
      TestComponentManager(parentScope = CoroutineScope(ContextElement(MARKER_0)), additionalContext = ContextElement(MARKER_1))
    val componentManager2 =
      TestComponentManager(parentScope = CoroutineScope(ContextElement(MARKER_0)), additionalContext = ContextElement(MARKER_2))
    componentManager.registerService(T1::class.java, T1::class.java, testPluginDescriptor, false)
    componentManager2.registerService(T2::class.java, T2::class.java, testPluginDescriptor, false)
    publishComponentManager2(componentManager2) {
      runBlocking {
        componentManager.preload(T1::class.java)
      }
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

  @Test
  fun `error message includes class name for unsupported constructor`() {
    val componentManager = TestComponentManager()
    componentManager.registerService(
      ServiceWithUnsupportedConstructor::class.java,
      ServiceWithUnsupportedConstructor::class.java,
      testPluginDescriptor,
      false
    )
    val error = LoggedErrorProcessor.executeAndReturnLoggedError {
      try {
        componentManager.getService(ServiceWithUnsupportedConstructor::class.java)
        throw AssertionError("Expected exception")
      }
      catch (_: PluginException) {
        // expected
      }
    }
    assertNotNull(error)
    assertThat(error.message).contains("ServiceWithUnsupportedConstructor")
  }

  @Test
  fun `test dynamically overridden service forwards calls`() {
    val componentManager = TestComponentManager()
    componentManager.useProxiesForOpenServices = true

    val pluginDescriptor1 = IdeaPluginDescriptorForServiceRegistration("testPlugin1")
    val pluginDescriptor2 = IdeaPluginDescriptorForServiceRegistration("testPlugin2")
    componentManager.registerService(serviceDescriptor(IfcService::class.java, IfcServiceImpl1::class.java, open = true), pluginDescriptor1)
    val ref1 = componentManager.getService(IfcService::class.java)!!
    assertEquals("IfcServiceImpl1", ref1.greet())
    assertNotEquals(IfcServiceImpl1::class.java.name,
                    ref1.javaClass.name,
                    "Should be a proxy class, not the service itself (because the service is open).")

    componentManager.registerService(serviceDescriptor(IfcService::class.java, IfcServiceImpl2::class.java, overrides = true),
                                     pluginDescriptor2)
    val ref2 = componentManager.getService(IfcService::class.java)!!
    assertNotSame(ref1, ref2)
    assertEquals(IfcServiceImpl2::class.java.name,
                 ref2.javaClass.name,
                 "Should be the service itself, not a proxy class (because the service is not open).")
    assertEquals("IfcServiceImpl2", ref2.greet())
    assertEquals("IfcServiceImpl2", ref1.greet(), "Old reference should redirect calls to new instance")
  }

  private class IdeaPluginDescriptorForServiceRegistration(
    val pluginId: String,
    private val bundled: Boolean = false,
  ) : TestIdeaPluginDescriptor() {
    override fun getPluginId(): PluginId = PluginId(pluginId)
    override fun getName(): @NlsSafe String = pluginId
    override fun getDescriptorPath(): String? = null
    override fun getPluginClassLoader(): ClassLoader? = null
    override fun isBundled(): Boolean = bundled
  }

  private fun serviceDescriptor(
    serviceIfc: Class<*>,
    serviceImpl: Class<*>,
    overrides: Boolean = false,
    open: Boolean = false,
  ): ServiceDescriptor {
    return ServiceDescriptor(serviceIfc.name, serviceImpl.name,
                             null, null, overrides, open, null,
                             PreloadMode.FALSE, null, null)
  }

  private fun publishComponentManager1(componentManager: ComponentManager, action: () -> Unit): Unit {
    componentManagerHolder1.set(componentManager)
    try {
      action()
    }
    finally {
      componentManagerHolder1.set(null)
    }
  }

  private fun publishComponentManager2(componentManager: ComponentManager, action: () -> Unit): Unit {
    componentManagerHolder2.set(componentManager)
    try {
      action()
    }
    finally {
      componentManagerHolder2.set(null)
    }
  }

}

private class SimpleService

internal interface IfcService {
  fun greet(): String
}

private class IfcServiceImpl1 : IfcService {
  override fun greet(): String = "IfcServiceImpl1"
}

private class IfcServiceImpl2 : IfcService {
  override fun greet(): String = "IfcServiceImpl2"
}

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

private val componentManagerHolder1: AtomicReference<ComponentManager> = AtomicReference(null)
private val componentManagerHolder2: AtomicReference<ComponentManager> = AtomicReference(null)


private class T0 {
  init {
    assertThat(currentThreadContext()[ContextElement.Key]).isNotNull().extracting("marker").isEqualTo(MARKER_2)
    componentManagerHolder1.get()!!.getService(T1::class.java)!!
  }
}

private class T1 {
  init {
    assertThat(currentThreadContext()[ContextElement.Key]).isNotNull().extracting("marker").isEqualTo(MARKER_1)
    // imitation of a globally-available `application`
    componentManagerHolder2.get()!!.getService(T2::class.java)!!
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
    componentManagerHolder2.get()!!.getService(R2::class.java)!!
  }
}

private class R2 {
  init {
    assertThat(currentThreadContext()[ContextElement.Key]).isNull()
  }
}

private class ContextElement(val marker: String) : AbstractCoroutineContextElement(Key) {
  override fun toString(): String {
    return "ServiceContainerTest: ContextElement(marker='$marker')"
  }

  override val key: CoroutineContext.Key<*> get() = Key

  object Key : CoroutineContext.Key<ContextElement> {
    override fun toString(): String {
      return "ServiceContainerTest: " + super.toString()
    }
  }

}

private class ServiceWithUnsupportedConstructor(@Suppress("UNUSED_PARAMETER") unusedParam: String)

private const val MARKER_0 = "marker 0"
private const val MARKER_1 = "marker 1"
private const val MARKER_2 = "marker 2"
private const val MARKER_3 = "marker 3"
