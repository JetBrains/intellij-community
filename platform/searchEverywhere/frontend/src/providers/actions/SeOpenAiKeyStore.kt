// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.providers.actions

import com.intellij.credentialStore.CredentialStore
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.components.Service

/**
 * Keeps the personal OpenAI API key of the Decision API ranker. See [SeApiKeyStore].
 *
 * [SeOpenAiDecisionsEndpointProvider] reads the key, and the internal action `SeSetOpenAiKeyAction` sets it.
 */
@Service(Service.Level.APP)
internal class SeOpenAiKeyStore internal constructor(credentialStore: () -> CredentialStore) :
  SeApiKeyStore(SeApiKeyKind.OPENAI, credentialStore) {
  @Suppress("unused") // the service container instantiates this one
  constructor() : this({ PasswordSafe.instance })
}
