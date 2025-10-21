// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs

import com.intellij.openapi.util.IntellijInternalApi
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

@Serializable
data class VcsKey @ApiStatus.Internal @IntellijInternalApi constructor(val name: @NonNls String) {
  override fun toString(): String = name
}
