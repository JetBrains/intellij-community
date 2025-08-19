// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend

import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.annotations.ApiStatus
import kotlin.jvm.java

@ApiStatus.Internal
@ApiStatus.Experimental
interface SeTabCustomizer {
  companion object {
    @JvmStatic
    fun getInstance(): SeTabCustomizer {
      return ApplicationManager.getApplication().getService(SeTabCustomizer::class.java)
    }
  }

  fun customizeTabs(tabFactories: List<SeTabFactory>) : List<SeTabFactory>
}