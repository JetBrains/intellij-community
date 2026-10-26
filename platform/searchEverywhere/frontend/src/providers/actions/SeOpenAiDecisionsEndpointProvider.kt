// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.providers.actions

import com.intellij.openapi.components.serviceAsync

private const val OPENAI_API_URL: String = "https://api.openai.com/v1"

/**
 * Serves the Decision API ranker straight from the OpenAI API with the personal OpenAI API key of the user.
 *
 * The key lives in [SeOpenAiKeyStore], and the internal action `SeSetOpenAiKeyAction` sets it.
 * The provider serves no endpoint while no key is set, or while the OpenAI API rejects the key.
 * Its registration comes first, thus a key that the user set wins over the endpoint of every plugin.
 */
internal class SeOpenAiDecisionsEndpointProvider : SeDecisionsEndpointProvider {
  override val id: String get() = "OpenAI API key"

  override suspend fun acquire(): SeDecisionsEndpointLease? {
    val keyStore = serviceAsync<SeOpenAiKeyStore>()
    val key = keyStore.getKey() ?: return null
    return openAiKeyLease(key, keyStore)
  }
}

/** Returns the endpoint of the OpenAI API that sends each request with [key]. It reports a refusal to [keyStore]. */
internal fun openAiKeyLease(key: String, keyStore: SeOpenAiKeyStore): SeDecisionsEndpointLease = apiKeyLease(OPENAI_API_URL, key, keyStore)
