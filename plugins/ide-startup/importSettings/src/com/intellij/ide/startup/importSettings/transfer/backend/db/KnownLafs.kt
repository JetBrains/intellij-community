// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.db

import com.intellij.ide.startup.importSettings.TransferableLafId
import com.intellij.ide.startup.importSettings.models.BundledLookAndFeel
import com.intellij.ide.ui.LafManager

object KnownLafs {
  val Light: BundledLookAndFeel
    get() = _light.value
  private val _light = lazy {
    LafManager.getInstance().defaultLightLaf?.let { BundledLookAndFeel(TransferableLafId.Light, it) } ?: error("Light theme not found")
  }

  val Darcula: BundledLookAndFeel
    get() = _dark.value
  private val _dark = lazy {
    LafManager.getInstance().defaultDarkLaf?.let { BundledLookAndFeel(TransferableLafId.Dark, it) } ?: error("Dark theme not found")
  }

  val HighContrast: BundledLookAndFeel
    get() = _highContrast.value
  private val _highContrast = lazy {
    BundledLookAndFeel.fromManager(TransferableLafId.HighContrast, "High contrast")
  }
}