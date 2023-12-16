// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("SSBasedInspection")

package com.intellij.testFramework.junit5.impl

import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.testFramework.common.*
import com.intellij.util.ui.EDT
import kotlinx.coroutines.*
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import java.time.Duration
import java.util.concurrent.TimeUnit

@TestOnly
internal class TestApplicationExtension : BeforeAllCallback, AfterEachCallback {
  override fun beforeAll(context: ExtensionContext) {
    context.testApplication().getOrThrow()
  }

  override fun afterEach(context: ExtensionContext) {
    ApplicationManager.getApplication().cleanApplicationState()
  }
}

@TestOnly
private fun ExtensionContext.testApplication(): Result<Unit> {
  val store = root.getStore(ExtensionContext.Namespace.GLOBAL)
  val resource = store.getOrComputeIfAbsent("application") {
    TestApplicationResource(initTestApplication())
  } as TestApplicationResource
  return resource.initializationResult
}

@TestOnly
private class TestApplicationResource(val initializationResult: Result<Unit>) : ExtensionContext.Store.CloseableResource {
  override fun close() {
    check(!EDT.isCurrentThreadEdt())
    if (!initializationResult.isSuccess) {
      return
    }

    runBlocking {
      withTimeout(Duration.ofSeconds(20).toMillis()) {
        withContext(Dispatchers.EDT) {
          val application = ApplicationManager.getApplication()
          application.messageBus.syncPublisher(AppLifecycleListener.TOPIC).appWillBeClosed(false)
          yield()
          withContext(Dispatchers.IO) {
            runInterruptible {
              waitForAppLeakingThreads(application, 10, TimeUnit.SECONDS)
            }
          }
          assertNonDefaultProjectsAreNotLeaked() // TODO? ability to disable this check for local (=non-build-server) runs
          yield()
          disposeTestApplication()
        }
        assertDisposerEmpty()
      }
    }
  }
}
