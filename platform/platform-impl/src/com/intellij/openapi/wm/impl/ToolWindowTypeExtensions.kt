// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal

package com.intellij.openapi.wm.impl

import com.intellij.openapi.wm.ToolWindowType
import org.jetbrains.annotations.ApiStatus

val ToolWindowType.isInternal: Boolean
  get() = this == ToolWindowType.SLIDING || this == ToolWindowType.DOCKED
