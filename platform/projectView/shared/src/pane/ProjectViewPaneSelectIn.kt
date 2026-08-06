// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.pane

import com.intellij.ide.SelectInContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

@ApiStatus.Experimental
sealed interface SelectInRequest

@ApiStatus.Experimental
sealed interface SelectByContext : SelectInRequest {
  val targetId: @NonNls String
  val context: SelectInContext
}

@ApiStatus.Experimental
sealed interface SelectByEditor : SelectInRequest {
  val considerOnlyLastFocusedEditor: Boolean
  val isInvokedManually: Boolean
}
