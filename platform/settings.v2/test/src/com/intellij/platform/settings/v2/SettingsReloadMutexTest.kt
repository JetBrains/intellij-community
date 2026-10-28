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
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.seconds

/**
 * Pins the reload mutex per context kind (ADR 0162). A reload takes the mutexes from the application down to the file
 * kind, hand over hand. It asks a provider under the mutex of the widest kind of the provider, and it installs a
 * bucket under the mutex of its kind. So a later reload can install its wide buckets while an earlier reload waits
 * for a slow file provider. A failed or a cancelled reload releases every mutex that it holds.
 *
 * Each test masks the providers with [GatedKindLayerProvider]s of one key and reloads the application scope. A closed
 * gate holds the loads of a provider.
 */
@TestApplication
class SettingsReloadMutexTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
    private val moduleFixture = projectFixture.moduleFixture()
    private val sourceRootFixture = moduleFixture.sourceRootFixture()

    private val key = SettingsKey("test.reloadMutex.value", default = "def")
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
  fun `a reload that no other reload blocks asks every provider at once`(): Unit = runBlocking {
    registerProviders()
    val gates = listOf(appProvider.closeGate(), projectProvider.closeGate(), fileProvider.closeGate())
    try {
      val reloadJob = launchReload()

      appProvider.awaitRequests(2)
      projectProvider.awaitRequests(2)
      fileProvider.awaitRequests(2)  // each provider is asked before any provider answers

      gates.forEach { it.complete(Unit) }
      reloadJob.join()
    }
    finally {
      gates.forEach { it.complete(Unit) }
    }
  }

  @Test
  fun `a later reload installs its wide buckets while an earlier reload waits for its file bucket`(): Unit = runBlocking {
    val dirContext = createDirContext("later-reload")
    registerProviders()
    appProvider.values[app] = "app first"
    projectProvider.values[projectContext] = "project first"
    fileProvider.values[dirContext] = "dir first"
    val fileGate = fileProvider.closeGate()
    try {
      val firstReload = launchReload()
      fileProvider.awaitRequests(2)
      withTimeout(10.seconds) { projectContext.awaitReady() }

      appProvider.values[app] = "app second"
      projectProvider.values[projectContext] = "project second"
      fileProvider.values[dirContext] = "dir second"
      val dirValuesAtFileAsk = CopyOnWriteArrayList<String>()
      fileProvider.onRequest = { dirValuesAtFileAsk.add(key[dirContext]) }
      val secondReload = launchReload()
      awaitEquals("project second") { key[projectContext] }
      withTimeout(10.seconds) { projectContext.awaitReady() }

      assertEquals("app second", key[app])
      assertFalse(dirContext.isReady())
      assertEquals(2, fileProvider.requests.value)  // the second reload waits for the file mutex

      fileGate.complete(Unit)
      secondReload.join()
      firstReload.join()
      assertEquals(listOf("dir first"), dirValuesAtFileAsk)  // the second ask came after the install of the first file bucket
      assertEquals("dir second", key[dirContext])
      assertTrue(dirContext.isReady())
    }
    finally {
      fileGate.complete(Unit)
    }
  }

  @Test
  fun `a cancelled reload releases its mutexes and its pending kinds`(): Unit = runBlocking {
    val dirContext = createDirContext("cancelled-reload")
    registerProviders()
    val reloadJob = launchFileBlockedReload()

    reloadJob.cancelAndJoin()

    assertTrue(dirContext.isReady())
    fileProvider.gate = null
    fileProvider.values[dirContext] = "dir value"
    withTimeout(10.seconds) { reload() }
    assertEquals("dir value", key[dirContext])
  }

  @Test
  fun `a reload cancelled while it waits for the file mutex releases the mutexes it holds`(): Unit = runBlocking {
    val dirContext = createDirContext("cancelled-queued-reload")
    registerProviders()
    val firstReload = launchFileBlockedReload()
    try {
      projectProvider.values[projectContext] = "project second"
      val secondReload = launchReload()
      awaitEquals("project second") { key[projectContext] }  // the second reload holds the project mutex now

      secondReload.cancelAndJoin()
      assertFalse(dirContext.isReady())  // the first reload still waits for its file bucket

      fileProvider.gate?.complete(Unit)
      firstReload.join()
      assertTrue(dirContext.isReady())
      withTimeout(10.seconds) { reload() }  // the cancelled reload left no mutex held
    }
    finally {
      fileProvider.gate?.complete(Unit)
    }
  }

  /** Starts a reload whose file bucket waits for the closed gate of [fileProvider], and waits until it asks. */
  private suspend fun CoroutineScope.launchFileBlockedReload(): Job {
    fileProvider.closeGate()
    val reloadJob = launchReload()
    fileProvider.awaitRequests(2)
    return reloadJob
  }

  private suspend fun registerProviders() {
    projectContext.awaitReady()
    maskWithGatedProviders(disposable, appProvider, projectProvider, fileProvider)
  }

  /** Returns the context of a new directory [dirName] under the source root. */
  private suspend fun createDirContext(dirName: String): SettingsContext {
    val dir = VfsTestUtil.createDir(sourceRootFixture.get().virtualFile, dirName)
    return readAction { SettingsContext.file(dir, project) }
  }

  private fun CoroutineScope.launchReload(): Job = launch { reload() }

  private suspend fun reload() = SettingsVault.getInstance().reload(app)
}
