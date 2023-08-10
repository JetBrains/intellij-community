// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.highlighter

import com.intellij.cce.core.Session
import com.intellij.cce.report.ReportColors.Companion.getColor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import java.awt.Font

class Highlighter(private val project: Project) {
  companion object {
    private val listenerKey = Key<HighlightersClickListener>("com.intellij.cce.highlighter.listener")
  }

  private lateinit var listener: HighlightersClickListener

  fun highlight(sessions: List<Session>) {
    val editor = (FileEditorManager.getInstance(project).selectedEditors[0] as TextEditor).editor
    listener = editor.getUserData(listenerKey) ?: HighlightersClickListener(editor, project)
    editor.addEditorMouseListener(listener)

    ApplicationManager.getApplication().invokeLater {
      editor.markupModel.removeAllHighlighters()
      listener.clear()
      for (session in sessions) {
        addHighlight(editor, session, session.offset, session.offset + session.expectedText.length)
      }
      editor.putUserData(listenerKey, listener)
    }
  }

  private fun addHighlight(editor: Editor, session: Session, begin: Int, end: Int) {
    val color = getColor(session, HighlightColors, session.lookups.lastIndex)
    editor.markupModel.addRangeHighlighter(begin, end, HighlighterLayer.LAST,
                                           TextAttributes(null, color, null, EffectType.BOXED, Font.PLAIN),
                                           HighlighterTargetArea.EXACT_RANGE)
    listener.addSession(session, begin, end)
  }
}
