// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.authentication

import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Safety margin, in seconds, subtracted from the access token expiration time so that a token that is about
 * to expire is refreshed proactively instead of being used for an operation that may outlive it.
 */
private const val ACCESS_TOKEN_EXPIRY_MARGIN_SECONDS = 60

@Serializable
sealed class GitLabCredentials {
  abstract val accessToken: String

  @Serializable
  class OAuth(
    override val accessToken: String,
    val refreshToken: String,
    val clientId: String,
    val expiresIn: Int,
    private val createdAt: Long,
  ) :
    GitLabCredentials() {
    fun isAccessTokenValid(): Boolean =
      Clock.System.now() < Instant.fromEpochSeconds(createdAt + expiresIn - ACCESS_TOKEN_EXPIRY_MARGIN_SECONDS)

    companion object {
      fun fromDTO(dto: GitLabOAuthResponseDTO, clientId: String): OAuth = with(dto) {
        OAuth(accessToken, refreshToken, clientId, expiresIn, createdAt)
      }
    }
  }

  @Serializable
  class Token(override val accessToken: String) : GitLabCredentials()
}