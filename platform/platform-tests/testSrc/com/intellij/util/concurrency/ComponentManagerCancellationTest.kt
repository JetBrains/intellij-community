// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency

import com.intellij.codeWithMe.ClientId
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.client.ClientKind
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.serviceContainer.ComponentManagerImpl
import com.intellij.serviceContainer.TestComponentManager
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.application
import com.intellij.util.concurrency.ClientIdPropagationTest.TestClientAppSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class ComponentManagerCancellationTest : LightPlatformTestCase() {
  override fun runInDispatchThread() = false

  fun `test service throws CE in cancelled context - local service`() {
    runTestLocalService { manager ->
      testServiceThrowsCE(manager)
    }
  }

  fun `test service throws CE in cancelled context - client service via client session`() {
    runTestClientServiceViaClientService { manager ->
      testServiceThrowsCE(manager)
    }
  }

  fun `test service throws CE in cancelled context - local service via client session`() {
    runTestLocalServiceViaClientService { manager ->
      testServiceThrowsCE(manager)
    }
  }

  fun `test serviceIfCreated for missing service in cancelled context - local service`() {
    runTestLocalService { manager ->
      testServiceIfCreatedForMissing(manager)
    }
  }

  fun `test serviceIfCreated for missing service in cancelled context - client service via client session`() {
    runTestClientServiceViaClientService { manager ->
      testServiceIfCreatedForMissing(manager)
    }
  }

  fun `test serviceIfCreated for missing service in cancelled context - local service via client session`() {
    runTestLocalServiceViaClientService { manager ->
      testServiceIfCreatedForMissing(manager)
    }
  }

  fun `test serviceIfCreated for created service in cancelled context - local service`() {
    runTestLocalService { manager ->
      testServiceIfCreatedForCreated(manager)
    }
  }

  fun `test serviceIfCreated for created service in cancelled context - client service via client session`() {
    runTestClientServiceViaClientService { manager ->
      testServiceIfCreatedForCreated(manager)
    }
  }

  fun `test serviceIfCreated for created service in cancelled context - local service via client session`() {
    runTestLocalServiceViaClientService { manager ->
      testServiceIfCreatedForCreated(manager)
    }
  }

  fun `test service for unregistered service in cancelled context - local service`() {
    val manager = TestComponentManager()
    testServiceForUnregistered(manager)
  }

  fun `test service for unregistered service in cancelled context - client service`() {
    val application = application as ApplicationImpl
    val manager = TestClientAppSession(application, TEST_CLIENT_ID)

    testServiceForUnregistered(manager)
  }

  private fun testServiceThrowsCE(manager: ComponentManagerImpl) {
    runCancellationCheck(true) {
      assertNotNull("failed to init the service", manager.getService(SimpleService::class.java))
    }
    assertNull(manager.getServiceIfCreated(SimpleService::class.java))
  }

  private fun testServiceIfCreatedForMissing(manager: ComponentManagerImpl) {
    runCancellationCheck(false) {
      assertNull("we should not create service in getServiceIfCreated", manager.getServiceIfCreated(SimpleService::class.java))
    }
  }

  private fun testServiceIfCreatedForCreated(manager: ComponentManagerImpl) {
    assertNotNull("failed to init the service", manager.getService(SimpleService::class.java))

    runCancellationCheck(false) {
      assertNotNull(manager.getServiceIfCreated(SimpleService::class.java))
    }
  }

  private fun testServiceForUnregistered(manager: ComponentManagerImpl) {
    runCancellationCheck(false) {
      assertNull(manager.getService(SimpleService::class.java))
    }
  }

  private fun runTestLocalService(test: (ComponentManagerImpl) -> Unit) {
    val manager = TestComponentManager()
    manager.registerService(SimpleService::class.java, SimpleService::class.java, testPluginDescriptor, false)

    test(manager)
  }

  private fun runTestClientServiceViaClientService(test: (ComponentManagerImpl) -> Unit) {
    val application = application as ApplicationImpl
    val manager = TestClientAppSession(application, TEST_CLIENT_ID)
    manager.registerService(SimpleService::class.java, SimpleService::class.java, testPluginDescriptor, false, clientKind = ClientKind.REMOTE)

    test(manager)
  }

  private fun runTestLocalServiceViaClientService(test: (ComponentManagerImpl) -> Unit) {
    val application = application as ApplicationImpl
    val manager = TestClientAppSession(application, TEST_CLIENT_ID)
    application.registerService(SimpleService::class.java, SimpleService::class.java, testPluginDescriptor, false, clientKind = ClientKind.LOCAL)

    try {
      test(manager)
    }
    finally {
      application.unregisterService(SimpleService::class.java)
    }
  }

  private fun runCancellationCheck(shallThrowCE: Boolean, task: () -> Unit) {
    var serviceQueried = false
    var serviceQuerySucceeded = false
    runBlocking {
      val lock = CompletableDeferred<Unit>()
      val job = launch {
        try {
          lock.complete(Unit)
          awaitCancellation()
        }
        finally {
          serviceQueried = true
          try {
            task()
          }
          catch (e: CancellationException) {
            e.printStackTrace()
            throw e
          }
          serviceQuerySucceeded = true
        }
      }
      lock.await()
      job.cancelAndJoin()
    }
    assertTrue("service was not queried", serviceQueried)

    if (shallThrowCE) {
      assertFalse("we expected CE, but it was not thrown", serviceQuerySucceeded)
    }
    else {
      assertTrue("we did not expect CE, but it was thrown", serviceQuerySucceeded)
    }
  }
}

private val testPluginDescriptor: DefaultPluginDescriptor = DefaultPluginDescriptor("test")

private class SimpleService

private val TEST_CLIENT_ID = ClientId("OurTestClientId")
