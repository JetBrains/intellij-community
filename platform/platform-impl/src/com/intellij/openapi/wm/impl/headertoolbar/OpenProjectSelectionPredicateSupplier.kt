// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.headertoolbar

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.annotations.ApiStatus
import java.util.function.Predicate

@ApiStatus.Internal
@ApiStatus.Experimental
interface OpenProjectSelectionPredicateSupplier {

  companion object {
    @JvmStatic fun getInstance(): OpenProjectSelectionPredicateSupplier =
      ApplicationManager.getApplication().getService(OpenProjectSelectionPredicateSupplier::class.java)
  }

  fun getPredicate(): Predicate<AnAction>
}