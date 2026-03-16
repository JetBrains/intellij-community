// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
enum class ChangesViewDiffAction {
  SINGLE_CLICK_DIFF_PREVIEW,
  PERFORM_DIFF,
}