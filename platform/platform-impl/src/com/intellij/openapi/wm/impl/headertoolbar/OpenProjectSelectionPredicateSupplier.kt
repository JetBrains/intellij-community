// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.headertoolbar

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.components.service
import org.jetbrains.annotations.ApiStatus
import java.util.function.Predicate

@ApiStatus.Internal
@ApiStatus.Experimental
interface OpenProjectSelectionPredicateSupplier {
  companion object {
    fun getInstance(): OpenProjectSelectionPredicateSupplier = service()
  }

  fun getPredicate(): Predicate<AnAction>
}