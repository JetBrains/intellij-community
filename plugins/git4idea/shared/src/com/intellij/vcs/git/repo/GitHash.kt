// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.repo

import com.intellij.openapi.util.NlsSafe
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@JvmInline
@ApiStatus.Internal
@Serializable
value class GitHash(val hash: @NlsSafe String) {
  fun toShortString(): @NlsSafe String = hash.take(8)
}