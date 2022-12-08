// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework.actions

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.actions.DocumentsSynchronizer
import com.intellij.diff.actions.SynchronizedDocumentContent
import com.intellij.diff.contents.DocumentContent
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.project.Project
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.refactoring.suggested.startOffset
import java.util.function.IntUnaryOperator

class TestDiffContent(
  private val project: Project,
  original: DocumentContent,
  text: String,
  private val elemPtr: SmartPsiElementPointer<PsiElement>
) : SynchronizedDocumentContent(original) {
  override fun getContentType(): FileType = FileTypes.PLAIN_TEXT

  override val synchronizer: DocumentsSynchronizer = object : DocumentsSynchronizer(project, original.document, fakeDocument) {
    override fun onDocumentChanged1(event: DocumentEvent) {
      PsiDocumentManager.getInstance(project).performForCommittedDocument(document1, Runnable {
        val element = elemPtr.element ?: return@Runnable
        replaceString(myDocument2, 0, myDocument2.textLength, ElementManipulators.getValueText(element))
      })
    }

    override fun onDocumentChanged2(event: DocumentEvent) {
      if (!myDocument1.isWritable) return
      try {
        myDuringModification = true
        val element = elemPtr.element ?: return
        ElementManipulators.handleContentChange(element, event.document.text)
      } finally {
        myDuringModification = false
      }
    }

    override fun startListen() {
      replaceString(myDocument2, 0, myDocument2.textLength, text)
      super.startListen()
    }
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
          if (!element.isValid) return@IntUnaryOperator - 1
          val line = value + original.document.getLineNumber(element.startOffset)
          originalLineConvertor?.applyAsInt(line) ?: line
        })
      }
    }
  }
}

