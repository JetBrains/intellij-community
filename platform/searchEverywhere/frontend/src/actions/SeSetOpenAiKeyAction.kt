// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.actions

import com.intellij.openapi.components.serviceAsync
import com.intellij.platform.searchEverywhere.frontend.providers.actions.SeApiKeyKind
import com.intellij.platform.searchEverywhere.frontend.providers.actions.SeOpenAiKeyStore

/**
 * Sets or removes the personal OpenAI API key of the Decision API ranker of Search Everywhere. See [SeOpenAiKeyStore].
 */
internal class SeSetOpenAiKeyAction : SeSetApiKeyAction(SeApiKeyKind.OPENAI, { serviceAsync<SeOpenAiKeyStore>() })
