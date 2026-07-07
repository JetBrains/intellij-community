// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.openapi.Disposable
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Pins the broadcast of a slot operation across providers, and its match of the context (ADR 0078). Two
 * [TestLayerProvider]s stack several layers at one context.
 * - **Across providers**: a slot that two providers hold is rewritten in both, not only in the winning one.
 * - **No climbing**: an operation at a context that does not hold the slot does **not** climb to an ancestor that
 *   holds it.
 *
 * [SettingsSlotBroadcastTest] pins the broadcast across the locality files on disk.
 */
@TestApplication
class SettingsSlotBroadcastAcrossLayersTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)

    private val current = SettingsKey("test.bcast2.current", default = "dev")
    private val data = SettingsMapKey("test.bcast2.data", discriminatorKey = current)
    private val style = SettingsKey("test.bcast2.style", default = "none", storageKey = data)
  }

  @TestDisposable
  private lateinit var disposable: Disposable

  private val project get() = projectFixture.get()
  private val lower = TestLayerProvider("lower")
  private val higher = TestLayerProvider("higher")

  // Drain the one-time boot reload before a test masks the providers and seeds, so the reload cannot sweep a new seed.
  // SettingsLocalityTest does the same.
  @BeforeEach
  fun awaitVaultBoot(): Unit = runBlocking {
    val vault = SettingsVault.getInstance()
    vault.reload(SettingsContext.app())
    vault.awaitReady(SettingsContext.app())
  }

  @Test
  fun `a slot op broadcasts across providers`(): Unit = runBlocking {
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(lower, higher), disposable)
    val context = SettingsContext.app()
    val dev = context.access { this[current] = "dev" }

    // The same slot lives in two providers: the lower one's team layer and the higher one's local layer.
    lower.seed(dev, style, "fromLower", LayerLocality.TEAM)
    higher.seed(dev, style, "fromHigher", LayerLocality.LOCAL)
    awaitEquals("fromHigher") { style[dev] }  // local (higher) shadows team (lower)

    data.renameSlot(context, "dev", "dev2")

    val dev2 = context.access { this[current] = "dev2" }
    assertEquals("fromHigher", style[dev2])   // the slot moved in the higher/local layer
    // The dev slot is now empty in BOTH providers. If only the higher one had moved, the team value of the lower one
    // would show here.
    assertEquals("none", style[dev])
  }

  @Test
  fun `a slot op does not climb to an ancestor context`(): Unit = runBlocking {
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(higher), disposable)
    val appCtx = SettingsContext.app()
    val projectCtx = SettingsContext.project(project)

    // The slot lives only at the application context.
    higher.seed(appCtx.access { this[current] = "dev" }, style, "appVal", LayerLocality.LOCAL)
    awaitEquals("appVal") { style[appCtx.access { this[current] = "dev" }] }

    // A rename at the project context finds no layer holding the slot there; it must be a no-op, not climb to the app.
    data.renameSlot(projectCtx, "dev", "dev2")

    assertEquals("appVal", style[appCtx.access { this[current] = "dev" }])   // the app slot is untouched
    assertEquals("none", style[appCtx.access { this[current] = "dev2" }])    // and nothing was created
  }
}
