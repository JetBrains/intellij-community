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
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins the readiness gate (ADR 0063). While a reload that covers a context runs, that context is **dirty**. A write
 * to it **suspends** until the reload finishes, so it applies on the fresh data. [readWhenReady] suspends until then
 * too. A reload of a file covers the directory store that holds the overrides of the file. A [GatingProvider] holds
 * each reload until the test releases it.
 */
@TestApplication
class SettingsReadinessGateTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
    private val moduleFixture = projectFixture.moduleFixture()
    private val sourceRootFixture = moduleFixture.sourceRootFixture()

    private val key = SettingsKey("test.gate.value", default = "def")
  }

  private val project get() = projectFixture.get()
  private val sourceRoot get() = sourceRootFixture.get().virtualFile

  @TestDisposable
  private lateinit var disposable: Disposable

  /** A provider that holds every load until [gate] is completed, so a reload stays in flight on demand. */
  private class GatingProvider(private val gate: CompletableDeferred<Unit>) : LayerProvider {
    override suspend fun requestData(scopeContext: SettingsContext): List<LayerData> {
      gate.await()
      return emptyList()
    }
  }

  /**
   * Registers a [GatingProvider], so each load of it waits until the test completes the gate.
   *
   * @return the gate that releases the held loads
   */
  private fun startBlockedReload(): CompletableDeferred<Unit> {
    val gate = CompletableDeferred<Unit>()
    LayerProvider.EP_NAME.point.registerExtension(GatingProvider(gate), disposable)
    return gate
  }

  @Test
  fun `a write suspends until the covering reload finishes`(): Unit = runBlocking {
    val appCtx = SettingsContext.app()
    val gate = startBlockedReload()

    val reloadJob = launch { SettingsVault.getInstance().reload(appCtx) }
    awaitEquals(false) { appCtx.isReady() }  // reload in flight → context dirty

    val writeJob = launch { key[appCtx] = "written" }
    delay(200)
    assertTrue(writeJob.isActive)          // gated: the write cannot apply while a covering reload is in flight
    assertEquals("def", key[appCtx])       // so the value is still the default

    gate.complete(Unit)
    writeJob.join()
    reloadJob.join()
    assertEquals("written", key[appCtx])   // the write applied on top of the finished reload
    assertTrue(appCtx.isReady())
  }

  @Test
  fun `readWhenReady suspends until the covering reload finishes`(): Unit = runBlocking {
    val appCtx = SettingsContext.app()
    val gate = startBlockedReload()

    val reloadJob = launch { SettingsVault.getInstance().reload(appCtx) }
    awaitEquals(false) { appCtx.isReady() }

    val readJob = async { appCtx.readWhenReady(key) }
    delay(200)
    assertTrue(readJob.isActive)           // gated: the read waits for readiness

    gate.complete(Unit)
    val value = readJob.await()
    reloadJob.join()
    assertEquals("def", value)             // read ran only after the reload finished (nothing loaded → default)
  }

  @Test
  fun `a reload of a file covers the directory store that holds its overrides`(): Unit = runBlocking {
    val file = VfsTestUtil.createFile(sourceRoot, "gate-file-store/a.txt")
    val fileCtx = readAction { SettingsContext.file(file, project) }
    val dirCtx = readAction { SettingsContext.file(file.parent, project) }
    val gate = startBlockedReload()

    val reloadJob = launch { SettingsVault.getInstance().reload(fileCtx) }
    awaitEquals(false) { dirCtx.isReady() }  // the file layer stores the file's overrides in the directory config

    gate.complete(Unit)
    reloadJob.join()
    assertTrue(dirCtx.isReady())
  }
}
