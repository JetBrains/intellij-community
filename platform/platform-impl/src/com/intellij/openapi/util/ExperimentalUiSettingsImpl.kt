// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util

import com.intellij.ui.ExperimentalUI

class ExperimentalUiSettingsImpl : ExperimentalUiSettings {
  override fun isEnabled(): Boolean {
    return ExperimentalUI.isNewUI()
  }
}