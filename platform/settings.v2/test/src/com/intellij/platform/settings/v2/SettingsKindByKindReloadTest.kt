// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Pins the install kind by kind (ADR 0160). A reload installs one bucket per context kind. The bucket of a kind
 * installs when every provider whose range of context kinds contains the kind has answered, and it sweeps only the
 * stale layers of its kind. A context gets ready when the bucket of its kind and the buckets of every wider kind have
 * installed. A held push applies when the context of its store gets ready.
 *
 * Each test masks the providers with [GatedKindLayerProvider]s of one key and reloads the application scope. A closed
 * gate holds the loads of a provider, so the test controls the order in which the providers answer.
 */
@TestApplication
class SettingsKindByKindReloadTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
    private val moduleFixture = projectFixture.moduleFixture()
    private val sourceRootFixture = moduleFixture.sourceRootFixture()

    private val key = SettingsKey("test.kindByKind.value", default = "def")

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
  fun `a slow file provider keeps only the directory and the file contexts dirty`(): Unit = runBlocking {
    val (dirContext, fileContext) = createFileContexts("slow-file")
    registerProviders(appProvider, projectProvider, fileProvider)
    appProvider.values[app] = "app value"
    projectProvider.values[projectContext] = "project value"
    fileProvider.values[dirContext] = "dir value"
    val fileGate = fileProvider.closeGate()

    val reloadJob = launchReload()
    fileProvider.awaitRequests(2)
    withTimeout(10.seconds) { projectContext.awaitReady() }

    assertTrue(app.isReady())
    assertEquals("app value", key[app])
    assertEquals("project value", key[projectContext])
    assertFalse(dirContext.isReady())
    assertFalse(fileContext.isReady())
    assertEquals("project value", key[dirContext])  // the file bucket has not installed

    withTimeout(10.seconds) { key[projectContext] = "project write" }  // a project write does not wait for the file bucket
    assertEquals("project write", key[projectContext])

    val fileWrite = launch { key[fileContext] = "file write" }
    delay(QUIET_PERIOD)
    assertTrue(fileWrite.isActive)  // a file write waits for the file bucket

    fileGate.complete(Unit)
    fileWrite.join()
    reloadJob.join()
    assertEquals("dir value", key[dirContext])
    assertEquals("file write", key[fileContext])
    assertTrue(fileContext.isReady())
  }

  @Test
  fun `a file context stays dirty until the project bucket installs, also when the file bucket is first`(): Unit = runBlocking {
    val (dirContext, fileContext) = createFileContexts("file-first")
    registerProviders(appProvider, projectProvider, fileProvider)
    projectProvider.values[projectContext] = "project value"
    fileProvider.values[dirContext] = "dir value"
    val projectGate = projectProvider.closeGate()

    val reloadJob = launchReload()
    projectProvider.awaitRequests(2)
    awaitEquals("dir value") { key[dirContext] }  // the file bucket has installed
    withTimeout(10.seconds) { app.awaitReady() }

    assertFalse(projectContext.isReady())
    assertFalse(dirContext.isReady())
    assertFalse(fileContext.isReady())

    projectGate.complete(Unit)
    reloadJob.join()
    assertEquals("project value", key[projectContext])
    assertTrue(dirContext.isReady())
    assertTrue(fileContext.isReady())
  }

  @Test
  fun `a provider of the project and the file kinds delays both buckets until it answers`(): Unit = runBlocking {
    val (dirContext, _) = createFileContexts("multi-kind-delays")
    val (otherDirContext, _) = createFileContexts("multi-kind-delays-other")
    val projectAndFileProvider = GatedKindLayerProvider(PROJECT_AND_FILE_KINDS, key)
    registerProviders(appProvider, projectAndFileProvider, fileProvider)
    appProvider.values[app] = "app value"
    projectAndFileProvider.values[projectContext] = "project value"
    projectAndFileProvider.values[dirContext] = "wide dir value"
    fileProvider.values[otherDirContext] = "other dir value"
    val projectAndFileGate = projectAndFileProvider.closeGate()

    val reloadJob = launchReload()
    projectAndFileProvider.awaitRequests(2)
    fileProvider.awaitRequests(2)
    withTimeout(10.seconds) { app.awaitReady() }
    delay(QUIET_PERIOD)

    assertEquals("app value", key[otherDirContext])  // the file provider has answered, but the file bucket waits
    assertFalse(projectContext.isReady())
    assertFalse(otherDirContext.isReady())

    projectAndFileGate.complete(Unit)
    reloadJob.join()
    assertEquals("project value", key[projectContext])
    assertEquals("wide dir value", key[dirContext])
    assertEquals("other dir value", key[otherDirContext])
  }

  @Test
  fun `each bucket installs only the layers of its own kind`(): Unit = runBlocking {
    val (dirContext, _) = createFileContexts("multi-kind-installs")
    val projectAndFileProvider = GatedKindLayerProvider(PROJECT_AND_FILE_KINDS, key)
    registerProviders(appProvider, projectAndFileProvider, fileProvider)
    projectAndFileProvider.values[projectContext] = "project value"
    projectAndFileProvider.values[dirContext] = "wide dir value"
    val fileGate = fileProvider.closeGate()

    val reloadJob = launchReload()
    fileProvider.awaitRequests(2)
    withTimeout(10.seconds) { projectContext.awaitReady() }

    assertEquals("project value", key[projectContext])
    assertEquals("project value", key[dirContext])  // the directory layer of the same answer waits for the file bucket

    fileGate.complete(Unit)
    reloadJob.join()
    assertEquals("wide dir value", key[dirContext])
  }

  @Test
  fun `the sweep of a bucket removes only the stale layers of its own kind`(): Unit = runBlocking {
    val (dirContext, _) = createFileContexts("sweep-per-kind")
    val projectAndFileProvider = GatedKindLayerProvider(PROJECT_AND_FILE_KINDS, key)
    registerProviders(appProvider, projectAndFileProvider, fileProvider)
    appProvider.values[app] = "app value"
    projectAndFileProvider.values[projectContext] = "project value"
    projectAndFileProvider.values[dirContext] = "wide dir value"
    reload()
    assertEquals("project value", key[projectContext])
    assertEquals("wide dir value", key[dirContext])

    projectAndFileProvider.values.clear()
    val fileGate = fileProvider.closeGate()
    val reloadJob = launchReload()
    fileProvider.awaitRequests(3)
    withTimeout(10.seconds) { projectContext.awaitReady() }

    assertEquals("app value", key[projectContext])  // the project bucket swept the stale project layer
    assertEquals("wide dir value", key[dirContext])  // the stale directory layer waits for the sweep of the file bucket

    fileGate.complete(Unit)
    reloadJob.join()
    assertEquals("app value", key[dirContext])
  }

  @Test
  fun `a held push of a project store applies when the project bucket installs, before the file bucket`(): Unit = runBlocking {
    val (dirContext, _) = createFileContexts("held-project-push")
    registerProviders(appProvider, projectProvider, fileProvider)
    projectProvider.values[projectContext] = "project value"
    val projectGate = projectProvider.closeGate()
    val fileGate = fileProvider.closeGate()

    val reloadJob = launchReload()
    projectProvider.awaitRequests(2)
    fileProvider.awaitRequests(2)
    SettingsVault.getInstance().push(projectProvider.layer(projectContext, "pushed"))
    awaitPushesTaken()
    assertEquals("fenced", key[projectContext])  // the push is held

    projectGate.complete(Unit)
    awaitEquals("pushed") { key[projectContext] }  // the push applies after the install of the project bucket
    assertTrue(projectContext.isReady())
    assertFalse(dirContext.isReady())

    fileGate.complete(Unit)
    reloadJob.join()
    assertEquals("pushed", key[projectContext])
  }

  @Test
  fun `a held push of a directory store waits for the file bucket`(): Unit = withHeldFileBucket("held-dir-push") { dirContext, reload ->
    reload.fileGate.complete(Unit)
    reload.job.join()

    assertEquals("pushed", key[dirContext])  // the push applies after the install of the file bucket
    assertTrue(dirContext.isReady())
  }

  @Test
  fun `a held push of a directory store applies when the reload is cancelled`(): Unit =
    withHeldFileBucket("held-dir-push-cancel") { dirContext, reload ->
      reload.job.cancelAndJoin()

      assertEquals("pushed", key[dirContext])
      assertTrue(dirContext.isReady())
    }

  @Test
  fun `a held push of a directory store applies when the file provider fails`(): Unit =
    withHeldFileBucket("held-dir-push-failure", failure = IllegalStateException("the load fails")) { dirContext, reload ->
      val (_, loggedErrors) = collectingErrors {
        reload.fileGate.complete(Unit)
        reload.job.join()
      }

      assertTrue(loggedErrors.any { "Failed to load settings" in it }, "the failure is logged: $loggedErrors")
      assertEquals("pushed", key[dirContext])
      assertTrue(dirContext.isReady())
    }

  /**
   * Starts a reload whose file bucket waits for a closed gate. Pushes a directory layer of [fileProvider] once the
   * project context is ready, checks that the vault holds it, and runs [test]. Opens the gate when [test] ends.
   *
   * @param dirName the name of the directory that this test creates
   * @param failure the exception that the held load of [fileProvider] throws, or `null` for a load that succeeds
   */
  private fun withHeldFileBucket(
    dirName: String,
    failure: RuntimeException? = null,
    test: suspend CoroutineScope.(dirContext: SettingsContext, reload: HeldFileBucket) -> Unit,
  ): Unit = runBlocking {
    val (dirContext, _) = createFileContexts(dirName)
    registerProviders(appProvider, projectProvider, fileProvider)
    projectProvider.values[projectContext] = "project value"
    fileProvider.values[dirContext] = "dir value"
    fileProvider.failure = failure
    val fileGate = fileProvider.closeGate()
    val job = launchReload()
    try {
      fileProvider.awaitRequests(2)
      withTimeout(10.seconds) { projectContext.awaitReady() }
      SettingsVault.getInstance().push(fileProvider.layer(dirContext, "pushed"))
      awaitPushesTaken()
      assertEquals("project value", key[dirContext])  // the push is held
      test(dirContext, HeldFileBucket(job, fileGate))
    }
    finally {
      fileGate.complete(Unit)
    }
  }

  /** Holds the [job] of a reload that [withHeldFileBucket] started, and the [fileGate] that holds its file bucket. */
  private class HeldFileBucket(val job: Job, val fileGate: CompletableDeferred<Unit>)

  private suspend fun registerProviders(vararg providers: GatedKindLayerProvider) {
    projectContext.awaitReady()
    maskWithGatedProviders(disposable, *providers)
  }

  /**
   * Returns the contexts of a new directory [dirName] under the source root and of a file in it. The file context
   * resolves through the directory context and the project context.
   */
  private suspend fun createFileContexts(dirName: String): Pair<SettingsContext, SettingsContext> {
    val file = VfsTestUtil.createFile(sourceRootFixture.get().virtualFile, "$dirName/a.txt")
    return readAction { SettingsContext.file(file.parent, project) to SettingsContext.file(file, project) }
  }

  /**
   * Waits until the vault has taken every push sent before. The vault takes the pushes in order. A fence push at the
   * application context applies at once, because the application bucket installs without a gate.
   */
  private suspend fun awaitPushesTaken() {
    withTimeout(10.seconds) { app.awaitReady() }
    SettingsVault.getInstance().push(appProvider.layer(app, "fenced"))
    awaitEquals("fenced") { key[app] }
  }

  private fun CoroutineScope.launchReload(): Job = launch { reload() }

  private suspend fun reload() = SettingsVault.getInstance().reload(app)
}
