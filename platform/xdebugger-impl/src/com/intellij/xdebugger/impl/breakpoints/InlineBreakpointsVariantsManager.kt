// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints

import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.DocumentUtil
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.containers.toMutableSmartList
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.breakpoints.XBreakpointProperties
import com.intellij.xdebugger.breakpoints.XLineBreakpointType
import com.intellij.xdebugger.impl.XDebuggerUtilImpl
import com.intellij.xdebugger.impl.XSourcePositionImpl
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.concurrency.asDeferred

@ApiStatus.Internal
data class InlineVariantWithMatchingBreakpoint(
  val variant: XLineBreakpointType<*>.XLineBreakpointVariant?,
  val breakpoint: XLineBreakpointImpl<*>?,
) {
  init {
    require(breakpoint != null || variant != null) { "Both breakpoint and variant are null" }
  }
}

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class InlineBreakpointsVariantsManager(private val project: Project) {
  private val calculateVariantsSemaphore = Semaphore(1)

  suspend fun calculateBreakpointsVariants(
    document: Document,
    lines: Set<Int>,
  ): Map<Int, List<InlineVariantWithMatchingBreakpoint>> {
    val variants = calculateBreakpointsVariantsInternal(document, lines)
    if (variants.isEmpty()) return emptyMap()
    val lineToBreakpoints = allBreakpointsIn(document)
      .filter { it.line in variants }
      .groupBy { it.line }
    return readAction {
      variants.mapValues { (line, variants) ->
        val lineBreakpoints = lineToBreakpoints[line] ?: emptyList()
        matchVariantsWithBreakpoints(variants, lineBreakpoints)
      }
    }
  }

  private suspend fun calculateBreakpointsVariantsInternal(document: Document, lines: Set<Int>): Map<Int, List<XLineBreakpointType<*>.XLineBreakpointVariant>> {
    return withSemaphorePermit {
      val lineToVariants = readAction {
        val file = FileDocumentManager.getInstance().getFile(document) ?: return@readAction emptyMap()
        lines.associateWith { calculateVariants(document, file, it) }
      }
      lineToVariants.mapValues { (_, variantsDeferred) ->
        try {
          variantsDeferred.await().filter { it.shouldUseAsInlineVariant() }
        }
        catch (_: IndexNotReadyException) {
          emptyList()
        }
      }
    }
  }

  private fun calculateVariants(document: Document, file: VirtualFile, line: Int): Deferred<List<XLineBreakpointType<*>.XLineBreakpointVariant>> {
    try {
      if (!DocumentUtil.isValidLine(line, document)) return CompletableDeferred(emptyList())

      val linePosition = XSourcePositionImpl.create(file, line)
      val breakpointTypes = XBreakpointUtil.getAvailableLineBreakpointTypes(project, linePosition, null)
      if (breakpointTypes.isEmpty()) return CompletableDeferred(emptyList())
      val variants = XDebuggerUtilImpl.getLineBreakpointVariants(project, breakpointTypes, linePosition)
      return variants.asDeferred()
    }
    catch (_: IndexNotReadyException) {
      return CompletableDeferred(emptyList())
    }
  }

  @RequiresReadLock
  private fun matchVariantsWithBreakpoints(
    variants: List<XLineBreakpointType<*>.XLineBreakpointVariant>,
    breakpoints: List<XLineBreakpointImpl<*>>,
  ): List<InlineVariantWithMatchingBreakpoint> {
    if (!shouldAlwaysShowAllInlays() &&
        breakpoints.size == 1 &&
        (variants.isEmpty() ||
         variants.size == 1 && variants.first().isMatching(breakpoints.first()))) {
      // No need to show inline variants when there is only one breakpoint and one matching variant (or no variants at all).
      return emptyList()
    }

    return buildList {
      val remainingBreakpoints = breakpoints.toMutableSmartList()
      for (variant in variants) {
        val matchingBreakpoints = breakpoints.filter { variant.isMatching(it) }
        if (matchingBreakpoints.isEmpty()) {
          // Easy case: just draw this inlay as a variant.
          add(InlineVariantWithMatchingBreakpoint(variant, breakpoint = null))
        }
        else {
          // We might have multiple breakpoints for a single variant, bad luck. Still draw them.
          // Otherwise, we have a variant and single corresponding breakpoint.
          for (breakpoint in matchingBreakpoints) {
            val notYetMatched = remainingBreakpoints.remove(breakpoint)
            if (notYetMatched) {
              // However, if this breakpoint is the only breakpoint, and all variant highlighters are inside its range, don't draw it.
              val singleLineBreakpoint = breakpoints.size == 1 && breakpointHasTheBiggestRange(breakpoint, variants)

              if (!singleLineBreakpoint || shouldAlwaysShowAllInlays()) {
                add(InlineVariantWithMatchingBreakpoint(variant, breakpoint))
              }
            }
            else {
              // We have multiple variants matching a single breakpoint, bad luck.
              // Don't draw anything new.
            }
          }
        }
      }

      for (breakpoint in remainingBreakpoints) {
        // We have some breakpoints without matched variants.
        // Draw them.
        add(InlineVariantWithMatchingBreakpoint(variant = null, breakpoint))
      }
    }
  }

  private fun breakpointHasTheBiggestRange(breakpoint: XLineBreakpointImpl<*>, variants: List<XLineBreakpointType<*>.XLineBreakpointVariant>): Boolean {
    val range = breakpoint.highlightRange ?: return true

    return variants.all {
      val variantRange = it.highlightRange ?: return@all false
      range.contains(variantRange)
    }
  }

  private fun allBreakpointsIn(document: Document): Collection<XLineBreakpointImpl<*>> {
    val lineBreakpointManager = (XDebuggerManager.getInstance(project).breakpointManager as XBreakpointManagerImpl).lineBreakpointManager
    return lineBreakpointManager.getDocumentBreakpoints(document)
  }

  private suspend fun <T> withSemaphorePermit(action: suspend () -> T): T {
    if (!Registry.`is`(LIMIT_CALCULATE_VARIANTS_JOBS_COUNT_KEY)) {
      return action()
    }
    return calculateVariantsSemaphore.withPermit {
      action()
    }
  }

  private fun shouldAlwaysShowAllInlays() = Registry.`is`(SHOW_EVEN_TRIVIAL_KEY)

  companion object {
    private const val LIMIT_CALCULATE_VARIANTS_JOBS_COUNT_KEY = "debugger.limit.inline.breakpoints.jobs.count"
    internal const val SHOW_EVEN_TRIVIAL_KEY = "debugger.show.breakpoints.inline.even.trivial"

    fun getInstance(project: Project): InlineBreakpointsVariantsManager = project.service()
  }
}

@Suppress("UNCHECKED_CAST") // Casts are required for gods of Kotlin-Java type inference.
private fun XLineBreakpointType<*>.XLineBreakpointVariant.isMatching(breakpoint: XLineBreakpointImpl<*>): Boolean {
  val v = this as XLineBreakpointType<XBreakpointProperties<*>>.XLineBreakpointVariant
  val b = breakpoint as XLineBreakpointImpl<XBreakpointProperties<*>>
  val type: XLineBreakpointType<XBreakpointProperties<*>> = v.type

  return type == b.type && type.variantAndBreakpointMatch(b, v)
}
