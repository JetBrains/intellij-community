// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
    context.root.getStore(ExtensionContext.Namespace.GLOBAL).testApplication()
  }

  override fun afterEach(context: ExtensionContext) {
    ApplicationManager.getApplication().cleanApplicationState()
  }
}

@TestOnly
private fun ExtensionContext.Store.testApplication() {
  getOrComputeIfAbsent("application") {
    initTestApplication()
    TestApplicationResource()
  }
}

@TestOnly
private class TestApplicationResource : ExtensionContext.Store.CloseableResource {

  override fun close() {
    check(!EDT.isCurrentThreadEdt())
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
