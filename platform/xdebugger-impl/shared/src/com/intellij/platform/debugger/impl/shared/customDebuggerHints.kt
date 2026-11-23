// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.shared

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.util.DocumentUtil
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus

/**
 * Marks an after-line-end inlay as a custom debugger hint. The debugger shows no inline value hint on the line of such an inlay.
 */
@ApiStatus.Internal
@JvmField
val CUSTOM_DEBUGGER_HINT: Key<Boolean> = Key.create("custom debugger hint")

/**
 * Returns `true` if an editor of [document] in [project] has a [CUSTOM_DEBUGGER_HINT] inlay on [line].
 *
 * In split mode, the backend calls it for its own editors, because the frontend copies of the inlays do not keep the mark.
 */
@ApiStatus.Internal
@RequiresEdt
fun hasCustomDebuggerHint(project: Project, document: Document, line: Int): Boolean {
  if (!DocumentUtil.isValidLine(line, document)) return false
  val lineStart = document.getLineStartOffset(line)
  val lineEnd = document.getLineEndOffset(line)
  return EditorFactory.getInstance().getEditors(document, project).any { editor ->
    editor.inlayModel.getAfterLineEndElementsInRange(lineStart, lineEnd).any(::isCustomDebuggerHint)
  }
}

@ApiStatus.Internal
fun isCustomDebuggerHint(inlay: Inlay<*>): Boolean = CUSTOM_DEBUGGER_HINT.get(inlay) != null
