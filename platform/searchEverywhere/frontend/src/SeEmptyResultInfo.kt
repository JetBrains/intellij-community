// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend

import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.StatusText
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.event.ActionListener

@ApiStatus.Experimental
class SeEmptyResultInfo(val chunks: List<SeEmptyResultInfoChunk>)

@ApiStatus.Experimental
class SeEmptyResultInfoChunk(
  val text: @Nls String,
  val onNewLine: Boolean = false,
  val attrs: SimpleTextAttributes = StatusText.DEFAULT_ATTRIBUTES,
  val listener: ActionListener? = null,
)