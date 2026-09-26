// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.impl

import com.intellij.openapi.diagnostic.ControlFlowException

class MenuCancelledControlFlowException(cause: Throwable?) : RuntimeException(cause), ControlFlowException {
  constructor() : this(null)
}