// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl.islands

import com.intellij.ide.ui.laf.UiThemeRemapper

internal class IslandsThemeRemapper : UiThemeRemapper {
  override fun mapLaFId(id: String): String? {
    return when (id) {
      "Many Islands Dark", "One Island Dark" -> "Islands Dark"
      "Many Islands Light", "One Island Light" -> "Islands Light"
      else -> null
    }
  }
}