// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere

import com.intellij.ide.ui.colors.ColorId
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
class SeAttributedString(val text: String, val coloredRanges: List<SeColoredRange>)

@ApiStatus.Internal
@Serializable
data class SeColoredRange(val start: Int, val end: Int, val foregroundColor: ColorId?)
