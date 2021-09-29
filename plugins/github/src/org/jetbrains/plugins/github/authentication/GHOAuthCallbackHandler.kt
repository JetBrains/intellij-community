// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication

import com.intellij.collaboration.auth.OAuthCallbackHandlerBase
import com.intellij.collaboration.auth.services.OAuthService

internal class GHOAuthCallbackHandler : OAuthCallbackHandlerBase() {
  override fun oauthService(): OAuthService<*> = GHOAuthService.instance
}
