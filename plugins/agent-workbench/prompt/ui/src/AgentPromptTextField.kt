// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.project.Project
import com.intellij.ui.LanguageTextField
import com.intellij.util.textCompletion.TextCompletionProvider
import com.intellij.util.textCompletion.TextCompletionUtil
import com.intellij.util.ui.JBUI
import org.intellij.plugins.markdown.lang.MarkdownFileType

internal class AgentPromptTextField(
  project: Project,
  completionProvider: TextCompletionProvider? = null,
) : LanguageTextField(
  FileTypes.PLAIN_TEXT.language,
  project,
  "",
  completionProvider?.let { provider -> TextCompletionUtil.DocumentWithCompletionCreator(provider, false) } ?: SimpleDocumentCreator(),
  false,
) {
  init {
    setPlaceholder(AgentPromptBundle.message("popup.prompt.placeholder"))
    setShowPlaceholderWhenFocused(true)
    addSettingsProvider { editor ->
      editor.settings.isUseSoftWraps = true
      editor.settings.isPaintSoftWraps = false
      editor.settings.isLineNumbersShown = false
      editor.settings.setGutterIconsShown(false)
      editor.settings.isFoldingOutlineShown = false
      editor.settings.isAdditionalPageAtBottom = false
      editor.settings.isRightMarginShown = false
      editor.setVerticalScrollbarVisible(true)
      editor.setHorizontalScrollbarVisible(false)
    }
  }

  override fun createEditor(): EditorEx {
    val ed = super.createEditor()
    ed.highlighter = EditorHighlighterFactory.getInstance()
      .createEditorHighlighter(project, MarkdownFileType.INSTANCE)
    ed.backgroundColor = JBUI.CurrentTheme.Popup.BACKGROUND
    ed.gutterComponentEx.background = JBUI.CurrentTheme.Popup.BACKGROUND
    return ed
  }
}
