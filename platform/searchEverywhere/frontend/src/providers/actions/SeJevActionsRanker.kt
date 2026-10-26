// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.providers.actions

import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.util.registry.RegistryManager

private const val MODEL_REGISTRY_KEY: String = "search.everywhere.actions.ranker.jev.model"

private const val OPENROUTER_KEY_PREFIX: String = "sk-or-"

/**
 * Orders the actions with a Jev model of TypeSafe and the personal Jev API key of the user.
 *
 * The key lives in [SeJevKeyStore], and the internal action `SeSetJevKeyAction` sets it. The ranker cannot rank while no key is set.
 * The key selects the service, see [SeJevRoute]. The session works as the Decision API session does, see [SeDecisionsActionsRankerSession].
 *
 * The `search.everywhere.actions.ranker.jev.model` registry key names the model. An empty value selects the default model of the route.
 * To select this ranker, set the `search.everywhere.actions.ranker` registry key to `jev`.
 */
internal class SeJevActionsRanker : SeActionsRanker {
  override val id: String get() = "jev"
  override val diagnosticName: String get() = "Jev"

  override suspend fun openSession(): SeActionsRankerSession? {
    val model = RegistryManager.getInstanceAsync().stringValue(MODEL_REGISTRY_KEY)?.trim()?.ifEmpty { null }
    val service = serviceAsync<SeDecisionsActionsRankerService>()
    val keyStore = serviceAsync<SeJevKeyStore>()
    val key = keyStore.getKey() ?: return null

    val route = SeJevRoute.forKey(key)
    val lease = apiKeyLease(route.baseUrl, key, keyStore)
    return SeDecisionsActionsRankerSession(lease, model ?: route.defaultModel, service.client, service.scope, route.api)
  }
}

/** The service that serves a Jev model for a personal key, with its API base, its protocol, and its default model. */
internal enum class SeJevRoute(val baseUrl: String, val api: SeDecisionsApi, val defaultModel: String) {
  /** The System One API of TypeSafe, for a TypeSafe key. */
  TYPESAFE("https://api.typesafe.ai/v1", SeDecisionsApi.TYPESAFE, "jev-latest"),

  /** The Decision API of OpenRouter, for an OpenRouter key. */
  OPENROUTER("https://openrouter.ai/api/alpha", SeDecisionsApi.OPENROUTER, "~typesafe/jev-latest");

  companion object {
    /** Returns the route of [key]. An OpenRouter key starts with `sk-or-`, and every other key is a TypeSafe key. */
    fun forKey(key: String): SeJevRoute = if (key.startsWith(OPENROUTER_KEY_PREFIX)) OPENROUTER else TYPESAFE
  }
}
