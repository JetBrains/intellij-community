// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.openapi.Disposable
import com.intellij.platform.settings.v2.SettingsValue.StringValue
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Pins the range of context kinds of a provider (ADR 0159). The vault logs an error for a layer outside the
 * [range][LayerProvider.contextKindRange] of its provider and drops it. This applies to a reload, to the load when a
 * provider registers, and to a push. A provider that declares no range supplies layers at every kind.
 *
 * Each provider in this test returns an application layer and a project layer of one key. The project context
 * resolves through the application context, so a dropped project layer shows the application value.
 */
@TestApplication
class SettingsLayerKindRangeTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)

    private val key = SettingsKey("test.kindRange.key", default = "def")

    private val APPLICATION_ONLY = SettingsContextKindRange(SettingsContextKind.APPLICATION, SettingsContextKind.APPLICATION)

    /** Holds the part of the error message that the vault logs for a layer outside the range of its provider. */
    private const val OUT_OF_RANGE_ERROR = "is outside the range"
  }

  @TestDisposable
  private lateinit var disposable: Disposable

  private val app = SettingsContext.app()
  private val projectContext get() = SettingsContext.project(projectFixture.get())

  @Test
  fun `a reload drops a layer outside the range of its provider and logs an error`(): Unit = runBlocking {
    val provider = registerProvider(APPLICATION_ONLY)
    provider.returnedLayers = listOf(app to "app value", projectContext to "project value")

    val (_, loggedErrors) = collectingErrors { SettingsVault.getInstance().reload(app) }

    assertEquals("app value", key[app])
    assertEquals("app value", key[projectContext])
    assertOutOfRangeErrorLogged(loggedErrors)
  }

  @Test
  fun `a provider that declares no range supplies layers at every kind`(): Unit = runBlocking {
    val provider = registerProvider(SettingsContextKindRange.EVERY_KIND)
    provider.returnedLayers = listOf(app to "app value", projectContext to "project value")

    val (_, loggedErrors) = collectingErrors { SettingsVault.getInstance().reload(app) }

    assertEquals("app value", key[app])
    assertEquals("project value", key[projectContext])
    assertTrue(loggedErrors.none { OUT_OF_RANGE_ERROR in it }, "no layer is outside the range: $loggedErrors")
  }

  @Test
  fun `the load of a registered provider drops a layer outside its range and logs an error`(): Unit = runBlocking {
    val provider = KindRangeLayerProvider(APPLICATION_ONLY)
    provider.returnedLayers = listOf(app to "app value", projectContext to "project value")

    val (_, loggedErrors) = collectingErrors {
      ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(provider), disposable)
      awaitEquals("app value") { key[app] }
    }

    assertEquals("app value", key[projectContext])
    assertOutOfRangeErrorLogged(loggedErrors)
  }

  @Test
  fun `a push outside the range of its provider is dropped and logs an error`(): Unit = runBlocking {
    val provider = registerProvider(APPLICATION_ONLY)

    val (_, loggedErrors) = collectingErrors { SettingsVault.getInstance().push(provider.layer(projectContext, "project value")) }
    SettingsVault.getInstance().push(provider.layer(app, "app value"))
    awaitEquals("app value") { key[app] }

    assertEquals("app value", key[projectContext])  // the vault takes the pushes in order, so the dropped push came first
    assertOutOfRangeErrorLogged(loggedErrors)
  }

  /**
   * Masks the providers with a new [KindRangeLayerProvider] of [contextKindRange], and waits until its load at the
   * registration has read the layers. So the layers that a test sets later reach only the load that the test starts.
   */
  private suspend fun registerProvider(contextKindRange: SettingsContextKindRange): KindRangeLayerProvider {
    val provider = KindRangeLayerProvider(contextKindRange)
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(provider), disposable)
    awaitEquals(true) { provider.requests.get() > 0 }
    return provider
  }

  private fun assertOutOfRangeErrorLogged(loggedErrors: List<String>) {
    assertTrue(loggedErrors.any { OUT_OF_RANGE_ERROR in it && "'${projectContext.identity}'" in it },
               "the layer outside the range is logged: $loggedErrors")
  }

  /**
   * Supplies a layer of [key] at each context of [returnedLayers], and declares [contextKindRange]. It offers no
   * locality, so it takes no write.
   */
  private class KindRangeLayerProvider(override val contextKindRange: SettingsContextKindRange) : LayerProvider {
    /** Holds the context and the value of each layer that [requestData] returns. */
    @Volatile
    var returnedLayers: List<Pair<SettingsContext, String>> = emptyList()

    /** Counts the [requestData] calls that have read [returnedLayers]. */
    val requests: AtomicInteger = AtomicInteger()

    override suspend fun requestData(scopeContext: SettingsContext): List<LayerData> {
      val layers = returnedLayers
        .filter { (context, _) -> scopeContext.isAncestorOf(context, strict = false) }
        .map { (context, value) -> layer(context, value) }
      requests.incrementAndGet()
      return layers
    }

    /** Returns the layer of this provider at [context] that holds [value] for [key]. */
    fun layer(context: SettingsContext, value: String): LayerData =
      LayerData.Builder(context, this, LayerLocality.TEAM).apply { this[key.identity] = StringValue(value) }.build()
  }
}
