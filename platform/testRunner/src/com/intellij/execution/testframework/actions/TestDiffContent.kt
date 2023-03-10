// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework.actions

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.actions.DocumentsSynchronizer
import com.intellij.diff.contents.DiffContentBase
import com.intellij.diff.contents.DocumentContent
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.impl.EditorFactoryImpl
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.project.Project
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.refactoring.suggested.startOffset
import com.intellij.util.concurrency.annotations.RequiresEdt
import java.util.function.IntUnaryOperator

class TestDiffContent(
  private val project: Project,
  private val original: DocumentContent,
  text: String,
  private val elemPtr: SmartPsiElementPointer<PsiElement>
) : DiffContentBase(), DocumentContent {
  override fun getDocument(): Document = fakeDocument

  override fun getContentType(): FileType = FileTypes.PLAIN_TEXT

  private val fakeDocument = (EditorFactory.getInstance() as EditorFactoryImpl).createDocument("", true, false).apply {
    putUserData(UndoManager.ORIGINAL_DOCUMENT, original.document)
  }

  private val synchronizer: DocumentsSynchronizer = object : DocumentsSynchronizer(project, original.document, fakeDocument) {
    override fun onDocumentChanged1(event: DocumentEvent) {
      PsiDocumentManager.getInstance(project).performForCommittedDocument(document1, Runnable {
        val element = elemPtr.element ?: return@Runnable
        replaceString(myDocument2, ElementManipulators.getValueText(element))
      })
    }

    override fun onDocumentChanged2(event: DocumentEvent) {
      if (!myDocument1.isWritable) return
      try {
        myDuringModification = true
        val element = elemPtr.element ?: return
        TestDiffProvider.TEST_DIFF_PROVIDER_LANGUAGE_EXTENSION.forLanguage(element.language).updateExpected(element, event.document.text)
      }
      finally {
        myDuringModification = false
      }
    }

    override fun startListen() {
      replaceString(myDocument2, text)
      super.startListen()
    }

    @RequiresEdt
    private fun replaceString(document: Document, newText: CharSequence) {
      try {
        myDuringModification = true
        CommandProcessor.getInstance().executeCommand(
          myProject,
          {
            ApplicationManager.getApplication().runWriteAction {
              document.replaceString(0, document.textLength, newText)
            }
          },
          DiffBundle.message("synchronize.document.and.its.fragment"),
          document
        )
      }
      finally {
        myDuringModification = false
      }
    }

  }

  private var assignments = 0

  override fun onAssigned(isAssigned: Boolean) {
    if (isAssigned) {
      if (assignments == 0) synchronizer.startListen()
      assignments++
    }
    else {
      assignments--
      if (assignments == 0) synchronizer.stopListen()
    }
    assert(assignments >= 0)
  }

  companion object {
    fun create(
      project: Project,
      text: String,
      elemPtr: SmartPsiElementPointer<PsiElement>
    ): TestDiffContent? {
      val element = elemPtr.element ?: return null
      val document = PsiDocumentManager.getInstance(project).getDocument(element.containingFile) ?: return null
      val diffContent = DiffContentFactory.getInstance().create(project, document)
      return TestDiffContent(project, diffContent, text, elemPtr).apply {
        val originalLineConvertor = original.getUserData(DiffUserDataKeysEx.LINE_NUMBER_CONVERTOR)
        putUserData(DiffUserDataKeysEx.LINE_NUMBER_CONVERTOR, IntUnaryOperator { value ->
          if (!element.isValid) return@IntUnaryOperator -1
          val line = value + original.document.getLineNumber(element.startOffset)
          originalLineConvertor?.applyAsInt(line) ?: line
        })
      }
    }
  }
}

