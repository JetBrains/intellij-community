// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.platform.settings.v2.SettingsValue.StringValue
import com.intellij.platform.settings.v2.impl.ShutdownSaver
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.registerServiceInstance
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.unregisterService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

/**
 * Checks the shutdown save of `SettingsAppLifecycleListener`. The test publishes
 * [AppLifecycleListener.beforeAppWillBeClosed] on the EDT under the write-intent lock, as the platform does.
 *
 * The debounced save must not reach the provider before the check, or a broken shutdown save also passes. So the
 * test holds a first debounced save in [TestLayerProvider.writeData], and then writes the pending value. The save of
 * the pending value waits for the held save. The flush of the shutdown save releases the held save when it collects
 * its storage units through [TestLayerProvider.onStorageContext]. With no flush, the held save stays held, and the
 * provider never receives the pending value.
 *
 * The production [ShutdownSaver] comes from `intellij.platform.settings.v2.shared.essential`, which the test module
 * has on its classpath.
 */
@TestApplication
class SettingsShutdownSaveTest {
  companion object {
    private val written = SettingsKey("test.shutdownSave.written", default = "def")
  }

  @TestDisposable
  private lateinit var disposable: Disposable

  private val provider = TestLayerProvider()
  private val app = SettingsContext.app()
  private val application get() = ApplicationManager.getApplication()

  @Test
  fun `the shutdown save runs through the registered saver`(): Unit = timeoutRunBlocking {
    val productionSaver = checkNotNull(ShutdownSaver.getInstanceOrNull()) { "the product registers no shutdown saver" }
    val saves = AtomicInteger()
    application.replaceService(ShutdownSaver::class.java, object : ShutdownSaver {
      override fun save() {
        saves.incrementAndGet()
        productionSaver.save()
      }
    }, disposable)

    assertEquals(StringValue("with a saver"), valueSavedByClose("with a saver"))
    assertEquals(1, saves.get())
  }

  @Test
  fun `the shutdown save with no saver writes a pending change`(): Unit = timeoutRunBlocking {
    // The replacement lets the disposal restore the production saver after the unregistration below.
    val placeholder = object : ShutdownSaver {
      override fun save(): Unit = error("no saver is registered")
    }
    application.replaceService(ShutdownSaver::class.java, placeholder, disposable)
    application.unregisterService(ShutdownSaver::class.java)
    try {
      assertEquals(StringValue("with no saver"), valueSavedByClose("with no saver"))
    }
    finally {
      application.registerServiceInstance(ShutdownSaver::class.java, placeholder)
    }
  }

  /**
   * Writes [pending] while a debounced save of [written] is held, then closes the application.
   *
   * @param pending the value that only the shutdown save can bring to the provider before the check
   * @return the value of [written] that the provider holds right after the close
   */
  private suspend fun valueSavedByClose(pending: String): SettingsValue? {
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(provider), disposable)
    app.awaitReady()
    val heldSave = provider.holdNextSave(SaveResult.SAVED)
    try {
      written[app] = "before $pending"
      withTimeout(10.seconds) { heldSave.started.await() }
      written[app] = pending
      provider.onStorageContext = { heldSave.release.complete(Unit) }
      withContext(Dispatchers.EDT) {
        application.messageBus.syncPublisher(AppLifecycleListener.TOPIC).beforeAppWillBeClosed(false)
      }
      return provider.saved(app, LayerLocality.LOCAL)?.get(written)
    }
    finally {
      provider.onStorageContext = null
      heldSave.release.complete(Unit)
    }
  }
}
