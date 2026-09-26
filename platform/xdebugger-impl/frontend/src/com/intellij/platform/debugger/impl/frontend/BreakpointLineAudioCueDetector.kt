// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.ide.audioCues.EditorAudioCue
import com.intellij.ide.audioCues.EditorAudioCueDetector
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorKind
import com.intellij.platform.debugger.impl.shared.proxy.XDebugManagerProxy

internal class BreakpointLineAudioCueDetector : EditorAudioCueDetector {
  private val breakpointLine = EditorAudioCue(DebuggerAudioCues.BREAKPOINT_LINE)

  override fun detect(editor: Editor, line: Int, caretOffset: Int): Set<EditorAudioCue> {
    if (editor.editorKind != EditorKind.MAIN_EDITOR) return emptySet()
    val project = editor.project ?: return emptySet()
    val breakpoints = XDebugManagerProxy.getInstance()
      .getBreakpointManagerProxy(project)
      .getLineBreakpointManager()
      .getDocumentBreakpointProxies(editor.document)
    return if (breakpoints.any { it.getLine() == line }) setOf(breakpointLine) else emptySet()
  }
}
