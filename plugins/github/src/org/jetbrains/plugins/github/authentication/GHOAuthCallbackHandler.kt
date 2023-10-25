// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication

import com.intellij.collaboration.auth.OAuthCallbackHandlerBase
import com.intellij.collaboration.auth.services.OAuthService

internal class GHOAuthCallbackHandler : OAuthCallbackHandlerBase() {
  override fun oauthService(): OAuthService<*> = GHOAuthService.instance

  override fun handleOAuthResult(oAuthResult: OAuthService.OAuthResult<*>): AcceptCodeHandleResult {
    val redirectUrl = if (oAuthResult.isAccepted) {
      GHOAuthService.SERVICE_URL.resolve("complete")
    }
    else {
      GHOAuthService.SERVICE_URL.resolve("error")
    }
    return AcceptCodeHandleResult.Redirect(redirectUrl)
  }
}
