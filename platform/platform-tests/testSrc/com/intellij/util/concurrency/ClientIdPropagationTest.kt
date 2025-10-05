// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency

import com.intellij.codeWithMe.*
import com.intellij.concurrency.currentThreadContext
import com.intellij.concurrency.installThreadContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.client.ClientAppSession
import com.intellij.openapi.client.ClientAppSessionImpl
import com.intellij.openapi.client.ClientSessionsManager
import com.intellij.openapi.client.ClientType
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.Alarm
import com.intellij.util.application
import io.kotest.assertions.failure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities
import kotlin.coroutines.coroutineContext

private val TEST_CLIENT_ID = ClientId("OurTestClientId")
private val TEST_CLIENT_ID_2 = ClientId("OurTestClientId2")
private val TEST_CLIENT_ID_3 = ClientId("OurTestClientId3")

class ClientIdPropagationTest : LightPlatformTestCase() {

  @Service(Service.Level.APP)
  class ScopeHolder(val cs: CoroutineScope)
  
  fun testSwingInvokeLater() = doTest {
    SwingUtilities.invokeLater(::completeWithThreadLocal)
  }

  fun testApplicationInvokeLater() = doTest {
    application.invokeLater(::completeWithThreadLocal)
  }

  fun testAlarm() = doTest {
    Alarm(testRootDisposable).addRequest(::completeWithThreadLocal, 10)
  }

  fun testAppExecutorService() = doTest {
    AppExecutorUtil.getAppExecutorService().submit(::completeWithThreadLocal)
  }

  fun testChildCoroutine() = doTest {
    runBlocking(ClientId.coroutineContext()) {
      launch(Dispatchers.EDT) {
        completeForCoroutine()
      }
    }
  }

  fun testChildCoroutineViaThreadContext() = doTest {
    runBlocking(currentThreadContext()) {
      launch(Dispatchers.EDT) {
        completeForCoroutine()
      }
    }
  }

  /**
   * Check that ClientId from thread-local flows automatically into launch
   * If to disable [ClientIdContextElementPrecursor] in [ComponentManagerImpl] the test becomes broken
   */
  fun testLaunchPropagationFromThreadLocal() = doTest {
    runBlocking(currentThreadContext()) {
      val serviceScope = service<ScopeHolder>().cs
      assert(serviceScope.coroutineContext.clientIdContextElement == null) { "Scope should not contain ClientId" }
      serviceScope.launch (Dispatchers.EDT) {
        completeForCoroutine()
      }
    }
  }

  /**
   * Check that ClientId from thread-local doesn't overwrite manually passed into the `launch` ClientId
   */
  fun testLaunchPropagation_argumentPreferredOverThreadLocal() = doTest(expectedClientId = TEST_CLIENT_ID_2) {
    runBlocking(currentThreadContext()) {
      val serviceScope = service<ScopeHolder>().cs
      assert(serviceScope.coroutineContext.clientIdContextElement == null) { "Scope should not contain ClientId" }
      serviceScope.launch (Dispatchers.EDT + TEST_CLIENT_ID_2.asContextElement()) {
        completeForCoroutine()
      }
    }
  }

  /**
   * Check that ClientId from thread-local doesn't override a ClientId provided from the coroutine scope
   */
  fun testLaunchPropagation_scopeClientIdPreferredOverThreadLocal() = doTest(expectedClientId = TEST_CLIENT_ID_3, controllerContainerClientId = TEST_CLIENT_ID_3) {
    runBlocking(currentThreadContext()) {
      val session = ClientSessionsManager.getAppSession(TEST_CLIENT_ID_3) as? ComponentManagerEx ?: io.kotest.assertions.fail("No session for $TEST_CLIENT_ID_3")
      val clientScope = session.getCoroutineScope()
      assert(clientScope.coroutineContext.clientIdContextElement?.clientId != TEST_CLIENT_ID) { "Scope shouldn't contain ClientId from thread-local" }
      assert(currentThreadClientId != TEST_CLIENT_ID_3) { "Current thread-local ClientId shouldn't equal to the controller ClientId" }
      clientScope.launch (Dispatchers.EDT) {
        completeForCoroutine()
      }
    }
  }

  /**
   * Check that ClientId from the launch argument is preferred over scope's one and thread-local's one
   */
  fun testLaunchPropagation_argumentPreferredOverScopeClientId() = doTest(expectedClientId = TEST_CLIENT_ID_2, controllerContainerClientId = TEST_CLIENT_ID_3) {
    runBlocking(currentThreadContext()) {
      val session = ClientSessionsManager.getAppSession(TEST_CLIENT_ID_3) as? ComponentManagerEx ?: io.kotest.assertions.fail("No session for $TEST_CLIENT_ID_3")
      val clientScope = session.getCoroutineScope()
      assert(clientScope.coroutineContext.clientIdContextElement?.clientId != TEST_CLIENT_ID) { "Scope shouldn't contain ClientId from thread-local" }
      assert(currentThreadClientId != TEST_CLIENT_ID_3) { "Current thread-local ClientId shouldn't equal to the controller ClientId" }
      clientScope.launch (Dispatchers.EDT + TEST_CLIENT_ID_2.asContextElement()) {
        completeForCoroutine()
      }
    }
  }

  private val resultClientId = CompletableFuture<ClientId?>()

  private fun completeWithClientId(clientId: ClientId?) {
    resultClientId.complete(clientId)
  }

  private fun completeWithThreadLocal() {
    completeWithClientId(currentThreadClientId)
  }


  private suspend fun completeForCoroutine() {
    val clientIdFromThreadLocal = currentThreadClientId
    val clientIdFromContext = coroutineContext.clientIdContextElement?.clientId
    if (clientIdFromContext != clientIdFromThreadLocal) {
      resultClientId.completeExceptionally(failure("Thread-local $clientIdFromThreadLocal and coroutine context $clientIdFromContext must be equal"))
    }
    else {
      resultClientId.complete(clientIdFromContext)
    }
  }


  private fun doTest(expectedClientId: ClientId = TEST_CLIENT_ID, controllerContainerClientId: ClientId = TEST_CLIENT_ID, testRunnable: Runnable) {
    service<ClientSessionsManager<ClientAppSession>>().registerSession(testRootDisposable,
                                                                       TestClientAppSession(application as ApplicationImpl, controllerContainerClientId))
    installThreadContext(ClientIdContextElement(TEST_CLIENT_ID)) {
      testRunnable.run()
    }

    val clientId = resultClientId.get(10, TimeUnit.SECONDS)
    kotlin.test.assertEquals(expectedClientId, clientId, "Unexpected clientId value: $clientId")
  }

  override fun runInDispatchThread() = false

  class TestClientAppSession(application: ApplicationImpl, clientId: ClientId)
    : ClientAppSessionImpl(clientId, ClientType.CONTROLLER, application) {
    override val name: String
      get() = "TestAppSession"
  }
}
