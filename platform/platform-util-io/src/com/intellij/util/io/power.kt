// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger

enum class PowerStatus {
  UNKNOWN, AC, BATTERY;

  companion object {
    internal val LOG: Logger = logger<PowerStatus>()

    @JvmStatic
    fun getPowerStatus(): PowerStatus =
      try { IoService.getInstance().powerStatus }
      catch (t: Throwable) {
        LOG.warn(t)
        UNKNOWN
      }
  }
}
