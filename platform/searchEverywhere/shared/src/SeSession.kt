// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

/**
 * Search Everywhere session.
 * The life duration is bound to the Search Everywhere popup window.
 */
@ApiStatus.Experimental
@Serializable
sealed interface SeSession {
  @get:ApiStatus.Internal
  val id: String
}

/**
 * The frontend makes the session with a random [id].
 * The backend opens its part of the session on demand. See [com.intellij.platform.searchEverywhere.impl.SeRemoteApi.closeSession].
 *
 * The frontend and the backend use the session as a map key, so two copies with the same [id] are equal.
 */
@ApiStatus.Internal
@Serializable
data class SeSessionImpl(override val id: String) : SeSession
