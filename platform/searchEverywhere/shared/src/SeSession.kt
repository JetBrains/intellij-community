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
sealed interface SeSession

/**
 * The frontend creates the session with a random [id].
 * The frontend and the backend use the session as a map key, so two copies with the same [id] are equal.
 */
@ApiStatus.Internal
@Serializable
data class SeSessionImpl(val id: String) : SeSession

@get:ApiStatus.Internal
val SeSession.id: String get() = (this as SeSessionImpl).id
