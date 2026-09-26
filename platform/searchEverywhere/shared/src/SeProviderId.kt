// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

/**
 * Represents a unique identifier for a search provider in the Search Everywhere functionality.
 */
@ApiStatus.Experimental
@Serializable
data class SeProviderId(val value: String)