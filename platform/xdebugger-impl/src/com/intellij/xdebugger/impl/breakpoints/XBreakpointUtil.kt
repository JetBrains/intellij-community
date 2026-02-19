// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.component1
import com.intellij.openapi.util.component2
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.debugger.impl.ui.XDebuggerEntityConverter
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.XBreakpointProperties
import com.intellij.xdebugger.breakpoints.XBreakpointType
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.breakpoints.XLineBreakpointType
import one.util.streamex.StreamEx
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.asPromise

object XBreakpointUtil {
  /**
   * The forcibly shortened version of [XBreakpointType.getShortText].
   */
  @JvmStatic
  fun getShortText(breakpoint: XBreakpoint<*>): @Nls String {
    val len = 70
    return StringUtil.shortenTextWithEllipsis(breakpoint.shortText, len, len / 2)
  }

  /**
   * @see XBreakpointType.getDisplayText
   */
  @JvmStatic
  fun getDisplayText(breakpoint: XBreakpoint<*>): @Nls String =
    breakpoint.displayText

  /**
   * @see XBreakpointType.getGeneralDescription
   */
  @JvmStatic
  fun getGeneralDescription(breakpoint: XBreakpoint<*>): @Nls String =
    breakpoint.generalDescription

  /**
   * @see XBreakpointType.getPropertyXMLDescriptions
   */
  @JvmStatic
  fun getPropertyXMLDescriptions(breakpoint: XBreakpoint<*>): List<String> =
    breakpoint.propertyXMLDescriptions

  @JvmStatic
  fun findType(id: @NonNls String): XBreakpointType<*, *>? =
    breakpointTypes().find { it.id == id }

  @ApiStatus.Internal
  @JvmStatic
  fun breakpointTypes(): StreamEx<XBreakpointType<*, *>> =
    StreamEx.of(XBreakpointType.EXTENSION_POINT_NAME.extensionList)

  @ApiStatus.Obsolete
  @JvmStatic
  fun findSelectedBreakpoint(project: Project, editor: Editor): Pair<GutterIconRenderer?, XBreakpoint<*>?> {
    val pair = XBreakpointUIUtil.findSelectedBreakpointProxy(project, editor)
    val (renderer, breakpoint) = pair
    if (breakpoint == null) return Pair.create(null, null)
    val monolithBreakpoint = XDebuggerEntityConverter.getBreakpoint(breakpoint.id)
    if (monolithBreakpoint != null) {
      return Pair.create(renderer, monolithBreakpoint)
    }
    return Pair.create(null, null)
  }

  /**
   * Toggle line breakpoint with editor support:
   * - unfolds folded block on the line
   * - if folded, checks if line breakpoints could be toggled inside folded text
   *
   */
  @Deprecated("use {@link #toggleLineBreakpoint(Project, XSourcePosition, boolean, Editor, boolean, boolean, boolean)}")
  @JvmStatic
  fun toggleLineBreakpoint(
    project: Project,
    position: XSourcePosition,
    editor: Editor,
    temporary: Boolean,
    moveCaret: Boolean,
    canRemove: Boolean,
  ): Promise<XLineBreakpoint<*>?> =
    toggleLineBreakpoint(project, position, true, editor, temporary, moveCaret, canRemove)

  /**
   * Toggle line breakpoint with editor support:
   * - unfolds folded block on the line
   * - if folded, checks if line breakpoints could be toggled inside folded text
   */
  @JvmStatic
  fun toggleLineBreakpoint(
    project: Project,
    position: XSourcePosition,
    selectVariantByPositionColumn: Boolean,
    editor: Editor,
    temporary: Boolean,
    moveCaret: Boolean,
    canRemove: Boolean,
  ): Promise<XLineBreakpoint<*>?> {
    return XBreakpointUIUtil.toggleLineBreakpointProxy(project,
                                                       position,
                                                       selectVariantByPositionColumn,
                                                       editor,
                                                       temporary,
                                                       moveCaret,
                                                       canRemove).asPromise()
      .then { proxy ->
        if (proxy == null) return@then null
        val monolithBreakpoint = XDebuggerEntityConverter.getBreakpoint(proxy.id)
        monolithBreakpoint as? XLineBreakpoint<*>
      }
  }

  @ApiStatus.Internal
  fun getAvailableLineBreakpointTypes(
    project: Project,
    position: XSourcePosition,
    editor: Editor? = null,
    selectTypeByPositionColumn: Boolean = false,
  ): List<XLineBreakpointType<*>> {
    val breakpointManager = XDebuggerManager.getInstance(project).breakpointManager
    val breakpointInfo = XBreakpointUIUtil.getAvailableLineBreakpointInfo(position, selectTypeByPositionColumn, editor,
                                                                          XDebuggerUtil.getInstance().lineBreakpointTypes.toList(),
                                                                          { type, line -> breakpointManager.findBreakpointAtLine(type, position.file, line) },
                                                                          { type -> type.priority },
                                                                          { callback -> callback() },
                                                                          { type, line -> type.canPutAt(position.file, line, project) }
    )
    return breakpointInfo.first
  }
}

val XBreakpoint<*>.shortText: @Nls String
  get() {
    @Suppress("UNCHECKED_CAST") val t = type as XBreakpointType<XBreakpoint<*>, *>
    return t.getShortText(this)
  }

val XBreakpoint<*>.displayText: @Nls String
  get() {
    @Suppress("UNCHECKED_CAST") val t = type as XBreakpointType<XBreakpoint<*>, *>
    return t.getDisplayText(this)
  }

val XBreakpoint<*>.generalDescription: @Nls String
  get() {
    @Suppress("UNCHECKED_CAST") val t = type as XBreakpointType<XBreakpoint<*>, *>
    return t.getGeneralDescription(this)
  }

val XBreakpoint<*>.propertyXMLDescriptions: List<@Nls String>
  get() {
    @Suppress("UNCHECKED_CAST") val t = type as XBreakpointType<XBreakpoint<*>, *>
    return t.getPropertyXMLDescriptions(this)
  }

val <P : XBreakpointProperties<*>> XLineBreakpoint<P>.highlightRange: TextRange?
  get() =
    type.getHighlightRange(this)
