// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.chooser.ui

import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe

object UiUtils {
  const val DEFAULT_BUTTON_WIDTH: Int = 280
  const val DEFAULT_BUTTON_HEIGHT: Int = 40
  val POPUP = Key<Boolean>("ImportSetting_OtherOptions_POPUP")
  val DESCRIPTION = Key<@NlsSafe String>("ImportSetting_ProductDescription")
}