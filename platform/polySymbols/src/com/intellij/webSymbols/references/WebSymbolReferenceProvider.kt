// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.references

import com.intellij.psi.PsiElement
import com.intellij.psi.util.startOffsetInAncestor
import org.jetbrains.annotations.ApiStatus

@ApiStatus.ScheduledForRemoval
@ApiStatus.Experimental
@Deprecated("Left only to provide compatibility for PsiElement.startOffsetIn utility method")
class WebSymbolReferenceProvider {

  companion object {
    @JvmStatic
    @ApiStatus.ScheduledForRemoval
    @ApiStatus.Experimental
    @Deprecated(message = "Use com.intellij.psi.util.startOffsetInAncestor() instead",
                replaceWith = ReplaceWith("startOffsetInAncestor(parent)"))
    fun PsiElement.startOffsetIn(parent: PsiElement): Int =
      startOffsetInAncestor(parent)
  }
}