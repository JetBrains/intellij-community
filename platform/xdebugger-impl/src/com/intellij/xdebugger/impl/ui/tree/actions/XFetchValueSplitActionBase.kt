// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.tree.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehavior
import com.intellij.platform.debugger.impl.shared.SplitDebuggerAction
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueContainerNode
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl
import org.jetbrains.annotations.ApiStatus

/**
 * Base class for actions that operate on frontend [XValueNodeImpl] tree nodes.
 *
 * Use this class if the logic of the action is split and needs to operate on the frontend.
 *
 * [XFetchValueSplitActionBase.getNodes] returns frontend node instances ([XValueNodeImpl]) which are only available on the frontend.
 * Moreover, for nodes used by this action [XValueContainerNode.getValueContainer] returns frontend-specific [XValue]s,
 * which cannot be cast to an [XValue] obtained from plugin-specific [XDebugProcess].
 *
 * For backend only Monolith actions use [XFetchValueActionBase] instead, which works with backend [XValue] instances.
 */
@ApiStatus.Experimental
abstract class XFetchValueSplitActionBase : XFetchValueActionBase() {
  internal final override fun getNodes(e: AnActionEvent): List<XValueNodeImpl> {
    return XDebuggerTreeSplitActionBase.getSelectedNodes(e.dataContext)
  }

  @ApiStatus.Internal
  override fun getBehavior(): ActionRemoteBehavior {
    return SplitDebuggerAction.getSplitBehavior()
  }
}
