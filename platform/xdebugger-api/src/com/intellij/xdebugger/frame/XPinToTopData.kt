// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.frame

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
data class XPinToTopData(
  val canBePinned: Boolean,
  val tag: String?,
  val pinned: Boolean?,
  val customMemberName: String?,
  val customParentTag: String?,
)