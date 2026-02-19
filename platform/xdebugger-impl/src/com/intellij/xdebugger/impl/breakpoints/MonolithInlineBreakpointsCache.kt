// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints

import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.platform.debugger.impl.shared.InlineBreakpointsCache
import com.intellij.platform.debugger.impl.shared.proxy.InlineVariantWithMatchingBreakpointProxy
import com.intellij.xdebugger.impl.proxy.asProxy

internal class MonolithInlineBreakpointsCache(override val project: Project) : InlineBreakpointsCache {
  override suspend fun performWithVariants(document: Document, lines: Set<Int>, block: suspend (Map<Int, List<InlineVariantWithMatchingBreakpointProxy>>) -> Boolean) { // calculate variants without caching
    val variants = InlineBreakpointsVariantsManager.getInstance(project).calculateBreakpointsVariants(document, lines).mapValues { (_, variants) ->
        variants.map { (variant, breakpoint) ->
          val inlineBreakpoint = breakpoint?.asProxy()?.asInlineLightBreakpoint()
          InlineVariantWithMatchingBreakpointProxy(variant?.asProxy(), inlineBreakpoint)
        }
      }
    block(variants)
  }
}
