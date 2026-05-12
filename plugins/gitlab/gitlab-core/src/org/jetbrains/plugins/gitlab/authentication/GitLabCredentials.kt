// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.authentication

import kotlinx.serialization.Serializable

@Serializable
sealed class GitLabCredentials {
  abstract val accessToken: String

  @Serializable
  class Token(override val accessToken: String) : GitLabCredentials()
}