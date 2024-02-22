// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.breakpoints

import com.intellij.openapi.editor.Document
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface InlineBreakpointsDisabler {

  companion object {
    val EP: ExtensionPointName<InlineBreakpointsDisabler> = ExtensionPointName.create(
      "com.intellij.xdebugger.inlineBreakpointsDisabler")
  }

  fun areInlineBreakpointsDisabled(document: Document) : Boolean
}