// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.highlighter

import com.intellij.cce.core.Session
import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.lookup.LookupArranger
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange

class HighlightersClickListener(private val editor: Editor, private val project: Project) : EditorMouseListener {
  private val sessions = mutableMapOf<TextRange, Session>()

  fun addSession(session: Session, begin: Int, end: Int) {
    sessions[TextRange(begin, end)] = session
  }

  fun clear() = sessions.clear()

  override fun mouseClicked(event: EditorMouseEvent) {
    for (session in sessions) {
      if (editor.caretModel.offset in session.key.startOffset + 1..session.key.endOffset) {
        val lookup = LookupManager.getInstance(project).createLookup(editor, LookupElement.EMPTY_ARRAY, "",
                                                                     LookupArranger.DefaultArranger()) as LookupImpl
        for ((index, completion) in session.value.lookups.last().suggestions.withIndex()) {
          val item = if (index == session.value.lookups.last().selectedPosition)
            LookupElementBuilder.create(completion.presentationText).bold()
          else LookupElementBuilder.create(completion.presentationText)
          lookup.addItem(item, PrefixMatcher.ALWAYS_TRUE)
        }
        lookup.showLookup()
      }
    }
  }
}