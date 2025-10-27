// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.ex

import com.intellij.codeWithMe.ClientId
import org.jetbrains.annotations.ApiStatus

/**
 * Minimal marker interface for ranges that carry client IDs information.
 */
@ApiStatus.Experimental
interface LstLocalRange {
  val clientIds: List<ClientId>
}
