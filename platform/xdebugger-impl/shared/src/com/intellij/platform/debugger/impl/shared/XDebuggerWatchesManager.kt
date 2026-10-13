// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.shared

import com.intellij.openapi.editor.Editor
import com.intellij.platform.debugger.impl.shared.proxy.XDebugSessionProxy
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.xdebugger.XExpression
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.impl.XWatch
import com.intellij.xdebugger.impl.inline.InlineWatch
import com.intellij.xdebugger.impl.inline.XInlineWatchesView
import org.jetbrains.annotations.ApiStatus

/**
 * Stores and coordinates debugger watches shared between debugger UI implementations.
 *
 * Regular watches are grouped by debug configuration name. Inline watches are stored independently of a session and are propagated to
 * all currently open watches views for the project.
 */
@ApiStatus.Internal
interface XDebuggerWatchesManager {
  /**
   * Returns regular watches stored for [configurationName], or an empty list when none are stored.
   *
   * The configuration name is normally the debug configuration type ID and falls back to the session name when no run configuration is
   * available.
   */
  fun getWatchEntries(configurationName: String): List<XWatch>

  /**
   * Replaces regular watches stored for [configurationName]. Passing an empty [watchList] clears watches for that configuration.
   */
  fun setWatchEntries(configurationName: String, watchList: List<@JvmSuppressWildcards XWatch>)

  /**
   * Returns all inline watches currently tracked for the project.
   */
  fun getInlineWatches(): List<InlineWatch>

  /**
   * Shows an editor in [mainEditor] for creating or editing an inline watch at [presentationPosition].
   *
   * [session] provides the debugger context and editor provider. [expression], when provided, is used as the initial editor value.
   */
  fun showInplaceEditor(presentationPosition: XSourcePosition, mainEditor: Editor, session: XDebugSessionProxy, expression: XExpression?)

  /**
   * Removes [removed] inline watches from storage and notifies all watches views except [watchesView].
   *
   * Pass the originating [watchesView] to avoid sending the same update back to the view that already performed it.
   */
  fun inlineWatchesRemoved(removed: List<InlineWatch>, watchesView: XInlineWatchesView?)

  /**
   * Adds an inline watch with [expression] at [position] and inserts it into all open watches views.
   *
   * [index] is the insertion index used by the views; `-1` appends the watch. When [navigateToWatchNode] is `true`, the receiving
   * view should reveal the created node.
   */
  @RequiresEdt
  fun addInlineWatchExpression(expression: XExpression, index: Int, position: XSourcePosition, navigateToWatchNode: Boolean)

  /**
   * Clears all stored regular and inline watches, for example before loading persisted state.
   */
  fun clearContext()
}