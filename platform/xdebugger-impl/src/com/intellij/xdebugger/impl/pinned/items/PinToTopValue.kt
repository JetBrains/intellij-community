// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.pinned.items

import com.intellij.xdebugger.frame.XPinToTopData
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CompletableFuture

@ApiStatus.Experimental
interface PinToTopValue {
  val pinToTopDataFuture: CompletableFuture<XPinToTopData>?
    @ApiStatus.Internal
    get() = null

  fun canBePinned() : Boolean {
    return true
  }
}