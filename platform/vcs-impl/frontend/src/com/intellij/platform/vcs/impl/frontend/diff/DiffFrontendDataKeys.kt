// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.frontend.diff

import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object DiffFrontendDataKeys {
  /**
   * We can't rely neither on instance type nor on file type when interacting with VF on frontend in **split** mode
   */
  val IS_DIFF_SPLIT_VIRTUAL_FILE: Key<Unit> = Key.create("IS_DIFF_SPLIT_VIRTUAL_FILE")
}
