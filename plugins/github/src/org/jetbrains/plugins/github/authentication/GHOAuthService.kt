// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication

import com.intellij.collaboration.auth.credentials.Credentials
import com.intellij.collaboration.auth.services.OAuthServiceBase
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.util.Url
import com.intellij.util.Urls.newFromEncoded

@Service
internal class GHOAuthService : OAuthServiceBase<Credentials>() {
  override val name: String get() = "github/oauth"

  override fun revokeToken(token: String) {
    TODO("Not yet implemented")
  }

  companion object {
    val instance: GHOAuthService
      get() = service()

    val SERVICE_URL: Url = newFromEncoded("https://account.jetbrains.com/github/oauth/intellij")
  }
}
