package com.intellij.xdebugger.impl.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.ex.EditorGutterComponentEx
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.XBreakpointManager

abstract class AllButThisBreakpointAction : AnAction() {
    override fun update(e: AnActionEvent) {
      if (!Registry.`is`("debugger.remove.disable.actions", false)) {
        e.presentation.isEnabledAndVisible = false
        return
      }

        val project = e.project ?: return
        val (currentFile, caretLines) = getCurrentLines(e) ?: run {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val breakpointManager = XDebuggerManager.getInstance(project).breakpointManager
        val breakpoints = breakpointManager.allBreakpoints

        e.presentation.isEnabledAndVisible = breakpoints.any { it.matches(currentFile, caretLines) }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val (currentFile, caretLines) = getCurrentLines(e) ?: return

        val breakpointManager = XDebuggerManager.getInstance(project).breakpointManager
        val breakpoints = breakpointManager.allBreakpoints

        for (breakpoint in breakpoints) {
            if (!breakpoint.matches(currentFile, caretLines)) {
                performAction(breakpointManager, breakpoint)
            }
        }
    }

    protected abstract fun performAction(breakpointManager: XBreakpointManager, breakpoint: XBreakpoint<*>)

    private fun getCurrentLines(e: AnActionEvent): Pair<VirtualFile, List<Int>>? {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return null
        val caretLines = e.getData(EditorGutterComponentEx.LOGICAL_LINE_AT_CURSOR)?.let(::listOf)
            ?: editor.caretModel.allCarets.map { it.logicalPosition.line }
        val currentFile = editor.virtualFile

        if (caretLines.isEmpty()) return null

        return currentFile to caretLines
    }

    private fun XBreakpoint<*>.matches(currentFile: VirtualFile, caretLines: List<Int>): Boolean {
        val sourcePosition = this.sourcePosition
        val breakpointFile = sourcePosition?.file
        val breakpointLine = sourcePosition?.line

        return breakpointFile == currentFile && caretLines.contains(breakpointLine)
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}
