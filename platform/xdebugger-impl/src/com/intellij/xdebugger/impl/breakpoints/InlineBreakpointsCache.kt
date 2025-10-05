// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface InlineBreakpointsCache {
  val project: Project

  /**
   * Runs [block] with calculated variants for given [lines] in [document].
   * [block] returns `true` if the breakpoints rendering completed successfully, and returns `false` in case rendering request is canceled,
   * because the document has changed. In this case, the rendering will be repeated later, and this method should not continue computations.
   * N.B. [block] may be called multiple times (e.g., first time with cached variants, second time with calculated variants)
   */
  suspend fun performWithVariants(
    document: Document,
    lines: Set<Int>,
    block: suspend (Map<Int, List<InlineVariantWithMatchingBreakpointProxy>>) -> Boolean,
  )

  fun editorReleased(editor: Editor) {}
}

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
