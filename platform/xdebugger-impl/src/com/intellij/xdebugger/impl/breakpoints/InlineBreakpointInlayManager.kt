// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints

import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.DocumentUtil
import com.intellij.util.SlowOperations
import com.intellij.util.application
import com.intellij.util.containers.toMutableSmartList
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import com.intellij.xdebugger.breakpoints.XBreakpointProperties
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.breakpoints.XLineBreakpointType
import com.intellij.xdebugger.impl.XDebuggerUtilImpl
import com.intellij.xdebugger.impl.XSourcePositionImpl
import kotlin.math.max

class InlineBreakpointInlayManager(private val project: Project, private val lineBreakpointManager: XLineBreakpointManager) {

  private val updateQueue = MergingUpdateQueue(
    "inline breakpoint inlay redraw queue",
    300, true, null, project, null, false
  ).setRestartTimerOnAdd(true)

  private fun redraw(file: VirtualFile, document: Document, onlyLine: Int?, onlyEditor: Editor?) {

    if (!PsiDocumentManager.getInstance(project).isCommitted(document)) {
      // FIXME: yes, it happens
      // Just postpone it a little bit.
      updateQueue.queue(Update.create(Pair(document, onlyLine)) {
        application.invokeLater {
          val file = FileDocumentManager.getInstance().getFile(document) ?: return@invokeLater
          redraw(file, document, onlyLine, onlyEditor)
        }
      })
      return
    }

    if (onlyLine != null && !DocumentUtil.isValidLine(onlyLine, document)) return

    fun forEachEditor(action: (Editor) -> Unit) {
      if (onlyEditor != null) {
        action(onlyEditor)
      }
      else {
        EditorFactory.getInstance().getEditors(document, project).forEach(action)
      }
    }

    // remove all previous inlays
    forEachEditor {
      val inlayModel = it.inlayModel
      val startOffset = onlyLine?.let { document.getLineStartOffset(it) } ?: Int.MIN_VALUE
      val endOffset = onlyLine?.let { document.getLineEndOffset(it) } ?: Int.MAX_VALUE
      for (inlay in inlayModel.getInlineElementsInRange(startOffset, endOffset, InlineBreakpointInlayRenderer::class.java)) {
        Disposer.dispose(inlay)
      }
    }

    fun redrawSingleLine(line: Int, breakpoints: List<XLineBreakpointImpl<*>>) {
      if (!DocumentUtil.isValidLine(line, document)) return

      val linePosition = XSourcePositionImpl.create(file, line)
      val breakpointTypes =
        SlowOperations.knownIssue("IDEA-333520, EA-908835").use {
          XBreakpointUtil.getAvailableLineBreakpointTypes(project, linePosition, null)
        }
      XDebuggerUtilImpl.getLineBreakpointVariants(project, breakpointTypes, linePosition).onProcessed { variantsOrNull ->

        if (!PsiDocumentManager.getInstance(project).isCommitted(document)) {
          // FIXME: fight copy paste!
          // FIXME: it doesn't seem to happen, but it's logical?..
          // Just postpone it a little bit.
          updateQueue.queue(Update.create(Pair(document, onlyLine)) {
            application.invokeLater {
              val file = FileDocumentManager.getInstance().getFile(document) ?: return@invokeLater
              redraw(file, document, onlyLine, onlyEditor)
            }
          })
          return@onProcessed
        }

        val variants =
          if (variantsOrNull == null) {
            emptyList()
          }
          else if (variantsOrNull.any(::isAllVariant)) {
            // No need to show "all" variant in case of the inline breakpoints approach, it's useful only for the popup based one.
            variantsOrNull.filter { !isAllVariant(it) }
          }
          else {
            variantsOrNull
          }

        val codeStartOffset = DocumentUtil.getLineStartIndentedOffset(document, line)

        if (breakpoints.size == 1 && variants.size == 1 &&
            areMatching(variants[0], breakpoints[0], codeStartOffset)) {
          // No need to show inline variants when there is only one breakpoint and one matching variant.
          return@onProcessed
        }

        val remainingBreakpoints = breakpoints.toMutableSmartList()
        for (variant in variants) {
          val breakpointsHere = remainingBreakpoints.filter { areMatching(variant, it, codeStartOffset) }
          if (!breakpointsHere.isEmpty()) {
            for (breakpointHere in breakpointsHere) {
              remainingBreakpoints.remove(breakpointHere)
              forEachEditor { addInlineBreakpointInlay(it, breakpointHere, variant, codeStartOffset) }
            }
          }
          else {
            forEachEditor { addInlineBreakpointInlay(it, variant, codeStartOffset) }
          }
        }
        for (remainingBreakpoint in remainingBreakpoints) {
          forEachEditor { addInlineBreakpointInlay(it, remainingBreakpoint, null, codeStartOffset) }
        }
      }
    }

    val allBreakpoints = lineBreakpointManager.getDocumentBreakpoints(document)

    if (onlyLine != null) {
      val breakpoints = allBreakpoints.filter { it.line == onlyLine }
      if (!breakpoints.isEmpty()) {
        redrawSingleLine(onlyLine, breakpoints)
      }
    }
    else {
      for ((line, breakpoints) in allBreakpoints.groupBy { it.line }) {
        if (line >= 0) {
          redrawSingleLine(line, breakpoints)
        }
      }
    }
  }

