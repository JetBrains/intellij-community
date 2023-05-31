// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluable.common

import com.intellij.cce.core.Language
import com.intellij.cce.core.Suggestion
import com.intellij.cce.core.SuggestionSource
import com.intellij.cce.interpreter.ActionsInvoker
import com.intellij.codeInsight.editorActions.CompletionAutoPopupHandler
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.impl.TrailingSpacesStripper
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.TestModeFlags
import java.io.File

abstract class BasicActionsInvoker(protected val project: Project, protected val language: Language) : ActionsInvoker {
  init {
    TestModeFlags.set(CompletionAutoPopupHandler.ourTestingAutopopup, true)
  }

  protected var editor: Editor? = null
  private var spaceStrippingEnabled: Boolean = true

  override fun moveCaret(offset: Int) {
    LOG.info("Move caret. ${positionToString(offset)}")
    editor!!.caretModel.moveToOffset(offset)
    editor!!.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
  }

  override fun rename(text: String) {
    val docManager = PsiDocumentManager.getInstance(project)
    val editor = editor!!
    val psiFile = docManager.getPsiFile(editor.document)
    val psiIdentifier = psiFile?.findElementAt(editor.caretModel.offset)
    val psiElement = PsiTreeUtil.getParentOfType(psiIdentifier, PsiNameIdentifierOwner::class.java)

    if (psiElement == null) {
      LOG.warn("Psi identifier wasn't found")
      return
    }
    for (ref in ReferencesSearch.search(psiElement)) {
      ref.handleElementRename(text)
    }
    psiElement.setName(text)
  }

  override fun printText(text: String) {
    LOG.info("Print text: ${StringUtil.shortenPathWithEllipsis(text, LOG_MAX_LENGTH)}. ${positionToString(editor!!.caretModel.offset)}")
    val project = editor!!.project
    val runnable = Runnable { EditorModificationUtil.insertStringAtCaret(editor!!, text) }
    WriteCommandAction.runWriteCommandAction(project) {
      val lookup = LookupManager.getActiveLookup(editor) as? LookupImpl
      if (lookup != null) {
        lookup.replacePrefix(lookup.additionalPrefix, lookup.additionalPrefix + text)
      }
      else {
        runnable.run()
      }
    }
  }

  override fun deleteRange(begin: Int, end: Int) {
    val document = editor!!.document
    val textForDelete = StringUtil.shortenPathWithEllipsis(document.text.substring(begin, end), LOG_MAX_LENGTH)
    LOG.info("Delete range. Text: $textForDelete. Begin: ${positionToString(begin)} End: ${positionToString(end)}")
    val project = editor!!.project
    val runnable = Runnable { document.deleteString(begin, end) }
    WriteCommandAction.runWriteCommandAction(project, runnable)
    if (editor!!.caretModel.offset != begin) editor!!.caretModel.moveToOffset(begin)
  }

  override fun openFile(file: String): String {
    LOG.info("Open file: $file")
    val virtualFile = LocalFileSystem.getInstance().findFileByIoFile(File(file))
    val descriptor = OpenFileDescriptor(project, virtualFile!!)
    spaceStrippingEnabled = TrailingSpacesStripper.isEnabled(virtualFile)
    TrailingSpacesStripper.setEnabled(virtualFile, false)
    val fileEditor = FileEditorManager.getInstance(project).openTextEditor(descriptor, true)
                     ?: throw Exception("Can't open text editor for file: $file")
    editor = fileEditor
    return fileEditor.document.text
  }

  override fun closeFile(file: String) {
    LOG.info("Close file: $file")
    val virtualFile = LocalFileSystem.getInstance().findFileByIoFile(File(file))!!
    TrailingSpacesStripper.setEnabled(virtualFile, spaceStrippingEnabled)
    FileEditorManager.getInstance(project).closeFile(virtualFile)
    editor = null
  }

  override fun isOpen(file: String): Boolean {
    val isOpen = FileEditorManager.getInstance(project).openFiles.any { it.path == file }
    return isOpen
  }

  override fun save() {
    val document = editor?.document ?: throw IllegalStateException("No open editor")
    FileDocumentManager.getInstance().saveDocumentAsIs(document)
  }

  override fun getText(): String = editor?.document?.text ?: throw IllegalStateException("No open editor")

  protected fun positionToString(offset: Int): String {
    val logicalPosition = editor!!.offsetToLogicalPosition(offset)
    return "Offset: $offset, Line: ${logicalPosition.line}, Column: ${logicalPosition.column}."
  }

  protected fun LookupElement.asSuggestion(): Suggestion {
    val presentation = LookupElementPresentation()
    renderElement(presentation)
    val presentationText = "${presentation.itemText}${presentation.tailText ?: ""}" +
                           if (presentation.typeText != null) ": " + presentation.typeText else ""

    val insertedText = if (lookupString.contains('>')) lookupString.replace(Regex("<.+>"), "")
    else lookupString
    return Suggestion(insertedText, presentationText, sourceFromPresentation(presentation))
  }

  private fun sourceFromPresentation(presentation: LookupElementPresentation): SuggestionSource {
    val icon = presentation.icon
    val typeText = presentation.typeText

    return when {
      icon is IconLoader.CachedImageIcon && icon.originalPath == "/icons/codota-color-icon.png" -> SuggestionSource.CODOTA
      typeText == "@tab-nine" -> SuggestionSource.TAB_NINE
      typeText == "full-line" -> SuggestionSource.INTELLIJ
      else -> SuggestionSource.STANDARD
    }
  }

  protected companion object {
    val LOG = logger<BasicActionsInvoker>()
    const val LOG_MAX_LENGTH = 50
  }
}