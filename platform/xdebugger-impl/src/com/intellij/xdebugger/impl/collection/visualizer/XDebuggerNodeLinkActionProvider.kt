// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.collection.visualizer

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.frame.XDebuggerTreeNodeHyperlink
import com.intellij.xdebugger.impl.XDebuggerSuspendScopeProvider
import com.intellij.xdebugger.impl.frame.XDebugView
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

/**
 * Extension point to provide a custom, language-dependent hyperlink for nodes in the Variables view.
 *
 * Note that it will only be invoked if there were no other hyperlinks provided before
 * ([XValueNodeImpl.myFullValueEvaluator] or [XValueNodeImpl.myAdditionalHyperLinks])
 */
@ApiStatus.Experimental
interface XDebuggerNodeLinkActionProvider {
  /**
   * Compute a hyperlink for the given [node].
   *
   * Passed [CoroutineScope] restricts the lifetime of the link action to a current debugger session state.
   * Refer to [XDebuggerSuspendScopeProvider.provideSuspendScope] for more details.
   *
   * @see [XDebuggerNodeLinkActionProvider.computeHyperlink]
   */
  suspend fun CoroutineScope.provideHyperlink(project: Project, node: XValueNodeImpl): XDebuggerTreeNodeHyperlink?

  companion object {
    private val EP_NAME = ExtensionPointName<XDebuggerNodeLinkActionProvider>("com.intellij.xdebugger.nodeLinkActionProvider")

    @JvmStatic
    fun computeHyperlink(project: Project, node: XValueNodeImpl) {
      if (node.hasLinks()) return

      val session = XDebugView.getSession(node.tree) ?: return
      val scope = XDebuggerSuspendScopeProvider.provideSuspendScope(session) ?: return

      scope.launch(Dispatchers.Default) {
        for (provider in EP_NAME.extensionList) {
          val hyperlink = with (provider) {
            scope.provideHyperlink(project, node)
          } ?: continue
          // Double-check to prevent adding more than one link concurrently
          if (!node.hasLinks()) {
            node.addAdditionalHyperlink(hyperlink)
          }
          return@launch
        }
      }
    }
  }
}
