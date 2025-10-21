// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@Serializable
@ApiStatus.Internal
data class SeTextSearchOptions(val isCaseSensitive: Boolean, val isWholeWordsOnly: Boolean, val isRegex: Boolean)