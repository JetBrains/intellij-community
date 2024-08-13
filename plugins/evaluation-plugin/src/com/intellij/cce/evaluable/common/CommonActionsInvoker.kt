// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluable.common

import com.intellij.cce.interpreter.ActionsInvoker
import com.intellij.codeInsight.editorActions.CompletionAutoPopupHandler
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.CaretState
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.impl.TrailingSpacesStripper
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.TestModeFlags
import com.intellij.util.progress.sleepCancellable
import java.io.File

class CommonActionsInvoker(private val project: Project) : ActionsInvoker {
  init {
    TestModeFlags.set(CompletionAutoPopupHandler.ourTestingAutopopup, true)
  }

  private var spaceStrippingEnabled: Boolean = true

  override fun moveCaret(offset: Int) = onEdt {
    val editor = getEditorSafe(project)
    editor.caretModel.moveToOffset(offset)
    editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
  }

  override fun rename(text: String) = WriteCommandAction.runWriteCommandAction(project) {
    val docManager = PsiDocumentManager.getInstance(project)
    val editor = getEditorSafe(project)
    val psiFile = docManager.getPsiFile(editor.document)
    val psiIdentifier = psiFile?.findElementAt(editor.caretModel.offset)
    val psiElement = PsiTreeUtil.getParentOfType(psiIdentifier, PsiNameIdentifierOwner::class.java)

    if (psiElement == null) {
      LOG.warn("Psi identifier wasn't found")
      return@runWriteCommandAction
    }
    for (ref in ReferencesSearch.search(psiElement)) {
      if (!isThisReference(ref.canonicalText) ) {
        ref.handleElementRename(text)
      }
    }
    psiElement.setName(text)
  }

  override fun printText(text: String) = writeAction {
    val editor = getEditorSafe(project)
    LOG.info("Print text: ${StringUtil.shortenPathWithEllipsis(text, LOG_MAX_LENGTH)}. ${positionToString(editor)}")
    val project = editor.project
    val runnable = Runnable { EditorModificationUtil.insertStringAtCaret(editor, text) }
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

  override fun deleteRange(begin: Int, end: Int) = writeAction {
    val editor = getEditorSafe(project)
    val document = editor.document
    val textForDelete = StringUtil.shortenPathWithEllipsis(document.text.substring(begin, end), LOG_MAX_LENGTH)
    LOG.info("Delete range. Text: $textForDelete. Begin: ${positionToString(editor)} End: ${positionToString(editor)}")
    val project = editor.project
    val runnable = Runnable { document.deleteString(begin, end) }
    WriteCommandAction.runWriteCommandAction(project, runnable)
    if (editor.caretModel.offset != begin) {
      editor.caretModel.moveToOffset(begin)
    }
  }

  override fun selectRange(begin: Int, end: Int) = onEdt {
    val editor = getEditorSafe(project)
    LOG.info("Select range. Begin: ${positionToString(editor)}, end: ${positionToString(editor)}")
    val startOffset = editor.offsetToLogicalPosition(begin)
    val endOffset = editor.offsetToLogicalPosition(end)
    editor.caretModel.setCaretsAndSelections(listOf(CaretState(startOffset, startOffset, endOffset)))
    editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
  }

  override fun delay(seconds: Int) {
    LOG.info("Delay for $seconds seconds")
    sleepCancellable(seconds * 1000L)
  }

  override fun openFile(file: String): String = readActionInSmartMode(project) {
    LOG.info("Open file: $file")
    val virtualFile = LocalFileSystem.getInstance().findFileByIoFile(File(file))
    val descriptor = OpenFileDescriptor(project, virtualFile!!)
    spaceStrippingEnabled = TrailingSpacesStripper.isEnabled(virtualFile)
    TrailingSpacesStripper.setEnabled(virtualFile, false)
    val fileEditor = FileEditorManager.getInstance(project).openTextEditor(descriptor, true)
                     ?: throw Exception("Can't open text editor for file: $file")
    return@readActionInSmartMode fileEditor.document.text
  }

  override fun closeFile(file: String) = onEdt {
    LOG.info("Close file: $file")
    val virtualFile = LocalFileSystem.getInstance().findFileByIoFile(File(file))!!
    TrailingSpacesStripper.setEnabled(virtualFile, spaceStrippingEnabled)
    FileEditorManager.getInstance(project).closeFile(virtualFile)
  }

  override fun isOpen(file: String): Boolean = readActionInSmartMode(project) {
    FileEditorManager.getInstance(project).openFiles.any { it.path == file }
  }

  override fun save() = writeAction {
    val editor = getEditorSafe(project)
    FileDocumentManager.getInstance().saveDocumentAsIs(editor.document)
  }

  override fun getText(): String = readActionInSmartMode(project) { getEditorSafe(project).document.text }

  private fun writeAction(action: () -> Unit) {
    ApplicationManager.getApplication().invokeAndWait {
      ApplicationManager.getApplication().runWriteAction(action)
    }
  }

  private fun onEdt(action: () -> Unit) = ApplicationManager.getApplication().invokeAndWait {
    action()
  }

  companion object {
    private val LOG = logger<CommonActionsInvoker>()
    private const val LOG_MAX_LENGTH = 50
  }

  private fun isThisReference(ref: String): Boolean {
    return ref == "\$this"
  }
}