  fun redrawLine(document: Document, line: Int) {
    require(Registry.`is`("debugger.show.breakpoints.inline"))
    val file = FileDocumentManager.getInstance().getFile(document) ?: return
    redraw(file, document, line, null)
  }

  fun queueRedrawLine(document: Document, line: Int) {
    require(Registry.`is`("debugger.show.breakpoints.inline"))
    updateQueue.queue(Update.create(Pair(document, line)) {
      application.invokeLater {
        redrawLine(document, line)
      }
    })
  }

  fun initializeInNewEditor(editor: Editor) {
    require(Registry.`is`("debugger.show.breakpoints.inline"))

    val document = editor.document
    val file = editor.virtualFile ?: return
    redraw(file, document, null, editor)
  }

  private fun isAllVariant(variant: XLineBreakpointType<*>.XLineBreakpointVariant): Boolean {
    // Currently, it's the easiest way to check that it's really multi-location variant.
    // Don't try to check whether the variant is an instance of XLineBreakpointAllVariant, they all are.
    // FIXME[inline-bp]: introduce better way for this or completely get rid of multi-location variants
    return variant.icon === AllIcons.Debugger.MultipleBreakpoints
  }

  private fun addInlineBreakpointInlay(editor: Editor,
                                       breakpoint: XLineBreakpointImpl<*>,
                                       variant: XLineBreakpointType<*>.XLineBreakpointVariant?,
                                       codeStartOffset: Int) {
    val offset = getBreakpointRangeStartOffset(breakpoint, codeStartOffset)
    addInlineBreakpointInlayImpl(editor, offset, breakpoint, variant)
  }

  private fun addInlineBreakpointInlay(editor: Editor, variant: XLineBreakpointType<*>.XLineBreakpointVariant, codeStartOffset: Int) {
    val offset = getBreakpointVariantRangeStartOffset(variant, codeStartOffset)
    addInlineBreakpointInlayImpl(editor, offset, null, variant)
  }

  private fun addInlineBreakpointInlayImpl(editor: Editor,
                                           offset: Int,
                                           breakpoint: XLineBreakpointImpl<*>?,
                                           variant: XLineBreakpointType<*>.XLineBreakpointVariant?) {
    val inlayModel = editor.inlayModel
    val renderer = InlineBreakpointInlayRenderer(breakpoint, variant)
    val inlay = inlayModel.addInlineElement(offset, renderer)
    inlay?.let { renderer.inlay = it }
  }

  private fun areMatching(variant: XLineBreakpointType<*>.XLineBreakpointVariant, breakpoint: XLineBreakpointImpl<*>, codeStartOffset: Int): Boolean {
    return variant.type == breakpoint.type &&
           getBreakpointVariantRangeStartOffset(variant, codeStartOffset) == getBreakpointRangeStartOffset(breakpoint, codeStartOffset)
  }

  private fun getBreakpointVariantRangeStartOffset(variant: XLineBreakpointType<*>.XLineBreakpointVariant, codeStartOffset: Int): Int {
    val variantRange = variant.highlightRange
    return getLineRangeStartNormalized(variantRange, codeStartOffset)
  }

  @Suppress("UNCHECKED_CAST")
  private fun getBreakpointRangeStartOffset(breakpoint: XLineBreakpointImpl<*>, codeStartOffset: Int): Int {
    val type: XLineBreakpointType<XBreakpointProperties<*>> = breakpoint.type as XLineBreakpointType<XBreakpointProperties<*>>
    val breakpointRange = type.getHighlightRange(breakpoint as XLineBreakpoint<XBreakpointProperties<*>>)
    return getLineRangeStartNormalized(breakpointRange, codeStartOffset)
  }

  private fun getLineRangeStartNormalized(range: TextRange?, codeStartOffset: Int): Int {
    // Null range represents the whole line.
    // Any start offset from the line start until the first non-whitespace character (code start) is normalized
    // to the offset of that non-whitespace character for ease of comparison of various ranges coming from variants and breakpoints.
    return range?.let { max(it.startOffset, codeStartOffset) } ?: codeStartOffset
  }
}
