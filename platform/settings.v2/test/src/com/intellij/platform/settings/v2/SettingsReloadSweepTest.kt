// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.platform.settings.v2.impl.providers.FileLayerService
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

/**
 * Pins the reload mark-sweep (ADR 0065). [SettingsVault.reload] installs what the providers return and **drops** the
 * layers that they no longer supply. The sweep test disables the VFS watcher of the file layer, so only the sweep
 * removes a layer, not a push of the watcher. A reload keeps the held layer while a save is pending for its store.
 * A push that arrives during a reload of its store applies after the reload installs, so the sweep does not drop it
 * (ADR 0123). A held push also applies when the reload is cancelled or fails. A provider that fails a reload keeps its
 * layers.
 */
@TestApplication
class SettingsReloadSweepTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
    private val moduleFixture = projectFixture.moduleFixture()
    private val sourceRootFixture = moduleFixture.sourceRootFixture()

    private val keyA = SettingsKey("test.sweep.a", default = "defA")
    private val keyB = SettingsKey("test.sweep.b", default = "defB")
    private val pendingKey = SettingsKey("test.sweep.pending", default = "def")
    private val pushedKey = SettingsKey("test.sweep.pushed", default = "def")
    private val fenceKey = SettingsKey("test.sweep.fence", default = "def")

    private const val CONFIG_A = """{ "test.sweep.a": "valA" }"""
    private const val CONFIG_B = """{ "test.sweep.b": "valB" }"""
  }

  @TestDisposable
  private lateinit var disposable: Disposable

  private val project get() = projectFixture.get()
  private val sourceRoot get() = sourceRootFixture.get().virtualFile
  private val projectContext get() = SettingsContext.project(project)

  private val testProvider = TestLayerProvider()

  @Test
  fun `reload installs supplied layers and sweeps the ones a vanished config no longer supplies`(): Unit = runBlocking {
    withFileWatcherDisabled(project) {
      // Two configs with different keys: dirA (keyA) and its child dirA/dirB (keyB).
      VfsTestUtil.createFile(sourceRoot, "dirA/${FileLayerService.CONFIG_FILE}", CONFIG_A)
      VfsTestUtil.createFile(sourceRoot, "dirA/dirB/${FileLayerService.CONFIG_FILE}", CONFIG_B)
      val dirA = readAction { sourceRoot.findFileByRelativePath("dirA")!! }
      val dirB = readAction { sourceRoot.findFileByRelativePath("dirA/dirB")!! }
      val ctxA = readAction { SettingsContext.file(dirA, project) }
      val ctxB = readAction { SettingsContext.file(dirB, project) }

      // Both load; keyA resolves at its own directory and is inherited by the child directory.
      reload()
      assertEquals("valA", keyA[ctxA])   // exact context
      assertEquals("valA", keyA[ctxB])   // child context inherits it
      assertEquals("valB", keyB[ctxB])
      assertEquals("defB", keyB[ctxA])   // a parent directory does not see the config of its child

      // Drop dirA's config; a reload sweeps dirA's layer (no longer supplied), dirB's stays.
      VfsTestUtil.deleteFile(readAction { dirA.findChild(FileLayerService.CONFIG_FILE)!! })
      reload()
      assertEquals("defA", keyA[ctxA])   // dirA layer swept → key default
      assertEquals("defA", keyA[ctxB])   // no longer inherited
      assertEquals("valB", keyB[ctxB])   // dirB layer untouched

      // Drop dirB's config, restore dirA's; a reload now sweeps dirB's layer and reinstalls dirA's.
      VfsTestUtil.deleteFile(readAction { dirB.findChild(FileLayerService.CONFIG_FILE)!! })
      VfsTestUtil.createFile(sourceRoot, "dirA/${FileLayerService.CONFIG_FILE}", CONFIG_A)
      reload()
      assertEquals("valA", keyA[ctxA])
      assertEquals("valA", keyA[ctxB])
      assertEquals("defB", keyB[ctxB])   // dirB layer swept → key default

      // Drop the last remaining config; a reload sweeps it too, leaving no file-layer settings in the vault.
      VfsTestUtil.deleteFile(readAction { dirA.findChild(FileLayerService.CONFIG_FILE)!! })
      reload()
      assertEquals("defA", keyA[ctxA])
      assertEquals("defA", keyA[ctxB])
      assertEquals("defB", keyB[ctxB])
    }
  }

  @Test
  fun `a reload keeps the held layer while a save is pending for its store`(): Unit = runBlocking {
    val provider = HeldSaveLayerProvider(pendingKey, "onDisk")
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(provider), disposable)
    val app = SettingsContext.app()
    try {
      pendingKey[app] = "written"
      withTimeout(10.seconds) { provider.saveStarted.await() }  // the save is pending and does not end

      SettingsVault.getInstance().reload(app)

      assertEquals("written", pendingKey[app])  // the reload did not install the layer that the store returned
    }
    finally {
      provider.releaseSave.complete(Unit)
    }
  }

  @Test
  fun `a push during a reload of its store applies after the reload installs`(): Unit = withHeldReload { reload ->
    testProvider.seed(projectContext, pushedKey, "pushed")  // a new layer that the held load did not read
    awaitPushesFolded()

    reload.heldRead.release.complete(Unit)
    reload.job.join()

    assertEquals("pushed", pushedKey[projectContext])  // the sweep of the reload did not drop the pushed layer
  }

  @Test
  fun `a push outside the scope of a running reload applies at once`(): Unit = withHeldReload {
    testProvider.seed(SettingsContext.app(), pushedKey, "pushed")
    awaitEquals("pushed") { pushedKey[SettingsContext.app()] }
  }

  @Test
  fun `a reload ends when its caller's scope is cancelled, and the context is ready again`(): Unit = withHeldReload { reload ->
    assertFalse(projectContext.isReady())

    reload.job.cancelAndJoin()

    assertTrue(reload.job.isCancelled)
    assertTrue(projectContext.isReady())
  }

  @Test
  fun `a held push applies when the reload is cancelled`(): Unit = withHeldReload { reload ->
    testProvider.seed(projectContext, pushedKey, "pushed")
    awaitPushesFolded()

    reload.job.cancelAndJoin()

    assertEquals("pushed", pushedKey[projectContext])
    assertTrue(projectContext.isReady())
  }

  @Test
  fun `a held push applies when a provider fails the reload`(): Unit =
    withHeldReload(failure = IllegalStateException("the load fails")) { reload ->
      testProvider.seed(projectContext, pushedKey, "pushed")
      awaitPushesFolded()

      val (_, loggedErrors) = collectingErrors {
        reload.heldRead.release.complete(Unit)
        reload.job.join()
      }

      assertTrue(loggedErrors.any { "Failed to load settings" in it }, "the failure is logged: $loggedErrors")
      assertEquals("pushed", pushedKey[projectContext])
      assertTrue(projectContext.isReady())
    }

  @Test
  fun `a provider that fails the reload keeps the layers it held`(): Unit = runBlocking {
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(testProvider), disposable)
    projectContext.awaitReady()
    testProvider.seed(projectContext, pushedKey, "kept")
    awaitEquals("kept") { pushedKey[projectContext] }
    testProvider.holdNextRead(projectContext, IllegalStateException("the load fails")).release.complete(Unit)

    val (_, loggedErrors) = collectingErrors { SettingsVault.getInstance().reload(projectContext) }

    assertTrue(loggedErrors.any { "Failed to load settings" in it }, "the failure is logged: $loggedErrors")
    assertEquals("kept", pushedKey[projectContext])
  }

  /**
   * Masks the providers with [testProvider], starts a reload of the project scope, and runs [test] while the load of
   * [testProvider] is held after it read the store. Releases the load when [test] ends.
   *
   * @param failure the exception that the held load throws when released, or `null` for a load that succeeds
   */
  private fun withHeldReload(failure: RuntimeException? = null, test: suspend CoroutineScope.(HeldReload) -> Unit): Unit = runBlocking {
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(testProvider), disposable)
    projectContext.awaitReady()
    val heldRead = testProvider.holdNextRead(projectContext, failure)
    val job = launch { SettingsVault.getInstance().reload(projectContext) }
    try {
      withTimeout(10.seconds) { heldRead.storeRead.await() }
      test(HeldReload(job, heldRead))
    }
    finally {
      heldRead.release.complete(Unit)
    }
  }

  /**
   * Holds the [job] of a reload of the project scope that [withHeldReload] started, and the [heldRead] of
   * [testProvider] in it.
   */
  private class HeldReload(val job: Job, val heldRead: TestLayerProvider.HeldRead)

  /**
   * Waits until the vault has taken every push sent before. The vault takes the pushes in order, and a fence push
   * at the application is outside the scope of a project reload, so it applies at once.
   */
  private suspend fun awaitPushesFolded() {
    testProvider.seed(SettingsContext.app(), fenceKey, "fenced")
    awaitEquals("fenced") { fenceKey[SettingsContext.app()] }
  }

  /**
   * A provider that returns [storedValue] for [key] from every load, and holds every save until [releaseSave]
   * completes. So a save stays pending on demand.
   */
  private class HeldSaveLayerProvider(private val key: SettingsKey<String>, private val storedValue: String) : LayerProvider {
    val saveStarted = CompletableDeferred<Unit>()
    val releaseSave = CompletableDeferred<Unit>()

    override fun availableLocalities(context: SettingsContext): Set<LayerLocality> = setOf(LayerLocality.LOCAL)

    override suspend fun requestData(scopeContext: SettingsContext): List<LayerData> {
      val app = SettingsContext.app()
      if (!scopeContext.isAncestorOf(app, strict = false)) return emptyList()
      return listOf(LayerData.Builder(app, this, LayerLocality.LOCAL).apply { this[key.identity] = SettingsValue.StringValue(storedValue) }.build())
    }

    override suspend fun writeData(storageContext: SettingsContext, locality: LayerLocality, slices: Map<SettingsContext, SettingsValue.MapValue>): SaveResult {
      saveStarted.complete(Unit)
      releaseSave.await()
      return SaveResult.SAVED
    }
  }

  private suspend fun reload() = SettingsVault.getInstance().reload(SettingsContext.project(project))
}
