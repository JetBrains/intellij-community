// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Pins the queue of the listener notifications (ADR 0161). The vault delivers a notification only when the context
 * that it carries is ready. A key-only event carries the context of its change, and a contextual listener carries its
 * own context. A queued contextual listener fires once, and only on a net change. A queued key-only event fires once
 * per pair of a listener and a changed context.
 *
 * Each test masks the providers with [GatedKindLayerProvider]s of one key and reloads the application scope. A closed
 * gate holds the file bucket, so a directory context stays dirty while the application and the project contexts are
 * ready.
 */
@TestApplication
class SettingsQueuedNotificationTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
    private val moduleFixture = projectFixture.moduleFixture()
    private val sourceRootFixture = moduleFixture.sourceRootFixture()

    private val key = SettingsKey("test.queuedNotification.value", default = "def")

    /** Holds the key of the key-only test of a project change. No other test has layers of it to drop. */
    private val keyOnlyKey = SettingsKey("test.queuedNotification.keyOnly", default = "def")

    /** Holds how long a test waits to see that something does not happen. */
    private val QUIET_PERIOD = 200.milliseconds
  }

  @TestDisposable
  private lateinit var disposable: Disposable

  private val app = SettingsContext.app()
  private val project get() = projectFixture.get()
  private val projectContext get() = SettingsContext.project(project)

  private val appProvider = GatedKindLayerProvider(APPLICATION_KINDS, key)
  private val projectProvider = GatedKindLayerProvider(PROJECT_KINDS, key)
  private val fileProvider = GatedKindLayerProvider(FILE_KINDS, key)

  @Test
  fun `a contextual listener at a directory waits for the file bucket and fires once on a net change`(): Unit = runBlocking {
    val dirContext = createDirContext("net-change")
    registerProviders()
    projectProvider.values[projectContext] = "project first"
    reload()
    val seenValues = listenAt(dirContext)

    projectProvider.values[projectContext] = "project second"
    fileProvider.values[dirContext] = "dir value"
    withFileBlockedReload { reloadJob ->
      assertEquals("project second", key[dirContext])  // the project bucket changed the value at the directory
      delay(QUIET_PERIOD)
      assertEquals(emptyList<String>(), seenValues)

      fileProvider.gate?.complete(Unit)
      reloadJob.join()
      assertEquals(listOf("dir value"), seenValues)
    }
  }

  @Test
  fun `a contextual listener at a directory does not fire when the file bucket restores its value`(): Unit = runBlocking {
    val dirContext = createDirContext("restored-value")
    registerProviders()
    projectProvider.values[projectContext] = "project first"
    reload()
    val seenValues = listenAt(dirContext)

    projectProvider.values[projectContext] = "project second"
    fileProvider.values[dirContext] = "project first"  // the directory config overrides the project value back
    withFileBlockedReload { reloadJob ->
      assertEquals("project second", key[dirContext])

      fileProvider.gate?.complete(Unit)
      reloadJob.join()
      assertEquals("project first", key[dirContext])
      assertEquals(emptyList<String>(), seenValues)
    }
  }

  @Test
  fun `an application write during a reload fires a project listener at once and a directory listener after the file bucket`(): Unit =
    runBlocking {
      val dirContext = createDirContext("app-write")
      registerProviders()
      val projectValues = listenAt(projectContext)
      val dirValues = listenAt(dirContext)

      withFileBlockedReload { reloadJob ->
        key[app] = "app write"
        assertEquals(listOf("app write"), projectValues)  // a write delivers the notifications at a ready context before it returns
        delay(QUIET_PERIOD)
        assertEquals(emptyList<String>(), dirValues)

        fileProvider.gate?.complete(Unit)
        reloadJob.join()
        assertEquals(listOf("app write"), dirValues)
        assertEquals(listOf("app write"), projectValues)
      }
    }

  @Test
  fun `a key-only event of a project change arrives before the file bucket, and of a directory change after it`(): Unit = runBlocking {
    val dirContext = createDirContext("key-only")
    val keyOnlyAppProvider = GatedKindLayerProvider(APPLICATION_KINDS, keyOnlyKey)
    val keyOnlyProjectProvider = GatedKindLayerProvider(PROJECT_KINDS, keyOnlyKey)
    val keyOnlyFileProvider = GatedKindLayerProvider(FILE_KINDS, keyOnlyKey)
    registerProviders(keyOnlyAppProvider, keyOnlyProjectProvider, keyOnlyFileProvider)
    val changeContexts = CopyOnWriteArrayList<String>()
    keyOnlyKey.addListener(disposable) { _, context -> changeContexts.add(context.identity) }

    keyOnlyProjectProvider.values[projectContext] = "project value"
    keyOnlyFileProvider.values[dirContext] = "dir value"
    val fileGate = keyOnlyFileProvider.closeGate()
    try {
      val reloadJob = launchReload()
      keyOnlyFileProvider.awaitRequests(2)
      withTimeout(10.seconds) { projectContext.awaitReady() }
      awaitEquals(listOf(projectContext.identity)) { changeContexts.toList() }
      delay(QUIET_PERIOD)
      assertEquals(listOf(projectContext.identity), changeContexts.toList())

      fileGate.complete(Unit)
      reloadJob.join()
      assertEquals(listOf(projectContext.identity, dirContext.identity), changeContexts.toList())
    }
    finally {
      fileGate.complete(Unit)
    }
  }

  @Test
  fun `two changes at a dirty directory fire a contextual and a key-only listener once`(): Unit = runBlocking {
    val dirContext = createDirContext("two-changes")
    registerProviders()
    val seenValues = listenAt(dirContext)
    val dirChanges = listenKeyOnlyAt(dirContext)

    withTwoChangesAtDirtyDirectory(dirContext) { secondReload ->
      delay(QUIET_PERIOD)
      assertEquals(emptyList<String>(), seenValues)
      assertEquals(0, dirChanges.size)

      fileProvider.gate?.complete(Unit)
      secondReload.join()
      assertEquals(listOf("dir second"), seenValues)
      assertEquals(1, dirChanges.size)
    }
  }

  @Test
  fun `the queue delivers when the file provider fails`(): Unit = runBlocking {
    val dirContext = createDirContext("failed-provider")
    registerProviders()
    val seenValues = listenAt(dirContext)

    projectProvider.values[projectContext] = "project value"
    fileProvider.failure = IllegalStateException("the load fails")
    withFileBlockedReload { reloadJob ->
      delay(QUIET_PERIOD)
      assertEquals(emptyList<String>(), seenValues)

      val (_, loggedErrors) = collectingErrors {
        fileProvider.gate?.complete(Unit)
        reloadJob.join()
      }
      assertTrue(loggedErrors.any { "Failed to load settings" in it }, "the failure is logged: $loggedErrors")
      assertEquals(listOf("project value"), seenValues)
    }
  }

  @Test
  fun `the queue delivers when the reload is cancelled`(): Unit = runBlocking {
    val dirContext = createDirContext("cancelled-reload")
    registerProviders()
    val seenValues = listenAt(dirContext)

    projectProvider.values[projectContext] = "project value"
    withFileBlockedReload { reloadJob ->
      delay(QUIET_PERIOD)
      assertEquals(emptyList<String>(), seenValues)

      reloadJob.cancelAndJoin()
      assertTrue(dirContext.isReady())
      assertEquals(listOf("project value"), seenValues)
    }
  }

  @Test
  fun `a listener disposed while its notification is queued does not fire`(): Unit = runBlocking {
    val dirContext = createDirContext("disposed-listener")
    registerProviders()
    val listenerDisposable = Disposer.newDisposable(disposable, "queued listeners")
    val disposedFires = CopyOnWriteArrayList<String>()
    key.addListener(listenerDisposable, dirContext) { _, _ -> disposedFires.add("contextual") }
    key.addListener(listenerDisposable) { _, context -> if (context == dirContext) disposedFires.add("key-only") }
    val seenValues = listenAt(dirContext)

    withTwoChangesAtDirtyDirectory(dirContext) { secondReload ->
      Disposer.dispose(listenerDisposable)

      fileProvider.gate?.complete(Unit)
      secondReload.join()
      assertEquals(listOf("dir second"), seenValues)
      assertEquals(emptyList<String>(), disposedFires)
    }
  }

  /**
   * Starts a reload whose file bucket waits for a closed gate, waits until the project context is ready, and runs
   * [test]. Opens the gate when [test] ends.
   */
  private suspend fun CoroutineScope.withFileBlockedReload(test: suspend (reloadJob: Job) -> Unit) {
    val fileGate = fileProvider.closeGate()
    val requestsBefore = fileProvider.requests.value
    try {
      val reloadJob = launchReload()
      fileProvider.awaitRequests(requestsBefore + 1)
      withTimeout(10.seconds) { projectContext.awaitReady() }
      test(reloadJob)
    }
    finally {
      fileGate.complete(Unit)
    }
  }

  /**
   * Changes the value at [dirContext] twice while it is dirty, and runs [test]. A first reload installs "dir first" at
   * [dirContext] while a second reload keeps it dirty. The second reload then reads "dir second", and its file bucket
   * waits for the closed gate of [fileProvider] during [test]. Opens the gates when [test] ends.
   */
  private suspend fun CoroutineScope.withTwoChangesAtDirtyDirectory(dirContext: SettingsContext, test: suspend (secondReload: Job) -> Unit) {
    fileProvider.values[dirContext] = "dir first"
    val firstGate = fileProvider.closeGate()
    try {
      val firstReload = launchReload()
      fileProvider.awaitRequests(2)
      val secondReload = launchReload()
      appProvider.awaitRequests(3)  // the second reload has started, so it keeps the directory dirty

      fileProvider.values[dirContext] = "dir second"
      fileProvider.closeGate()
      firstGate.complete(Unit)
      firstReload.join()
      fileProvider.awaitRequests(3)
      assertEquals("dir first", key[dirContext])  // the first file bucket has installed
      assertFalse(dirContext.isReady())
      test(secondReload)
    }
    finally {
      firstGate.complete(Unit)
      fileProvider.gate?.complete(Unit)
    }
  }

  /**
   * Registers a contextual listener of [key] at [context].
   *
   * @return the values that the listener reads at each call
   */
  private fun listenAt(context: SettingsContext): List<String> {
    val seenValues = CopyOnWriteArrayList<String>()
    key.addListener(disposable, context) { _, _ -> seenValues.add(key[context]) }
    return seenValues
  }

  /**
   * Registers a key-only listener of [key] that counts the calls with [context]. It ignores the other contexts, where
   * the providers of an earlier test can still drop their layers.
   *
   * @return the calls with [context]
   */
  private fun listenKeyOnlyAt(context: SettingsContext): List<SettingsContext> {
    val calls = CopyOnWriteArrayList<SettingsContext>()
    key.addListener(disposable) { _, changeContext -> if (changeContext == context) calls.add(changeContext) }
    return calls
  }

  private suspend fun registerProviders(vararg providers: GatedKindLayerProvider = arrayOf(appProvider, projectProvider, fileProvider)) {
    projectContext.awaitReady()
    maskWithGatedProviders(disposable, *providers)
  }
  /** Returns the context of a new directory [dirName] under the source root. */
  private suspend fun createDirContext(dirName: String): SettingsContext {
    val dir = VfsTestUtil.createDir(sourceRootFixture.get().virtualFile, dirName)
    return readAction { SettingsContext.file(dir, project) }
  }

  private fun CoroutineScope.launchReload(): Job = launch { reload() }

  private suspend fun reload() = SettingsVault.getInstance().reload(app)
}
