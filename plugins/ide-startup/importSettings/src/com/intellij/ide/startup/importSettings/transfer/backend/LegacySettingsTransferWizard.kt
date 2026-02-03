// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.transfer.backend

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.ui.EDT

/**
 * This is exclusively for legacy settings transfer wizard to not show any warnings.
 */
object LegacySettingsTransferWizard {

  private val isLegacyThreadingModelStorage = ThreadLocal<Boolean>()
  private val isLegacyThreadingModel: Boolean
    get() = isLegacyThreadingModelStorage.get().let { it != null && it }

  fun <T> withRelaxedThreading(action: () -> T): T {
    assert(!isLegacyThreadingModel)
    isLegacyThreadingModelStorage.set(true)
    try {
      return action()
    }
    finally {
      isLegacyThreadingModelStorage.remove()
    }
  }

  fun warnBackgroundThreadIfNotLegacy() {
    if (isLegacyThreadingModel) return
    if (EDT.isCurrentThreadEdt()) {
      thisLogger().error("Should not be called from EDT.")
    }
  }
}
