// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.base

import com.intellij.openapi.updateSettings.UpdateStrategyCustomization

class ShowWhatIsNewPageAfterUpdateCustomization : UpdateStrategyCustomization() {
  override val showWhatIsNewPageAfterUpdate: Boolean
    get() = true
}