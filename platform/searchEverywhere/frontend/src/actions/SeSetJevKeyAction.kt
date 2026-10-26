// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.actions

import com.intellij.openapi.components.serviceAsync
import com.intellij.platform.searchEverywhere.frontend.providers.actions.SeApiKeyKind
import com.intellij.platform.searchEverywhere.frontend.providers.actions.SeJevKeyStore

/**
 * Sets or removes the personal Jev API key of the Jev ranker of Search Everywhere: a TypeSafe key or an OpenRouter key.
 * See [SeJevKeyStore].
 */
internal class SeSetJevKeyAction : SeSetApiKeyAction(SeApiKeyKind.JEV, { serviceAsync<SeJevKeyStore>() })
