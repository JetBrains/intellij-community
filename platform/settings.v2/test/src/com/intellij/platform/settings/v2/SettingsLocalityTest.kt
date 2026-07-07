// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.openapi.Disposable
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

/**
 * Checks the locality resolution at one context. A [KeyLocality.SHAREABLE] key resolves its local layer over its team
 * layer. A [KeyLocality.ENVIRONMENTAL] key resolves only from the environmental layer. In one locality, the narrower
 * provider still wins. A plain write goes to the local layer by default. A write tries every locality at its context
 * before it goes to a wider context.
 */
@TestApplication
class SettingsLocalityTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  @TestDisposable
  private lateinit var disposable: Disposable

  private val lower = TestLayerProvider("lower")
  private val higher = TestLayerProvider("higher")

  /**
   * Reloads the application and waits until it is ready, before a test masks the providers and seeds layers. So the
   * boot reload of the vault ends over the real providers with no seeds, and no reload sweeps a seeded layer. Without
   * this, the boot reload races the first test that seeds the vault, for example when this class runs alone.
   */
  @BeforeEach
  fun awaitVaultBoot(): Unit = runBlocking {
    val vault = SettingsVault.getInstance()
    vault.reload(SettingsContext.app())
    vault.awaitReady(SettingsContext.app())
  }

  @Test
  fun `a shareable key resolves its local layer over its team layer`(): Unit = runBlocking {
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(higher), disposable)
    val context = SettingsContext.app()

    val shadowed = SettingsKey("test.loc.shadowed", default = "def")
    val teamOnly = SettingsKey("test.loc.teamOnly", default = "def")

    higher.seed(context, shadowed, "team", LayerLocality.TEAM)
    higher.seed(context, shadowed, "local", LayerLocality.LOCAL)
    higher.seed(context, teamOnly, "team", LayerLocality.TEAM)

    awaitEquals("local") { shadowed[context] }  // local shadows team
    awaitEquals("team") { teamOnly[context] }    // no local layer → resolves from team
  }

  @Test
  fun `an environmental key resolves only from the environmental layer`(): Unit = runBlocking {
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(higher), disposable)
    val context = SettingsContext.app()

    val envKey = SettingsKey("test.loc.env", default = "def", locality = KeyLocality.ENVIRONMENTAL)
    val shareKey = SettingsKey("test.loc.share", default = "def")  // SHAREABLE: must ignore the environmental layer
    val fence = SettingsKey("test.loc.fence", default = "def")

    higher.seed(context, envKey, "team", LayerLocality.TEAM)         // ignored by an environmental key
    higher.seed(context, envKey, "env", LayerLocality.ENVIRONMENTAL)
    awaitEquals("env") { envKey[context] }

    higher.seed(context, shareKey, "env", LayerLocality.ENVIRONMENTAL)  // must be ignored by a shareable key
    higher.seed(context, fence, "fenced", LayerLocality.TEAM)            // pushed after, so it folds later: a fence
    awaitEquals("fenced") { fence[context] }
    assertEquals("def", shareKey[context])  // the environmental seed did not reach the shareable key
  }

  @Test
  fun `within one locality the narrower provider wins`(): Unit = runBlocking {
    // Last in the list is the highest-priority (narrowest) layer; locality is equal, so provider order decides.
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(lower, higher), disposable)
    val context = SettingsContext.app()

    val key = SettingsKey("test.loc.providerTiebreak", default = "def")
    lower.seed(context, key, "lower", LayerLocality.LOCAL)
    higher.seed(context, key, "higher", LayerLocality.LOCAL)

    awaitEquals("higher") { key[context] }
  }

  @Test
  fun `a local layer outranks a team layer from a higher-priority provider`(): Unit = runBlocking {
    // higher has the highest priority, because the last provider wins a tie. It holds only a team value, and lower
    // holds a local one. The locality outranks the provider, so the local value of lower wins.
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(lower, higher), disposable)
    val context = SettingsContext.app()

    val key = SettingsKey("test.loc.localOverProvider", default = "def")
    higher.seed(context, key, "teamHigh", LayerLocality.TEAM)
    lower.seed(context, key, "localLow", LayerLocality.LOCAL)

    awaitEquals("localLow") { key[context] }
  }

  @Test
  fun `a plain write defaults to the local layer and shadows a team value`(): Unit = runBlocking {
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(higher), disposable)
    val context = SettingsContext.app()

    val key = SettingsKey("test.loc.writeDefault", default = "def")
    higher.seed(context, key, "team", LayerLocality.TEAM)
    awaitEquals("team") { key[context] }

    key[context] = "written"                 // a plain write lands in the local layer
    assertEquals("written", key[context])    // local shadows the team seed underneath (read-your-writes)
  }

  @Test
  fun `a shareable write falls back to the team layer when no context offers a local layer`(): Unit = runBlocking {
    val teamOnly = TeamOnlyLayerProvider()
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(teamOnly), disposable)
    val context = SettingsContext.app()

    val key = SettingsKey("test.loc.teamFallback", default = "def")
    key[context] = "written"

    assertEquals("written", key[context])
    assertEquals(LayerLocality.TEAM, withTimeout(10.seconds) { teamOnly.savedLocality.await() })
  }

  @Test
  fun `a shareable write lands in a team layer at its context before a local layer at a wider one`(): Unit = runBlocking {
    val provider = LocalAtAppOnlyLayerProvider()
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(provider), disposable)
    val projectContext = SettingsContext.project(projectFixture.get())
    val appContext = SettingsContext.app()

    val key = SettingsKey("test.loc.contextBeforeLocality", default = "def")
    key[projectContext] = "written"

    assertEquals("written", key[projectContext])
    assertEquals("def", key[appContext])  // the local layer of the wider context stays untouched
    assertEquals(projectContext.identity to LayerLocality.TEAM, withTimeout(10.seconds) { provider.firstSave.await() })
  }

  /** Offers a local layer only at the application context, and a team layer only at a narrower one. */
  private class LocalAtAppOnlyLayerProvider : LayerProvider {
    /** Holds the storage context identity and the locality of the first save. */
    val firstSave = CompletableDeferred<Pair<String, LayerLocality>>()

    override fun availableLocalities(context: SettingsContext): Set<LayerLocality> =
      if (context.kind == SettingsContextKind.APPLICATION) setOf(LayerLocality.LOCAL) else setOf(LayerLocality.TEAM)

    override suspend fun writeData(storageContext: SettingsContext, locality: LayerLocality, slices: Map<SettingsContext, SettingsValue.MapValue>): SaveResult {
      firstSave.complete(storageContext.identity to locality)
      return SaveResult.SAVED
    }
  }

  /** Offers only a team layer and reports the locality of its first save. */
  private class TeamOnlyLayerProvider : LayerProvider {
    val savedLocality = CompletableDeferred<LayerLocality>()

    override fun availableLocalities(context: SettingsContext): Set<LayerLocality> = setOf(LayerLocality.TEAM)

    override suspend fun writeData(storageContext: SettingsContext, locality: LayerLocality, slices: Map<SettingsContext, SettingsValue.MapValue>): SaveResult {
      savedLocality.complete(locality)
      return SaveResult.SAVED
    }
  }
}
