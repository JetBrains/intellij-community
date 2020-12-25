// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.icons.AllIcons.Actions.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.ex.LineStatusTracker
import com.intellij.openapi.vcs.ex.RollbackLineStatusAction
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager
import com.intellij.psi.PsiFile
import java.util.*
import javax.swing.Icon

class RollbackCurrentLineIntention : IntentionAction, LowPriorityAction, DumbAware, Iconable {
  override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
    if (editor == null) return false
    val tracker = getValidTrackerOrNull(project, editor)
    if (tracker == null) return false

    val caret = editor.caretModel.currentCaret
    val lines = BitSet()
    if (caret.hasSelection()) {
      val startLine = editor.document.getLineNumber(caret.selectionStart)
      val endLine = editor.document.getLineNumber(caret.selectionEnd)
      lines.set(startLine, endLine)
    }
    else {
      val currentLine = editor.document.getLineNumber(caret.offset)
      lines.set(currentLine)
    }
    return tracker.getRangesForLines(lines)?.isNotEmpty() ?: false
  }

  override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
    if (editor == null) return
    val tracker = getValidTrackerOrNull(project, editor)
    if (tracker == null) return

    RollbackLineStatusAction.rollback(tracker, editor)
  }

  private fun getValidTrackerOrNull(project: Project, editor: Editor) : LineStatusTracker<*>? {
    val tracker = LineStatusTrackerManager.getInstance(project).getLineStatusTracker(editor.document)
    if (tracker == null || !tracker.isValid() || !tracker.isAvailableAt(editor)) {
      return null
    }
    return tracker
  }

  override fun getText(): String = VcsBundle.message("intention.name.rollback.changes.in.current.line")

  override fun getFamilyName(): String = text

  override fun startInWriteAction(): Boolean = true

  override fun getIcon(flags: Int): Icon {
    return Rollback
  }
}