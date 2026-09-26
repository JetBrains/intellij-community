// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.markup.GutterDraggableObject
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.debugger.impl.shared.proxy.XBreakpointManagerProxy
import com.intellij.platform.debugger.impl.shared.proxy.XDebugManagerProxy
import com.intellij.platform.debugger.impl.shared.proxy.XLightLineBreakpointProxy
import com.intellij.platform.debugger.impl.shared.proxy.XLineBreakpointProxy
import com.intellij.util.ThreeState
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.awt.Cursor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DragSource

@ApiStatus.Internal
class BreakpointDraggableObjectFactory(
  private val breakpointManager: XBreakpointManagerProxy,
  private val breakpoint: XLightLineBreakpointProxy,
) {
  fun create(): GutterDraggableObject {
    return object : GutterDraggableObject {
      override fun copy(line: Int, file: VirtualFile?, actionId: Int): Boolean {
        if (file != null && canMoveTo(line, file)) {
          // TODO IJPL-185322 implement DnD for light breakpoints?
          val lineBreakpoint = breakpoint as? XLineBreakpointProxy ?: return false
          if (isCopyAction(actionId)) {
            breakpointManager.copyLineBreakpoint(lineBreakpoint, file, line)
          }
          else {
            val project = lineBreakpoint.project
            val cs = project.service<BreakpointDraggableObjectFactoryScopeProvider>().cs
            cs.launch { // switch to avoid blocking url call on EDT
              lineBreakpoint.setFileUrl(file.url)
              lineBreakpoint.setLine(line)
              val sessionProxy = XDebugManagerProxy.getInstance().getCurrentSessionProxy(project)
              if (sessionProxy != null) {
                breakpointManager.onBreakpointRemoval(lineBreakpoint, sessionProxy)
              }
              DebuggerUIUtil.notifyBreakpointAttachments(lineBreakpoint)
            }
            return true
          }
        }
        return false
      }

      override fun remove() {
        // TODO IJPL-185322 implement DnD remove for light breakpoints?
        if (breakpoint is XLineBreakpointProxy) {
          XBreakpointUIUtil.removeBreakpointWithConfirmation(breakpoint)
        }
      }

      override fun getCursor(line: Int, file: VirtualFile?, actionId: Int): Cursor? {
        if (canMoveTo(line, file)) {
          return if (isCopyAction(actionId)) DragSource.DefaultCopyDrop else DragSource.DefaultMoveDrop
        }

        return DragSource.DefaultMoveNoDrop
      }

      private fun canMoveTo(line: Int, file: VirtualFile?): Boolean {
        if (file != null && breakpoint.type.canPutAtFast(file, line, breakpoint.project) == ThreeState.YES) {
          val existing = breakpointManager.findBreakpointAtLine(breakpoint.type, file, line, breakpoint.getPlacement())
          return existing == null || existing == breakpoint
        }
        return false
      }

      private fun isCopyAction(actionId: Int): Boolean {
        return (actionId and DnDConstants.ACTION_COPY) == DnDConstants.ACTION_COPY
      }
    }
  }
}

@Service(Service.Level.PROJECT)
private class BreakpointDraggableObjectFactoryScopeProvider(val cs: CoroutineScope)
