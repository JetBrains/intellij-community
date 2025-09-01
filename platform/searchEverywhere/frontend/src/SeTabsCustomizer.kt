// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend

import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.annotations.ApiStatus
import kotlin.jvm.java

@ApiStatus.Internal
@ApiStatus.Experimental
interface SeTabsCustomizer {
  companion object {
    @JvmStatic
    fun getInstance(): SeTabsCustomizer {
      return ApplicationManager.getApplication().getService(SeTabsCustomizer::class.java)
    }
  }

  fun customize(tabFactories: List<SeTabFactory>) : List<SeTabFactory>
}