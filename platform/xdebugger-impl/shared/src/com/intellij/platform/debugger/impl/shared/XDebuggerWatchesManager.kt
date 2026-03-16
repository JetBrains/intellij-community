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

@ApiStatus.Internal
interface XDebuggerWatchesManager {
  fun getWatchEntries(configurationName: String): List<XWatch>

  fun setWatchEntries(configurationName: String, watchList: List<@JvmSuppressWildcards XWatch>)

  fun getInlineWatches(): List<InlineWatch>

  fun showInplaceEditor(presentationPosition: XSourcePosition, mainEditor: Editor, session: XDebugSessionProxy, expression: XExpression?)

  fun inlineWatchesRemoved(removed: List<InlineWatch>, watchesView: XInlineWatchesView?)

  @RequiresEdt
  fun addInlineWatchExpression(expression: XExpression, index: Int, position: XSourcePosition, navigateToWatchNode: Boolean)

  fun clearContext()
}