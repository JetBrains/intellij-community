// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.openapi.Disposable
import com.intellij.platform.settings.v2.SettingsValue.StringValue
import com.intellij.testFramework.ExtensionTestUtil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

/** Holds the range of context kinds of an application provider. */
internal val APPLICATION_KINDS: SettingsContextKindRange = SettingsContextKindRange(SettingsContextKind.APPLICATION, SettingsContextKind.APPLICATION)

/** Holds the range of context kinds of a project provider. */
internal val PROJECT_KINDS: SettingsContextKindRange = SettingsContextKindRange(SettingsContextKind.PROJECT, SettingsContextKind.PROJECT)

/** Holds the range of context kinds of a file provider, as the file layer declares it. */
internal val FILE_KINDS: SettingsContextKindRange = SettingsContextKindRange(SettingsContextKind.FILE, narrowestKind = null)

/** Holds the range of context kinds of a provider that supplies the project and the file layers. */
internal val PROJECT_AND_FILE_KINDS: SettingsContextKindRange = SettingsContextKindRange(SettingsContextKind.PROJECT, narrowestKind = null)

/**
 * Masks the providers with [providers] until [disposable] is disposed. Then waits until the load of each provider at
 * its registration has read its values. So the values that a test sets later reach only the reloads that it starts.
 */
internal suspend fun maskWithGatedProviders(disposable: Disposable, vararg providers: GatedKindLayerProvider) {
  ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, providers.toList(), disposable)
  for (provider in providers) provider.awaitRequests(1)
}

/**
 * Supplies a layer of [key] at each context of [values], and declares [contextKindRange]. A test holds its loads with
 * [gate], so a reload waits for this provider on demand. It takes a write in the [LayerLocality.LOCAL] layer at every
 * kind of its range. It persists nothing, so the vault model stays the truth.
 *
 * @param key the key of every layer that this provider supplies
 */
internal class GatedKindLayerProvider(
  override val contextKindRange: SettingsContextKindRange,
  private val key: SettingsKey<String>,
) : LayerProvider {
  /** Holds the value of [key] at each context that this provider supplies a layer at. A load reads it before [gate]. */
  val values: ConcurrentHashMap<SettingsContext, String> = ConcurrentHashMap()

  /**
   * Holds the gate that each load waits for after it reads [values], or `null` for a load that answers at once. A load
   * reads this gate before it counts in [requests]. So a test can replace the gate after [awaitRequests], and the held
   * load still waits for the old gate.
   */
  @Volatile
  var gate: CompletableDeferred<Unit>? = null

  /**
   * Holds the exception that a load throws after [gate], or `null` for a load that returns its layers. A load reads it
   * when its gate opens, so a test can set it while the load waits.
   */
  @Volatile
  var failure: RuntimeException? = null

  /** Counts the [requestData] calls that have read [values]. A held load counts before it waits for [gate]. */
  val requests: MutableStateFlow<Int> = MutableStateFlow(0)

  /** Runs when a [requestData] call starts, so a test can see the model at the moment the vault asks this provider. */
  @Volatile
  var onRequest: (() -> Unit)? = null

  override fun availableLocalities(context: SettingsContext): Set<LayerLocality> =
    if (context.kind in contextKindRange) setOf(LayerLocality.LOCAL) else emptySet()

  override suspend fun requestData(scopeContext: SettingsContext): List<LayerData> {
    onRequest?.invoke()
    val layers = values.entries
      .filter { (context, _) -> scopeContext.isAncestorOf(context, strict = false) }
      .map { (context, value) -> layer(context, value) }
    val loadGate = gate
    requests.update { it + 1 }
    loadGate?.await()
    failure?.let { throw it }
    return layers
  }

  /** Returns the layer of this provider at [context] that holds [value] for [key]. A test can push it. */
  fun layer(context: SettingsContext, value: String): LayerData =
    LayerData.Builder(context, this, LayerLocality.LOCAL).apply { this[key.identity] = StringValue(value) }.build()

  /** Suspends until [requests] reaches [count]. Fails after a timeout, so a reload that never asks reports. */
  suspend fun awaitRequests(count: Int) {
    withTimeout(10.seconds) { requests.first { it >= count } }
  }

  /** Holds [gate] closed from now on, and returns it. */
  fun closeGate(): CompletableDeferred<Unit> = CompletableDeferred<Unit>().also { gate = it }
}
