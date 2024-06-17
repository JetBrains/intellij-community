// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.inspector

import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class RedesignedInspectionsManager {
  companion object {
    @JvmStatic
    fun isAvailable(): Boolean {
      return Registry.`is`("ide.redesigned.inspector", false)
    }
  }
}