// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.transfer.backend.providers.vswin.mappings

import com.intellij.ide.startup.importSettings.models.FeatureInfo
import com.intellij.ide.startup.importSettings.transfer.backend.providers.vswin.KnownPlugins

object VisualStudioPluginsMapping {

  const val RESHARPER = "JetBrains s.r.o.|ReSharper"

  fun get(id: String): FeatureInfo? {
    return when (id) {
      RESHARPER -> KnownPlugins.ReSharper
      else -> null
    }
  }
}
