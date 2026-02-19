package com.intellij.cce.evaluable.common

import com.intellij.cce.core.Suggestion
import com.intellij.cce.core.SuggestionSource
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.ui.icons.CachedImageIcon

fun getEditor(project: Project): Editor? = (FileEditorManager.getInstance(project).selectedEditor as? TextEditor)?.editor

fun getEditorSafe(project: Project): Editor = getEditor(project) ?: throw IllegalStateException("No active editor")

fun positionToString(editor: Editor): String {
  val offset = editor.caretModel.offset
  val logicalPosition = editor.offsetToLogicalPosition(offset)
  return "Offset: $offset, Line: ${logicalPosition.line}, Column: ${logicalPosition.column}."
}

fun LookupElement.asSuggestion(): Suggestion {
  val presentation = LookupElementPresentation()
  renderElement(presentation)
  val presentationText = "${presentation.itemText}${presentation.tailText ?: ""}" +
                         if (presentation.typeText != null) ": " + presentation.typeText else ""
  return Suggestion(lookupString, presentationText, sourceFromPresentation(presentation))
}

fun sourceFromPresentation(presentation: LookupElementPresentation): SuggestionSource {
  val icon = presentation.icon
  val typeText = presentation.typeText

  return when {
    icon is CachedImageIcon && icon.originalPath == "/icons/codota-color-icon.png" -> SuggestionSource.CODOTA
    typeText == "@tab-nine" -> SuggestionSource.TAB_NINE
    typeText == "Full Line" -> SuggestionSource.INTELLIJ
    else -> SuggestionSource.STANDARD
  }
}

fun <T> readActionInSmartMode(project: Project, runnable: () -> T): T {
  val dumbService = DumbService.getInstance(project)
  var result: T? = null
  ApplicationManager.getApplication().invokeAndWait {
    result = dumbService.runReadActionInSmartMode<T>(runnable)
  }

  return result!!
}
